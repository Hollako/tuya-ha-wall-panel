#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <sys/wait.h>
static int mode;static long long started;
static long long test_now(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000LL+t.tv_nsec/1000000;}
static int parent_alive(int pid){(void)pid;return mode!=3||test_now()-started<30;}
static pid_t child_fake(int fd){pid_t pid=fork();if(!pid){if(mode==2||mode==3||mode==4){for(;;)pause();}char bytes[1024]={0};unsigned n=mode==1?16:64;if(mode==5)n=9*1024*1024;while(n){unsigned count=n>sizeof(bytes)?sizeof(bytes):n;if(write(fd,bytes,count)!=(ssize_t)count)_exit(1);n-=count;}_exit(0);}return pid;}
#define WP_CAPTURE_PARENT parent_alive
#define WP_CAPTURE_CHILD child_fake
#define WP_CAPTURE_TIMEOUT 250
#define main helper_main
#include "wallpanel-capture.c"
#undef main
int main(void){
    for(mode=0;mode<6;mode++){
        int control[2],data[2];assert(!pipe(control)&&!pipe(data));pid_t pid=fork();assert(pid>=0);
        if(!pid){close(control[1]);close(data[0]);dup2(control[0],0);dup2(data[1],1);close(control[0]);close(data[1]);started=test_now();_exit(cap_run(getpid()));}
        close(control[0]);close(data[1]);if(mode==4){close(control[1]);control[1]=-1;}
        char bytes[4096];ssize_t count;size_t total=0;while((count=read(data[0],bytes,sizeof(bytes)))>0)total+=(size_t)count;close(data[0]);if(control[1]>=0)close(control[1]);
        int status;assert(waitpid(pid,&status,0)==pid&&WIFEXITED(status));assert((WEXITSTATUS(status)==0)==(mode==0));assert(total<=CAP_LIMIT);if(!mode)assert(total==64);
    }
    puts("PASS: memory-stream capture helper success, truncation, timeout, owner loss, control-pipe cancellation and size limit. Synthetic bytes only.");
}
