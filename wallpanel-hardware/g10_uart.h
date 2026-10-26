/* Single-owner native G10 RS485 transport. No relay restoration/replay.
 * Interface calls are serialized by the caller. Querying ownership never
 * opens the UART; opening refuses any existing process with an open handle.
 */
#ifndef WALLPANEL_G10_UART_H
#define WALLPANEL_G10_UART_H
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>
#include "g10_mcu.h"
#ifndef G10_OWNER_CHECK
#define G10_OWNER_CHECK g10_owner
#endif
struct g10_uart {int fd,have_original;struct termios original;};
static long long g10_ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
/* Return 0 free, >0 owner PID, -1 unverifiable/error. Skip only our own FDs. */
static int g10_owner(const char *device){
    struct stat target;if(stat(device,&target)||!S_ISCHR(target.st_mode))return -1;
    DIR *proc=opendir("/proc");if(!proc)return -1;
    struct dirent *entry;int result=0;
    while((entry=readdir(proc))){
        char *end;long pid=strtol(entry->d_name,&end,10);
        if(!*entry->d_name||*end||pid<=0||pid==getpid())continue;
        char path[96];snprintf(path,sizeof(path),"/proc/%ld/fd",pid);
        DIR *fds=opendir(path);
        if(!fds){if(errno==ENOENT||errno==ESRCH)continue;result=-1;break;}
        struct dirent *fd;
        while((fd=readdir(fds))){
            if(fd->d_name[0]=='.')continue;
            struct stat st;
            if(fstatat(dirfd(fds),fd->d_name,&st,0)){
                if(errno==ENOENT||errno==ESRCH)continue;
                result=-1;break;
            }
            if(S_ISCHR(st.st_mode)&&st.st_rdev==target.st_rdev){result=(int)pid;break;}
        }
        closedir(fds);if(result)break;
    }
    closedir(proc);return result;
}
static int g10_direction(const char *node,int sending){
    int fd=open(node,O_WRONLY|O_CLOEXEC);if(fd<0)return -1;
    const char *value=sending?"send\n":"receive\n";size_t len=strlen(value);
    ssize_t done=write(fd,value,len);int error=errno;close(fd);errno=error;
    return done==(ssize_t)len?0:-1;
}
static int g10_uart_close(struct g10_uart *u,const char *direction){
    if(u->fd<0)return 0;
    /* Never leave the half-duplex bus driven, including on write failure. */
    int bad=g10_direction(direction,0);
    if(u->have_original&&tcsetattr(u->fd,TCSANOW,&u->original))bad=1;
    if(ioctl(u->fd,TIOCNXCL))bad=1;
    if(close(u->fd))bad=1;
    u->fd=-1;u->have_original=0;return bad?-1:0;
}
static int g10_uart_open(struct g10_uart *u,const char *device,const char *direction){
    u->fd=-1;u->have_original=0;
    if(G10_OWNER_CHECK(device)!=0){errno=EBUSY;return -1;}
    int fd=open(device,O_RDWR|O_NONBLOCK|O_NOCTTY|O_CLOEXEC);if(fd<0)return -1;
    /* TIOCEXCL prevents new non-privileged opens. The second ownership check
     * catches a competing owner opening between the first check and our open.
     * Root controllers still must be disabled before taking over the port. */
    if(ioctl(fd,TIOCEXCL)){int error=errno;close(fd);errno=error;return -1;}
    if(G10_OWNER_CHECK(device)!=0){ioctl(fd,TIOCNXCL);close(fd);errno=EBUSY;return -1;}
    struct termios t;
    if(tcgetattr(fd,&t)){int error=errno;ioctl(fd,TIOCNXCL);close(fd);errno=error;return -1;}
    u->fd=fd;u->original=t;u->have_original=1;
    cfmakeraw(&t);cfsetispeed(&t,B9600);cfsetospeed(&t,B9600);
    t.c_cflag=(t.c_cflag&~(CSIZE|PARENB|CSTOPB|CRTSCTS))|CS8|CLOCAL|CREAD;
    t.c_cc[VMIN]=0;t.c_cc[VTIME]=0;
    if(tcsetattr(fd,TCSANOW,&t)||g10_direction(direction,0)||tcflush(fd,TCIFLUSH)){
        g10_uart_close(u,direction);return -1;
    }
    return 0;
}
static int g10_uart_send(struct g10_uart *u,const char *direction,const unsigned char frame[7]){
    if(!g10_allowed(frame)){errno=EINVAL;return -1;}
    if(u->fd<0){errno=ENOTCONN;return -1;}
    int result=-1;
    if(g10_direction(direction,1))return -1;
    long long deadline=g10_ms()+250;size_t sent=0;
    while(sent<7&&g10_ms()<deadline){
        ssize_t n=write(u->fd,frame+sent,7-sent);
        if(n>0){sent+=(size_t)n;continue;}
        if(n<0&&errno!=EINTR&&errno!=EAGAIN)goto receive;
        struct pollfd p={.fd=u->fd,.events=POLLOUT};
        if(poll(&p,1,5)<0&&errno!=EINTR)goto receive;
    }
    if(sent!=7){errno=ETIMEDOUT;goto receive;}
    /* Bound transmitter drain rather than blocking indefinitely in tcdrain. */
    for(;;){
        int pending=0;if(ioctl(u->fd,TIOCOUTQ,&pending))goto receive;
        if(!pending)break;
        if(g10_ms()>=deadline){errno=ETIMEDOUT;goto receive;}
        struct timespec wait={.tv_nsec=1000000};nanosleep(&wait,NULL);
    }
    /* Seven bytes at 9600 baud take ~7.3 ms. Keep the documented vendor's
     * 15 ms post-write turnaround to cover FIFO and shift-register drain. */
    {struct timespec delay={.tv_nsec=15000000};while(nanosleep(&delay,&delay)<0&&errno==EINTR){}}
    result=0;
receive:
    if(g10_direction(direction,0))result=-1;
    return result;
}
#endif
