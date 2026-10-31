#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <net/if.h>
static int mode=1,writes,opens,good_magic=1,good_firmware=1,fail_set;
static int fake_socket(int a,int b,int c){(void)a;(void)b;(void)c;opens++;return 19;}
static int fake_ioctl(int fd,unsigned long command,void *request);
static int fake_close(int fd){assert(fd==19);return 0;}
#define WP_RADIO_SOCKET fake_socket
#define WP_RADIO_IOCTL fake_ioctl
#define WP_RADIO_CLOSE fake_close
#include "g10-radio.h"
static int fake_ioctl(int fd,unsigned long command,void *request){
    assert(fd==19&&command==0x89f0);struct ifreq *r=request;assert(!strcmp(r->ifr_name,"wlan0"));wp_radio_ioctl *i=(void*)r->ifr_data;
    if(i->cmd==0){assert(!i->set);uint32_t magic=good_magic?0x14e46c77:0;memcpy(i->buf,&magic,4);return 0;}
    if(i->cmd==262){assert(!i->set);if(!strcmp(i->buf,"ver"))strcpy(i->buf,good_firmware?"wl0 FWID 01-457251af":"other radio firmware");else{assert(!strcmp(i->buf,"btc_mode"));memcpy(i->buf,&mode,4);}return 0;}
    assert(i->cmd==263&&i->set&&i->len==13&&!strcmp(i->buf,"btc_mode"));int value;memcpy(&value,(char*)i->buf+9,4);assert(value==1||value==4);writes++;if(fail_set)return -1;mode=value;return 0;
}
int main(void){
    core_radio_tick(1);assert(mode==4&&writes==1);int before=opens;core_radio_tick(100);assert(opens==before);
    core_radio_tick(10001);assert(writes==1);mode=1;core_radio_tick(20001);assert(mode==4&&writes==2);core_radio_close();assert(mode==1&&writes==3);
    good_magic=0;core_radio_tick(1);assert(writes==3);core_radio_close();good_magic=1;good_firmware=0;core_radio_tick(1);assert(writes==3);core_radio_close();good_firmware=1;
    mode=2;core_radio_tick(1);core_radio_close();assert(mode==2&&writes==3);
    mode=4;core_radio_tick(1);core_radio_close();assert(mode==4&&writes==3);
    mode=1;fail_set=1;core_radio_tick(1);core_radio_close();assert(mode==1&&writes==4);fail_set=0;
    core_radio_tick(1);mode=2;core_radio_close();assert(mode==2&&writes==5);
    puts("PASS G10 radio: exact firmware/magic gate, fixed mode allowlist, retry after Wi-Fi reload, throttling, failed writes, baseline restoration and external-mode preservation. Fake I/O only.");
}
