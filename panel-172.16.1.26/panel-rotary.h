/* Model-specific, read-only quadrature observer. The vendor driver remains
 * bound for knob presses/buttons. No GPIO configuration or output is written.
 * Only this exact verified G10 DT wiring permits the physical input mapping. */
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include "panel-rotary-decoder.h"
static int rotary_fd=-1;
static pid_t rotary_pid=-1;
struct rotary_step {long long at;int direction;int reserved;};
static unsigned int rotary_be32(const unsigned char*p){return (unsigned int)p[0]<<24|(unsigned int)p[1]<<16|(unsigned int)p[2]<<8|p[3];}
static int rotary_property(const char *path,unsigned char *out,int length){
    int f=open(path,O_RDONLY|O_CLOEXEC);if(f<0)return -1;
    int n=read(f,out,length);close(f);return n==length?0:-1;
}
static int rotary_state(volatile uint32_t *bank){
    uint32_t v=bank[0x50/4];return ((v>>16)&1)*2+((v>>18)&1);
}
static volatile uint32_t *rotary_map(void){
    unsigned char a[12],b[12],id[4],reg[16];
    const char *bank="/sys/firmware/devicetree/base/pinctrl/gpio3@ff270000";
    char path[256];snprintf(path,sizeof(path),"%s/phandle",bank);
    if(rotary_property("/sys/firmware/devicetree/base/knod_aispeech/irq-a-gpio",a,12)||
       rotary_property("/sys/firmware/devicetree/base/knod_aispeech/irq-b-gpio",b,12)||rotary_property(path,id,4))return MAP_FAILED;
    snprintf(path,sizeof(path),"%s/reg",bank);
    if(rotary_property(path,reg,16)||rotary_be32(a)!=rotary_be32(id)||rotary_be32(b)!=rotary_be32(id)||
       rotary_be32(a+4)!=16||rotary_be32(b+4)!=18||rotary_be32(reg)||rotary_be32(reg+4)!=0xff270000||
       rotary_be32(reg+8)||rotary_be32(reg+12)!=0x100)return MAP_FAILED;
    int f=open("/dev/mem",O_RDONLY|O_SYNC|O_CLOEXEC);if(f<0)return MAP_FAILED;
    volatile uint32_t *mapped=mmap(NULL,4096,PROT_READ,MAP_SHARED,f,0xff270000);close(f);
    if(mapped!=MAP_FAILED&&(mapped[0x04/4]&((1u<<16)|(1u<<18)))){
        munmap((void*)mapped,4096);return MAP_FAILED;
    }
    return mapped;
}
static long long rotary_us(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000000+t.tv_nsec/1000;}
static void rotary_init(void){
    if(dry_run)return;
    volatile uint32_t *mapped=rotary_map();
    if(mapped==MAP_FAILED){puts("Rotary: physical decoder unavailable; using vendor input pulses.");return;}
    int pipefd[2];
    if(pipe2(pipefd,O_NONBLOCK|O_CLOEXEC)){munmap((void*)mapped,4096);return;}
    pid_t parent=getpid();rotary_pid=fork();
    if(rotary_pid<0){close(pipefd[0]);close(pipefd[1]);munmap((void*)mapped,4096);return;}
    if(!rotary_pid){
        prctl(PR_SET_NAME,"panel-rotary",0,0,0);
        prctl(PR_SET_PDEATHSIG,SIGTERM);
        if(getppid()!=parent)_exit(0);
        /* No inherited MQTT listener, credentials file, lock or input handles
         * are kept by the GPIO sampler. Its only output is a bounded pipe. */
        long maxfd=sysconf(_SC_OPEN_MAX);if(maxfd<0||maxfd>65536)maxfd=65536;
        for(int f=3;f<maxfd;f++)if(f!=pipefd[1])close(f);
        int current=rotary_state(mapped),candidate=current;
        struct rotary_decoder decoder;rotary_reset(&decoder,current);
        long long since=rotary_us();struct timespec interval={.tv_nsec=500000};
        while(running){
            int sample=rotary_state(mapped);long long now=rotary_us();
            if(sample!=candidate){candidate=sample;since=now;}
            if(candidate!=current&&now-since>=1000){
                current=candidate;int step=rotary_transition(&decoder,current);
                if(step){
                    struct rotary_step event={.at=now/1000,.direction=rotary_physical_direction(step)};
                    if(write(pipefd[1],&event,sizeof(event))<0&&errno!=EAGAIN&&errno!=EINTR)break;
                }
            }
            nanosleep(&interval,NULL);
        }
        munmap((void*)mapped,4096);close(pipefd[1]);_exit(0);
    }
    close(pipefd[1]);munmap((void*)mapped,4096);rotary_fd=pipefd[0];
    printf("Rotary: half-step decoder active, sampler pid=%d, read-only GPIO3 A16/B18.\n",rotary_pid);
}
static int rotary_receive(int publish_events){
    if(rotary_fd<0)return 0;
    struct rotary_step events[32];ssize_t n;
    while((n=read(rotary_fd,events,sizeof(events)))>0){
        for(size_t i=0;i<(size_t)n/sizeof(events[0]);i++){
            struct rotary_step *e=&events[i];
            if(!publish_events||net<0||ms()-e->at>1000)continue;
            gesture_flush(0);
            gesture_publish(0,e->direction>0?"rotate_right":"rotate_left",e->direction>0?KEY_FN_F2:KEY_FN_F1);
        }
    }
    if(n==0||(n<0&&errno!=EAGAIN&&errno!=EINTR)){
        close(rotary_fd);rotary_fd=-1;
        puts("Rotary: sampler stopped; reverting to vendor input pulses.");
    }
    return gesture_publish_failed?-1:0;
}
static void rotary_cleanup(void){
    if(rotary_pid>0){kill(rotary_pid,SIGTERM);while(waitpid(rotary_pid,NULL,0)<0&&errno==EINTR){}rotary_pid=-1;}
    if(rotary_fd>=0)close(rotary_fd);rotary_fd=-1;
}
