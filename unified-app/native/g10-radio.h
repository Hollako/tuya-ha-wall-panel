/* G10 AP6212A coexistence correction. Fixed ioctl allowlist, no firmware writes.
 * Qualified on FWID 01-457251af: mode 1 starves A2DP, mode 4 clears playback.
 * Recheck after Wi-Fi driver reload; restore our original mode on orderly exit.
 * Never included in the A133 backend. */
#include <sys/socket.h>
#include <net/if.h>
typedef struct {uint32_t cmd;void *buf;uint32_t len;uint8_t set;uint32_t used,needed;} wp_radio_ioctl;
#ifndef WP_RADIO_SOCKET
#define WP_RADIO_SOCKET socket
#define WP_RADIO_IOCTL ioctl
#define WP_RADIO_CLOSE close
#endif
static int core_radio_original=-1,core_radio_changed;
static long long core_radio_next;
static int core_radio_io(int fd,uint32_t command,unsigned char *buf,size_t len,int set){
    struct ifreq request;memset(&request,0,sizeof(request));strcpy(request.ifr_name,"wlan0");
    wp_radio_ioctl io={.cmd=command,.buf=buf,.len=(uint32_t)len,.set=(uint8_t)set};request.ifr_data=(char*)&io;
    return WP_RADIO_IOCTL(fd,0x89f0,&request);
}
static int core_radio_open(void){
    int fd=WP_RADIO_SOCKET(AF_INET,SOCK_DGRAM|SOCK_CLOEXEC,0);if(fd<0)return -1;
    unsigned char b[512]={0};uint32_t magic=0;
    if(core_radio_io(fd,0,b,sizeof(b),0))goto reject;memcpy(&magic,b,4);if(magic!=0x14e46c77)goto reject;
    memset(b,0,sizeof(b));strcpy((char*)b,"ver");if(core_radio_io(fd,262,b,sizeof(b),0))goto reject;
    b[sizeof(b)-1]=0;if(!strstr((char*)b,"FWID 01-457251af"))goto reject;return fd;
reject:WP_RADIO_CLOSE(fd);return -1;
}
static int core_radio_mode(int fd){
    unsigned char b[64]={0};strcpy((char*)b,"btc_mode");if(core_radio_io(fd,262,b,sizeof(b),0))return -1;
    int32_t mode;memcpy(&mode,b,4);return mode;
}
static int core_radio_set(int fd,int32_t mode){
    unsigned char b[16]={0};memcpy(b,"btc_mode",9);memcpy(b+9,&mode,4);return core_radio_io(fd,263,b,13,1);
}
static void core_radio_tick(long long now){
    if(now<core_radio_next)return;core_radio_next=now+10000;
    int fd=core_radio_open();if(fd<0)return;int mode=core_radio_mode(fd);
    if(mode==1||mode==4){
        if(core_radio_original<0)core_radio_original=mode;
        if(mode==1&&!core_radio_set(fd,4))core_radio_changed=1;
    }
    WP_RADIO_CLOSE(fd);
}
static void core_radio_close(void){
    if(core_radio_changed&&core_radio_original==1){int fd=core_radio_open();if(fd>=0){if(core_radio_mode(fd)==4)core_radio_set(fd,1);WP_RADIO_CLOSE(fd);}}
    core_radio_original=-1;core_radio_changed=0;core_radio_next=0;
}
