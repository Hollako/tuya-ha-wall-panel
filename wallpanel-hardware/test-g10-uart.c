/* Failure-path tests use fake syscalls; never open a real device. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>
static int owner_calls,owner_first,owner_second,opens,closes,mode,mode_calls;
static int fail_write,fail_termios,partial,uart_written,set_calls,excl,fail_receive;
static struct termios settings;
static unsigned char written[7];
static int fake_owner(const char *s){(void)s;return ++owner_calls==1?owner_first:owner_second;}
static int fake_open(const char *p,int flags,...){
    (void)flags;if(!strcmp(p,"DEVICE")){opens++;return 77;}
    assert(!strcmp(p,"DIRECTION"));return 88;
}
static int fake_close(int fd){if(fd==77)closes++;else assert(fd==88);return 0;}
static ssize_t fake_write(int fd,const void *buf,size_t size){
    if(fd==88){
        int sending=size==5&&!memcmp(buf,"send\n",5);
        assert(sending||(size==8&&!memcmp(buf,"receive\n",8)));mode_calls++;
        if(!sending&&fail_receive){errno=EIO;return -1;}
        mode=sending;return (ssize_t)size;
    }
    assert(fd==77&&mode==1);
    if(fail_write){errno=EIO;return -1;}
    if(partial&&size>2)size=2;
    assert(uart_written+(int)size<=7);memcpy(written+uart_written,buf,size);uart_written+=(int)size;
    return (ssize_t)size;
}
static int fake_ioctl(int fd,unsigned long request,...){
    assert(fd==77);
    if(request==TIOCEXCL){excl=1;return 0;}
    if(request==TIOCNXCL){excl=0;return 0;}
    assert(request==TIOCOUTQ);va_list a;va_start(a,request);int *pending=va_arg(a,int*);va_end(a);*pending=0;return 0;
}
static int fake_tcgetattr(int fd,struct termios *t){assert(fd==77);*t=settings;return 0;}
static int fake_tcsetattr(int fd,int action,const struct termios *t){
    assert(fd==77&&action==TCSANOW);set_calls++;
    if(fail_termios){errno=EIO;return -1;}settings=*t;return 0;
}
static int fake_tcflush(int fd,int which){assert(fd==77&&which==TCIFLUSH);return 0;}
#define G10_OWNER_CHECK fake_owner
#define open fake_open
#define close fake_close
#define write fake_write
#define ioctl fake_ioctl
#define tcgetattr fake_tcgetattr
#define tcsetattr fake_tcsetattr
#define tcflush fake_tcflush
#include "g10_uart.h"
#undef open
#undef close
#undef write
#undef ioctl
#undef tcgetattr
#undef tcsetattr
#undef tcflush
static void reset(void){
    owner_calls=owner_first=owner_second=opens=closes=mode=mode_calls=0;
    fail_write=fail_termios=partial=uart_written=set_calls=excl=fail_receive=0;
    memset(&settings,0,sizeof(settings));cfsetispeed(&settings,B115200);cfsetospeed(&settings,B115200);
}
int main(void){
    struct g10_uart u;unsigned char frame[7];g10_query(frame);
    reset();owner_first=123;assert(g10_uart_open(&u,"DEVICE","DIRECTION")<0&&errno==EBUSY&&!opens&&!mode_calls);
    reset();owner_first=-1;assert(g10_uart_open(&u,"DEVICE","DIRECTION")<0&&!opens&&!mode_calls);
    reset();owner_second=456;assert(g10_uart_open(&u,"DEVICE","DIRECTION")<0&&opens==1&&closes==1&&!excl&&!mode_calls);
    reset();assert(!g10_uart_open(&u,"DEVICE","DIRECTION"));
    assert(excl&&mode==0&&cfgetispeed(&settings)==B9600&&cfgetospeed(&settings)==B9600);
    assert((settings.c_cflag&CSIZE)==CS8&&!(settings.c_cflag&(PARENB|CSTOPB|CRTSCTS)));
    partial=1;assert(!g10_uart_send(&u,"DIRECTION",frame));assert(uart_written==7&&!memcmp(written,frame,7)&&mode==0);
    g10_uart_close(&u,"DIRECTION");assert(u.fd==-1&&!excl&&closes==1&&cfgetispeed(&settings)==B115200);
    reset();assert(!g10_uart_open(&u,"DEVICE","DIRECTION"));fail_write=1;
    assert(g10_uart_send(&u,"DIRECTION",frame)<0&&mode==0);g10_uart_close(&u,"DIRECTION");assert(!excl&&closes==1);
    reset();assert(!g10_uart_open(&u,"DEVICE","DIRECTION"));
    int before=mode_calls;unsigned char invalid[7]={0xfc,0,0,0xa3,1,0,0xfe};
    assert(g10_uart_send(&u,"DIRECTION",invalid)<0&&mode_calls==before&&!uart_written);g10_uart_close(&u,"DIRECTION");
    reset();fail_termios=1;assert(g10_uart_open(&u,"DEVICE","DIRECTION")<0&&closes==1&&!excl&&u.fd==-1);
    reset();assert(!g10_uart_open(&u,"DEVICE","DIRECTION"));fail_receive=1;
    assert(g10_uart_send(&u,"DIRECTION",frame)<0);fail_receive=0;g10_uart_close(&u,"DIRECTION");
    puts("PASS: busy/unknown/racing owners refused, 9600 8N1, partial writes, TX failure returns RX, forbidden commands rejected before I/O, cleanup restores termios/exclusivity, RX failure reported.");return 0;
}
