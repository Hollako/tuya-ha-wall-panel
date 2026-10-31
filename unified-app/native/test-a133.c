/* Host fixtures only: remap every board path into a private temporary folder. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
static char test_root[128],out[32768];static int forbidden,brightness=128,backlight=1,fb_blank_calls,fail_enable;
static void pathmap(const char *path,char *mapped){snprintf(mapped,1024,"%s%s",test_root,path);}
static int test_open(const char *path,int flags,...){
 mode_t mode=0600;if(flags&O_CREAT){va_list ap;va_start(ap,flags);mode=va_arg(ap,int);va_end(ap);}
 if(strstr(path,"relay_third")||strstr(path,"relay_four")||strstr(path,"ttyS2")||strstr(path,"rk817"))forbidden++;
 char p[1024];if(!strncmp(path,"/sys/",5)||!strncmp(path,"/proc/vendor/",13)||!strncmp(path,"/dev/",5)||!strncmp(path,"/data/local/wallpanel/",21)){pathmap(path,p);path=p;if((flags&O_ACCMODE)==O_WRONLY)flags|=O_TRUNC;}
 return open(path,flags,mode);
}
static FILE *test_fopen(const char *path,const char *mode){char p[1024];if(!strncmp(path,"/sys/",5)||!strncmp(path,"/proc/vendor/",13)||!strncmp(path,"/data/local/wallpanel/",21)){pathmap(path,p);path=p;}return fopen(path,mode);}
static int test_access(const char *path,int mode){char p[1024];pathmap(path,p);return access(p,mode);}
static int test_ioctl(int fd,unsigned long request,...){(void)fd;va_list ap;va_start(ap,request);unsigned long *args=va_arg(ap,unsigned long*);va_end(ap);if(request==0x103)return brightness;if(request==0x102){brightness=(int)args[1];return 0;}if(request==0x104){if(fail_enable){fail_enable=0;return -1;}backlight=1;return 0;}if(request==0x105){backlight=0;return 0;}fb_blank_calls++;return -1;}
static int test_output(const char *line){assert(strlen(out)+strlen(line)<sizeof(out));strcat(out,line);return 0;}
#define open test_open
#define fopen test_fopen
#define access test_access
#define ioctl test_ioctl
#define WP_OUTPUT test_output
#define main adapter_main
#include "a133-core.c"
#undef main
#undef ioctl
#undef fopen
#undef access
#undef open
static void fixture(const char *path,const void *bytes,size_t size){char p[1024];pathmap(path,p);for(char *q=p+1;*q;q++)if(*q=='/'){*q=0;mkdir(p,0700);*q='/';}int fd=open(p,O_WRONLY|O_CREAT|O_TRUNC,0600);assert(fd>=0&&write(fd,bytes,size)==(ssize_t)size);close(fd);}
static void text(const char *p,const char *v){fixture(p,v,strlen(v));}
static void gpio(const char *key,int pin){unsigned char bytes[16]={0,0,0,69,0,0,0,1,0,0,0,(unsigned char)pin,0,0,0,0};char p[256];snprintf(p,sizeof(p),A133_DT"/%s",key);fixture(p,bytes,sizeof(bytes));}
static void input_key(int key){int fds[2];assert(!pipe2(fds,O_NONBLOCK));struct input_event e={.type=EV_KEY,.code=key,.value=1};assert(write(fds[1],&e,sizeof(e))==sizeof(e));assert(!process_input(fds[0],0,1));close(fds[0]);close(fds[1]);}
int main(void){
 strcpy(test_root,"/tmp/wallpanel-a133-fixture-XXXXXX");assert(mkdtemp(test_root));
 text("/sys/class/graphics/fb1/name","fb_gc9a01\n");text("/sys/class/graphics/fb1/virtual_size","240,240\n");text(A133_DT"/compatible","tk,negpio");unsigned char id[4]={0,0,0,69};fixture("/sys/firmware/devicetree/base/soc@2900000/pinctrl@300b000/phandle",id,4);gpio("knob-ad",9);gpio("knob-dc",10);gpio("k1-io",4);gpio("k2-io",5);
 text("/proc/vendor/relay_first","off");text("/proc/vendor/relay_second","off");text("/proc/vendor/lcd_ctrl","on");text("/proc/vendor/horn_mode","on");text("/proc/vendor/outside_pa","on");text("/dev/disp","");
 assert(a133_profile_valid());gpio("knob-ad",8);assert(!a133_profile_valid());gpio("knob-ad",9);text("/sys/class/graphics/fb1/virtual_size","1280,800");assert(!a133_profile_valid());text("/sys/class/graphics/fb1/virtual_size","240,240");assert(a133_profile_valid());
 char conf[1024];pathmap("/config",conf);text("/config","version=1\nprofile=a133\ndevice_id=test\nbuttons=0\nrelays=1\n");struct core_config parsed;assert(!core_config_read(conf,&parsed));text("/config","version=1\nprofile=g10\ndevice_id=test\nbuttons=0\nrelays=1\n");assert(core_config_read(conf,&parsed));text("/config","version=1\nprofile=a133\ndevice_id=test\nbuttons=1\nrelays=1\n");assert(core_config_read(conf,&parsed));
 strcpy(core_config.device_id,"test");core_config.relays=1;core_online=1;core_values[0]=core_values[1]=0;
 core_command("v1 1 relay 1 1");assert(a133_relay_read(1)==1);assert(strstr(out,"\"ok\":true"));out[0]=0;
 core_command("v1 1 relay 1 0");assert(a133_relay_read(1)==1);assert(strstr(out,"\"ok\":false"));out[0]=0;
 core_command("v1 2 relay 3 1");assert(strstr(out,"\"ok\":false"));assert(forbidden==0);core_command("v1 3 relay 1 0");assert(a133_relay_read(1)==0);core_config.relays=0;out[0]=0;core_command("v1 4 relay 2 1");assert(a133_relay_read(2)==0&&strstr(out,"module_disabled"));
 assert(a133_brightness_read()==128);assert(!a133_brightness_write(0)&&!backlight);assert(!a133_brightness_write(70)&&backlight&&brightness==70);a133_restore();assert(backlight&&brightness==128&&fb_blank_calls==0);
 assert(!a133_brightness_write(0));fail_enable=1;assert(a133_brightness_write(70)<0);a133_restore();assert(backlight&&brightness==128);
 out[0]=0;core_command("v1 5 bind 1 1 0");assert(strstr(out,"buttons_not_fitted"));
 assert(a133_knob_node("/proc/vendor/led_ctrl",1)==0&&forbidden==0);assert(core_knob_command("led","White")<0);
 core_config.knob_input=1;core_alive=1;rotary_fd=99;net=0;out[0]=0;gestures_cancel_all();input_key(KEY_F2);assert(!*out);input_key(KEY_F3);gestures_tick_at(ms()+401);assert(strstr(out,"\"event\":\"press\""));out[0]=0;gestures_cancel_all();input_key(KEY_F3);input_key(KEY_F3);assert(strstr(out,"double_press"));out[0]=0;input_key(KEY_F4);assert(strstr(out,"long_press"));rotary_fd=-1;
 struct rotary_decoder decoder;rotary_reset(&decoder,3);int right=0,left=0;for(int i=0;i<5;i++){int seq[]={2,0,1,3};for(int k=0;k<4;k++){int s=rotary_physical_direction(rotary_transition(&decoder,seq[k]));if(s>0)right++;else if(s<0)left++;}}assert(right==10&&!left);for(int i=0;i<5;i++){int seq[]={1,0,2,3};for(int k=0;k<4;k++){int s=rotary_physical_direction(rotary_transition(&decoder,seq[k]));if(s>0)right++;else if(s<0)left++;}}assert(right==10&&left==10);
 assert(!forbidden);puts("PASS A133: DT/geometry mismatch rejection, config isolation, two relay channels only, stale/disabled command guards, backlight-only ioctls/restoration, F3/F4 gestures, duplicate rotary suppression and half-step direction. Fake I/O only.");return 0;
}
