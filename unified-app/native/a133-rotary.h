/* Passive GPIO observer; vendor press/hold input driver stays bound. */
#include "../../panel-172.16.1.26/panel-rotary-decoder.h"
static int rotary_fd=-1;static pid_t rotary_pid=-1;
struct rotary_step {long long at;int direction,reserved;};
static int a133_pin(const char *text,const char *name){const char *p=strstr(text,name);if(!p)return -1;const char *e=strchr(p,'\n');size_t n=e?(size_t)(e-p):strlen(p);char line[160];if(n>=sizeof(line))return -1;memcpy(line,p,n);line[n]=0;return strstr(line," in  hi")?1:strstr(line," in  lo")?0:-1;}
static int a133_encoder_sample(int fd){char data[8192];if(lseek(fd,0,SEEK_SET)<0)return -1;ssize_t n=read(fd,data,sizeof(data)-1);if(n<=0)return -1;data[n]=0;int a=a133_pin(data,"gpio-41 "),b=a133_pin(data,"gpio-42 ");return a<0||b<0?-1:(a<<1)|b;}
static void rotary_init(void){
 if(dry_run||!a133_profile_valid())return;int gpio=open("/sys/kernel/debug/gpio",O_RDONLY|O_CLOEXEC);if(gpio<0)return;int state=a133_encoder_sample(gpio);if(state<0){close(gpio);return;}int p[2];if(pipe2(p,O_NONBLOCK|O_CLOEXEC)){close(gpio);return;}pid_t parent=getpid();rotary_pid=fork();
 if(rotary_pid<0){close(gpio);close(p[0]);close(p[1]);return;}
 if(!rotary_pid){prctl(PR_SET_NAME,"a133-rotary",0,0,0);prctl(PR_SET_PDEATHSIG,SIGTERM);if(getppid()!=parent)_exit(1);long max=sysconf(_SC_OPEN_MAX);if(max<0||max>65536)max=65536;for(int f=3;f<max;f++)if(f!=gpio&&f!=p[1])close(f);struct rotary_decoder d;rotary_reset(&d,state);while(core_alive){int sample=a133_encoder_sample(gpio);if(sample<0)break;int step=rotary_transition(&d,sample);if(step){struct rotary_step event={.at=ms(),.direction=rotary_physical_direction(step)};if(write(p[1],&event,sizeof(event))!=(ssize_t)sizeof(event))break;}usleep(2000);}close(gpio);close(p[1]);_exit(0);}
 close(gpio);close(p[1]);rotary_fd=p[0];
}
static int rotary_receive(int enabled){if(rotary_fd<0)return 0;struct rotary_step steps[32];ssize_t n;while((n=read(rotary_fd,steps,sizeof(steps)))>0){if(n%(ssize_t)sizeof(steps[0]))return -1;for(size_t i=0;i<(size_t)n/sizeof(steps[0]);i++){struct rotary_step *e=&steps[i];if(!enabled||net<0||ms()-e->at>1000)continue;gesture_flush(0);gesture_publish(0,e->direction>0?"rotate_right":"rotate_left",e->direction>0?KEY_F2:KEY_F1);}}if(n==0||(n<0&&errno!=EAGAIN&&errno!=EINTR)){close(rotary_fd);rotary_fd=-1;core_alive=0;return -1;}return gesture_publish_failed?-1:0;}
static void rotary_cleanup(void){if(rotary_pid>0){kill(rotary_pid,SIGTERM);while(waitpid(rotary_pid,NULL,0)<0&&errno==EINTR){}rotary_pid=-1;}if(rotary_fd>=0)close(rotary_fd);rotary_fd=-1;}
