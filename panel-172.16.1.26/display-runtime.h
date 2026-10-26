/* Shared G10 backlight/input state machine for the app worker and init fallback. */
#include <linux/input.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <time.h>
#include "display-policy.h"
static struct pollfd bl_inputs[8];static int bl_count,bl_grabbed,bl_normal=120,bl_actual=-1,bl_phase,bl_touch,bl_release_pending,bl_manual_off,bl_app;
static long long bl_activity,bl_release_deadline;static struct dp_policy bl_policy={60,300,26};
static char bl_last_feedback[256];
#ifndef BL_BRIGHTNESS_PATH
#define BL_BRIGHTNESS_PATH "/sys/class/backlight/backlight/brightness"
#endif
#ifndef BL_INPUT_SYS
#define BL_INPUT_SYS "/sys/class/input"
#define BL_INPUT_DEV "/dev/input"
#endif
static long long bl_now(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
static int bl_read_actual(void){
    FILE *f=fopen(BL_BRIGHTNESS_PATH,"r");if(!f)return -1;char b[128];char *ok=fgets(b,sizeof(b),f);fclose(f);if(!ok)return -1;
    char *p=b;while(*p&&!isdigit((unsigned char)*p)&&*p!='-')p++;char *end;errno=0;long value=strtol(p,&end,10);
    if(p==end)return -1;while(isspace((unsigned char)*end))end++;return errno||*end||value<0||value>255?-1:(int)value;
}
#ifndef BL_WRITE
static int bl_write(int value){int fd=open(BL_BRIGHTNESS_PATH,O_WRONLY|O_CLOEXEC);if(fd<0)return -1;char b[24];int n=snprintf(b,sizeof(b),"%d\n",value);int ok=write(fd,b,n)==n;close(fd);return ok?0:-1;}
#define BL_WRITE bl_write
#endif
#ifndef BL_GRAB
static int bl_grab(int enabled){
    int ok=1;for(int i=0;i<bl_count;i++)if(ioctl(bl_inputs[i].fd,EVIOCGRAB,enabled)<0)ok=0;
    if(enabled&&!ok){for(int i=0;i<bl_count;i++)ioctl(bl_inputs[i].fd,EVIOCGRAB,0);return -1;}bl_grabbed=enabled;return 0;
}
#define BL_GRAB bl_grab
#endif
static void bl_feedback(void){
    char b[256];snprintf(b,sizeof(b),"v1 %d %d %d %d %d %d %d %d %lld\n",getpid(),bl_normal,bl_actual,bl_phase,bl_policy.dim_after,bl_policy.off_after,bl_policy.dim_brightness,bl_app,bl_activity);
    if(strcmp(b,bl_last_feedback)&&!dp_write(DP_FEEDBACK,b))strcpy(bl_last_feedback,b);
}
static int bl_apply(int phase){
    int target=dp_level(bl_policy,bl_normal,phase);
    if(target!=bl_actual){if(BL_WRITE(target))return -1;bl_actual=target;}bl_phase=phase;bl_feedback();return 0;
}
static void bl_close(void){
    if(bl_grabbed)BL_GRAB(0);
    for(int i=0;i<bl_count;i++)close(bl_inputs[i].fd);bl_count=0;bl_grabbed=bl_touch=bl_release_pending=bl_manual_off=0;bl_release_deadline=0;
    bl_last_feedback[0]=0;
}
static int bl_name_allowed(const char *s){return !strcmp(s,"goodix-ts")||!strcmp(s,"rk8xx_pwrkey")||!strcmp(s,"gpio_ir_recv")||!strcmp(s,"adc-keys");}
static int bl_open(int app){
    bl_app=app;bl_count=0;int touch_found=0;bl_actual=bl_read_actual();if(bl_actual<0)return -1;
    int saved=dp_integer(DP_STATE"/display-brightness",1,255);if(saved>0)bl_normal=saved;if(dp_load(&bl_policy))return -1;
    for(int i=0;i<32&&bl_count<8;i++){
        char path[256],name[128];snprintf(path,sizeof(path),BL_INPUT_SYS"/event%d/device/name",i);FILE *f=fopen(path,"r");if(!f)continue;
        char *ok=fgets(name,sizeof(name),f);fclose(f);if(!ok)continue;name[strcspn(name,"\r\n")]=0;if(!bl_name_allowed(name))continue;
        snprintf(path,sizeof(path),BL_INPUT_DEV"/event%d",i);int fd=open(path,O_RDONLY|O_NONBLOCK|O_CLOEXEC);if(fd<0){bl_close();return -1;}
        bl_inputs[bl_count++]=(struct pollfd){.fd=fd,.events=POLLIN};if(!strcmp(name,"goodix-ts"))touch_found=1;
    }
    if(!touch_found){bl_close();return -1;}
    bl_phase=bl_actual==0?2:bl_actual<bl_normal?1:0;bl_activity=bl_now();bl_manual_off=bl_phase==2;
    FILE *prior=fopen(DP_FEEDBACK,"r");int inherited=0;
    if(prior){int pid,normal,actual,phase,dim,off,level,app;long long at;char extra;
        int got=fscanf(prior,"v1 %d %d %d %d %d %d %d %d %lld %c",&pid,&normal,&actual,&phase,&dim,&off,&level,&app,&at,&extra);fclose(prior);
        if(got==9&&normal==bl_normal&&actual==bl_actual&&at>=0&&at<=bl_activity){bl_activity=at;inherited=1;}
    }
    if(!inherited&&bl_phase==1&&bl_policy.dim_after)bl_activity-=bl_policy.dim_after*1000LL;
    if(bl_phase==2&&BL_GRAB(1)){bl_close();return -1;}
    char pid[32];snprintf(pid,sizeof(pid),"%d\n",getpid());if(dp_write(DP_STATE"/backlight.pid",pid)){bl_close();return -1;}
    bl_feedback();return 0;
}
static int bl_reload(void){
    struct dp_policy policy;if(dp_load(&policy))return -1;int normal=dp_integer(DP_STATE"/display-brightness",1,255);if(normal<0)return -1;
    bl_policy=policy;bl_normal=normal;return bl_phase==2?(bl_feedback(),0):bl_apply(bl_phase);
}
static int bl_command(const char *field,int value,long long now){
    if(!strcmp(field,"brightness")){
        if(value<1||value>255)return -1;char b[32];snprintf(b,sizeof(b),"%d\n",value);if(dp_write(DP_STATE"/display-brightness",b))return -1;
        bl_normal=value;return bl_phase==2?(bl_feedback(),0):bl_apply(bl_phase);
    }
    if(!strcmp(field,"power")){
        if(value==0){bl_manual_off=1;return 0;}if(value!=1)return -1;
        if(bl_apply(0))return -1;bl_manual_off=0;BL_GRAB(0);bl_touch=0;bl_release_deadline=0;bl_activity=now;return 0;
    }
    if(dp_update(&bl_policy,field,value))return -1;bl_feedback();return 0;
}
static int bl_tick(long long now){
    if(bl_phase!=2&&!bl_touch&&!bl_grabbed){
        int desired=bl_manual_off?2:dp_phase(bl_policy,now-bl_activity);
        if(desired==2&&!bl_grabbed&&BL_GRAB(1))return -1;
        if(bl_apply(desired))return -1;
    }
    if(bl_phase!=2&&bl_grabbed&&bl_release_deadline&&now>=bl_release_deadline){BL_GRAB(0);bl_touch=0;bl_release_pending=0;bl_release_deadline=0;}
    return 0;
}
static int bl_event(const struct input_event *e,long long now){
    if(e->type==EV_KEY&&e->code==BTN_TOUCH)bl_touch=!!e->value;
    int press=(e->type==EV_KEY&&e->value==1)||(e->type==EV_ABS&&e->code==ABS_MT_TRACKING_ID&&e->value>=0)||(e->type==EV_REL&&e->value!=0);
    int activity=e->type==EV_KEY||e->type==EV_ABS||e->type==EV_REL;
    if(activity)bl_activity=now;
    if(bl_phase==2&&press){if(bl_apply(0))return -1;bl_manual_off=0;bl_release_deadline=now+15000;}
    else if(bl_phase==1&&activity){if(bl_apply(0))return -1;}
    if(bl_phase!=2&&bl_grabbed){
        if((e->type==EV_KEY&&e->value==0)||(e->type==EV_ABS&&e->code==ABS_MT_TRACKING_ID&&e->value<0))bl_release_pending=1;
        if(e->type==EV_SYN&&e->code==SYN_REPORT&&bl_release_pending&&!bl_touch){BL_GRAB(0);bl_release_deadline=0;bl_release_pending=0;}
    }return 0;
}
static int bl_receive(void){
    for(int i=0;i<bl_count;i++){
        struct input_event events[64];ssize_t bytes;
        while((bytes=read(bl_inputs[i].fd,events,sizeof(events)))>0){
            if(bytes%(ssize_t)sizeof(events[0]))return -1;
            for(size_t j=0;j<(size_t)bytes/sizeof(events[0]);j++)if(bl_event(&events[j],bl_now()))return -1;
        }
        if(bytes==0||(bytes<0&&errno!=EAGAIN&&errno!=EINTR))return -1;
    }return 0;
}
