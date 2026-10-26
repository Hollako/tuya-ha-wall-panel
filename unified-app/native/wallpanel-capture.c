#define _GNU_SOURCE
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <poll.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <time.h>
#include "../../wallpanel-hardware/mcu-lease.h"
#define CAP_LIMIT (8U*1024*1024)
#ifndef WP_CAPTURE_TIMEOUT
#define WP_CAPTURE_TIMEOUT 10000
#endif
static volatile sig_atomic_t capture_alive=1;
static long long cap_now(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
static void cap_stop(int sig){(void)sig;capture_alive=0;}
#ifndef WP_CAPTURE_PARENT
static int cap_parent(int pid){
    char path[96],name[64];snprintf(path,sizeof(path),"/proc/%d/comm",pid);FILE *f=fopen(path,"r");if(!f)return 0;char *ok=fgets(name,sizeof(name),f);fclose(f);
    return ok&&!strcmp(name,"wallpanel-core\n")&&!kill(pid,0)&&wp_lock_held(WP_MANAGEMENT_REQUEST)&&wp_lock_held(WP_MANAGEMENT_RESOURCE);
}
#define WP_CAPTURE_PARENT cap_parent
#endif
#ifndef WP_CAPTURE_CHILD
static pid_t cap_child(int output){pid_t parent=getpid(),pid=fork();if(!pid){
    prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(1);dup2(output,1);
    int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,2);}
    execl("/system/bin/screencap","screencap","-p",(char*)NULL);_exit(127);
}return pid;}
#define WP_CAPTURE_CHILD cap_child
#endif
static int cap_running(int pid,long long deadline){
    struct pollfd p={.fd=0,.events=POLLIN};if(poll(&p,1,0)>0&&(p.revents&(POLLIN|POLLHUP|POLLERR|POLLNVAL)))return 0;
    return capture_alive&&cap_now()<deadline&&WP_CAPTURE_PARENT(pid);
}
static int cap_run(int parent){
    int pipes[2];if(pipe2(pipes,O_CLOEXEC))return 2;fcntl(pipes[0],F_SETFL,O_NONBLOCK);
    pid_t child=WP_CAPTURE_CHILD(pipes[1]);close(pipes[1]);if(child<0){close(pipes[0]);return 2;}
    fcntl(1,F_SETFL,fcntl(1,F_GETFL)|O_NONBLOCK);long long deadline=cap_now()+WP_CAPTURE_TIMEOUT;size_t total=0;int success=0;
    unsigned char buffer[8192];
    while(cap_running(parent,deadline)){
        struct pollfd p[2]={{.fd=pipes[0],.events=POLLIN},{.fd=0,.events=POLLIN}};poll(p,2,50);
        ssize_t n=read(pipes[0],buffer,sizeof(buffer));if(n==0){success=1;break;}if(n<0){if(errno==EAGAIN||errno==EINTR)continue;break;}
        if(total+(size_t)n>CAP_LIMIT)break;total+=(size_t)n;size_t at=0;
        while(at<(size_t)n&&cap_running(parent,deadline)){
            ssize_t written=write(1,buffer+at,(size_t)n-at);
            if(written>0)at+=(size_t)written;else if(written<0&&(errno==EAGAIN||errno==EINTR)){struct pollfd out={.fd=1,.events=POLLOUT};poll(&out,1,50);}else goto done;
        }
        if(at<(size_t)n)break;
    }
done:
    close(pipes[0]);int status=0,finished=0;
    while(success&&cap_running(parent,deadline)){
        pid_t got=waitpid(child,&status,WNOHANG);if(got==child){finished=1;break;}if(got<0&&errno!=EINTR)break;usleep(10000);
    }
    if(!finished){success=0;kill(child,SIGKILL);while(waitpid(child,&status,0)<0&&errno==EINTR){}}
    return success&&total>=32&&WIFEXITED(status)&&WEXITSTATUS(status)==0?0:3;
}
int main(int argc,char **argv){
    if(argc==2&&!strcmp(argv[1],"--describe")){puts("{\"capture\":true,\"network\":false,\"saved_image\":false,\"limit\":8388608}");return 0;}
    if(argc!=2||geteuid()!=0)return 1;char *end;long pid=strtol(argv[1],&end,10);if(*end||pid<2||pid>4194304||!WP_CAPTURE_PARENT((int)pid))return 1;
    signal(SIGPIPE,SIG_IGN);signal(SIGTERM,cap_stop);signal(SIGINT,cap_stop);signal(SIGHUP,cap_stop);return cap_run((int)pid);
}
