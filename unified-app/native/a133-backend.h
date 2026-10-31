/* Exact tested A133 wiring. Never writes GPIO configuration or framebuffer 0. */
#include <dirent.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#define A133_DT "/sys/firmware/devicetree/base/soc@2900000/negpio"
static unsigned a133_be(const unsigned char *p){return (unsigned)p[0]<<24|(unsigned)p[1]<<16|(unsigned)p[2]<<8|p[3];}
static int a133_read(const char *path,char *out,size_t size){int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;ssize_t n=read(fd,out,size-1);close(fd);if(n<=0)return -1;out[n]=0;while(n>0&&(out[n-1]=='\n'||out[n-1]=='\r'))out[--n]=0;return 0;}
static int a133_property(const char *path,unsigned char *out,size_t size){int fd=open(path,O_RDONLY|O_CLOEXEC);if(fd<0)return -1;ssize_t n=read(fd,out,size);close(fd);return n==(ssize_t)size?0:-1;}
static int a133_gpio_property(const char *name,unsigned pin){char path[256];unsigned char value[16],bank[4];snprintf(path,sizeof(path),A133_DT"/%s",name);return !a133_property(path,value,16)&&!a133_property("/sys/firmware/devicetree/base/soc@2900000/pinctrl@300b000/phandle",bank,4)&&a133_be(value)==a133_be(bank)&&a133_be(value+4)==1&&a133_be(value+8)==pin&&a133_be(value+12)==0;}
static int a133_profile_valid(void){char name[80],compatible[80];return !a133_read("/sys/class/graphics/fb1/name",name,sizeof(name))&&!strcmp(name,"fb_gc9a01")&&!a133_read("/sys/class/graphics/fb1/virtual_size",name,sizeof(name))&&!strcmp(name,"240,240")&&!a133_read(A133_DT"/compatible",compatible,sizeof(compatible))&&!strcmp(compatible,"tk,negpio")&&a133_gpio_property("knob-ad",9)&&a133_gpio_property("knob-dc",10)&&a133_gpio_property("k1-io",4)&&a133_gpio_property("k2-io",5)&&access("/proc/vendor/relay_first",R_OK)==0&&access("/proc/vendor/relay_second",R_OK)==0;}
static int a133_conflict(void){DIR *d=opendir("/proc");if(!d)return 1;struct dirent *e;int found=0;while((e=readdir(d))){if(!isdigit((unsigned char)e->d_name[0]))continue;char p[128],b[256];snprintf(p,sizeof(p),"/proc/%.20s/cmdline",e->d_name);if(a133_read(p,b,sizeof(b)))continue;const char *names[]={"com.sznaner.bgmz9","com.sznaner.rotaryknob","com.sznaner.relay"};for(int i=0;i<3;i++){size_t n=strlen(names[i]);if(!strncmp(b,names[i],n)&&(b[n]==0||b[n]==':'))found=1;}}closedir(d);return found;}
static int a133_write_node(const char *path,int value){int fd=open(path,O_WRONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;const char *s=value?"on":"off";size_t n=strlen(s);int bad=write(fd,s,n)!=(ssize_t)n;close(fd);return bad?-1:0;}
static const char *a133_relay_paths[]={"/proc/vendor/relay_first","/proc/vendor/relay_second"};
static int a133_relay_read(int channel){char b[16];if(channel<1||channel>2||a133_read(a133_relay_paths[channel-1],b,sizeof(b)))return -1;return !strcmp(b,"on")?1:!strcmp(b,"off")?0:-1;}
static int a133_relay_write(int channel,int value){if(channel<1||channel>2||value<0||value>1)return -1;return a133_write_node(a133_relay_paths[channel-1],value)||a133_relay_read(channel)!=value?-1:0;}
static int a133_disp=-1,a133_original_brightness=-1,a133_main_on=1,a133_main_touched;
static int a133_disp_open(void){if(a133_disp>=0)return 0;a133_disp=open("/dev/disp",O_RDWR|O_CLOEXEC);if(a133_disp<0)return -1;unsigned long args[4]={0};int n=ioctl(a133_disp,0x103,args);if(n<0||n>255){close(a133_disp);a133_disp=-1;return -1;}a133_original_brightness=n;return 0;}
static int a133_brightness_read(void){if(a133_disp_open())return -1;if(!a133_main_on)return 0;unsigned long args[4]={0};return ioctl(a133_disp,0x103,args);}
static int a133_brightness_write(int value){if(value<0||value>255||a133_disp_open())return -1;unsigned long args[4]={0,(unsigned long)value,0,0};a133_main_touched=1;if(value){if(ioctl(a133_disp,0x102,args)<0||ioctl(a133_disp,0x104,args)<0)return -1;}else if(ioctl(a133_disp,0x105,args)<0)return -1;a133_main_on=value>0;a133_main_touched=1;return 0;}
static int a133_knob_node(const char *path,int value){if(!strcmp(path,"/proc/vendor/led_ctrl"))return 0;return strcmp(path,"/proc/vendor/lcd_ctrl")?-1:a133_write_node(path,value);}
static int a133_main_awake(void){return a133_main_on;}
static unsigned char a133_saved_frame[115200];static int a133_frame_saved,a133_lcd_saved=-1;
static int a133_knob_snapshot(void){if(a133_frame_saved)return 0;char b[16];if(a133_read("/proc/vendor/lcd_ctrl",b,sizeof(b)))return -1;a133_lcd_saved=!strcmp(b,"on")?1:!strcmp(b,"off")?0:-1;if(a133_lcd_saved<0)return -1;int fd=open("/dev/graphics/fb1",O_RDONLY|O_CLOEXEC);if(fd<0)return -1;size_t at=0;while(at<sizeof(a133_saved_frame)){ssize_t n=read(fd,a133_saved_frame+at,sizeof(a133_saved_frame)-at);if(n<=0){close(fd);return -1;}at+=(size_t)n;}close(fd);a133_frame_saved=1;return 0;}
static void a133_knob_restore(void){if(!a133_frame_saved)return;int fd=open("/dev/graphics/fb1",O_WRONLY|O_CLOEXEC);if(fd>=0){size_t at=0;while(at<sizeof(a133_saved_frame)){ssize_t n=write(fd,a133_saved_frame+at,sizeof(a133_saved_frame)-at);if(n<=0)break;at+=(size_t)n;}close(fd);}a133_write_node("/proc/vendor/lcd_ctrl",a133_lcd_saved);a133_frame_saved=0;}
static void a133_restore(void){if(a133_disp>=0){if(a133_main_touched){unsigned long args[4]={0,(unsigned long)a133_original_brightness,0,0};ioctl(a133_disp,0x102,args);ioctl(a133_disp,a133_original_brightness?0x104:0x105,args);}close(a133_disp);a133_disp=-1;}}
#define WP_PROFILE_VALID a133_profile_valid
#define WP_KNOB_FB_PATH "/dev/graphics/fb1"
#define WP_KNOB_INPUT_NAME "sunxi-keyboard"
#define WP_KEY_LEFT KEY_F1
#define WP_KEY_RIGHT KEY_F2
#define WP_KEY_PRESS KEY_F3
#define WP_KEY_HOLD KEY_F4
#define WP_ROTARY_HEADER "a133-rotary.h"
#define WP_KNOB_NODE a133_knob_node
#define WP_KNOB_MAIN_AWAKE a133_main_awake
#define BL_READ_ACTUAL a133_brightness_read
#define BL_WRITE a133_brightness_write
#define BL_TOUCH_NAME "gslX680"
#define BL_NAME_ALLOWED(s) (!strcmp((s),"gslX680"))
