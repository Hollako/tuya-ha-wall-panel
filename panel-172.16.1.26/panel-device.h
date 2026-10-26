/* Display commands use the existing input-grabbing backlight controller.
 * Slow screenshots and graceful reboot run in separate bounded children. */
#include "display-policy.h"
static pid_t device_capture=-1,device_rebooter=-1;
static long long device_capture_at,device_capture_requested,device_reboot_at;
static unsigned device_capture_seq;
static int device_report=1;
static char device_sent[1024],device_error[96];

static int device_integer(const char *path,int minimum,int maximum){
    FILE *f=fopen(path,"r");if(!f)return -1;
    char line[128];char *ok=fgets(line,sizeof(line),f);fclose(f);if(!ok)return -1;
    char *p=line;while(*p&&!isdigit((unsigned char)*p)&&*p!='-')p++;
    char *end;errno=0;long value=strtol(p,&end,10);
    while(isspace((unsigned char)*end))end++;
    return errno||p==end||*end||value<minimum||value>maximum?-1:(int)value;
}
static int device_backlight_signal(int signal){
    int pid=device_integer(STATE"/backlight.pid",2,4194304);if(pid<0)return -1;
    char path[128],comm[64];snprintf(path,sizeof(path),"/proc/%d/comm",pid);
    FILE *f=fopen(path,"r");if(!f)return -1;
    char *ok=fgets(comm,sizeof(comm),f);fclose(f);
    if(!ok||strcmp(comm,"panel-backlight\n"))return -1;
    return kill(pid,signal);
}
static int device_brightness(int value){
    if(value<1||value>255)return -1;
    if(device_integer(STATE"/display-brightness",1,255)!=value){
        int fd=open(STATE"/display-brightness.new",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600);
        if(fd<0)return -1;
        char buffer[16];int n=snprintf(buffer,sizeof(buffer),"%d\n",value);
        int ok=write(fd,buffer,n)==n;close(fd);
        if(!ok||rename(STATE"/display-brightness.new",STATE"/display-brightness"))return -1;
    }
    return device_backlight_signal(SIGHUP);
}
static void device_command(const char *topic,const char *payload,int retained){
    if(retained||dry_run)return;
#ifdef WALLPANEL_DIRECT_MCU
    if(!strcmp(topic,BASE"/device/command/set")&&(!strcmp(payload,"REBOOT")||!strcmp(payload,"SCREENSHOT"))&&(device_external||wp_lock_held(WP_MANAGEMENT_REQUEST)))return;
    int display_topic=!strcmp(topic,BASE"/device/brightness/set")||!strncmp(topic,BASE"/display/policy/",strlen(BASE"/display/policy/"))||(!strcmp(topic,BASE"/device/command/set")&&(!strcmp(payload,"DISPLAY_ON")||!strcmp(payload,"DISPLAY_OFF")));
    if(display_topic&&wp_lock_held(WP_DISPLAY_REQUEST))return;
#endif
    int result=0;
    if(!strncmp(topic,BASE"/display/policy/",strlen(BASE"/display/policy/"))){
        const char *field=topic+strlen(BASE"/display/policy/");char key[32];size_t n=strlen(field);
        if(n<5||n>=sizeof(key)||strcmp(field+n-4,"/set"))return;memcpy(key,field,n-4);key[n-4]=0;
        char *end;errno=0;long value=strtol(payload,&end,10);if(errno||end==payload||*end||value<0||value>86400)return;
        struct dp_policy policy;result=dp_load(&policy)||dp_update(&policy,key,(int)value)||device_backlight_signal(SIGHUP);
    }else if(!strcmp(topic,BASE"/device/brightness/set")){
        char *end;errno=0;long value=strtol(payload,&end,10);
        if(errno||end==payload||*end||value<1||value>255)return;
        result=device_brightness(value);
    }else if(!strcmp(topic,BASE"/device/command/set")){
        if(!strcmp(payload,"DISPLAY_ON"))result=device_backlight_signal(SIGUSR1);
        else if(!strcmp(payload,"DISPLAY_OFF"))result=device_backlight_signal(SIGUSR2);
        else if(!strcmp(payload,"REBOOT")){
            if(!device_reboot_at&&device_rebooter<0)device_reboot_at=ms()+1500;
        }else if(!strcmp(payload,"SCREENSHOT")){
            if(device_capture>0||ms()-device_capture_requested<5000)return;
            device_capture_requested=ms();device_capture=fork();
            if(!device_capture){
                setpgid(0,0);
                for(int fd=3;fd<1024;fd++)close(fd);
                execl(STATE"/panel-screenshot","panel-screenshot",(char*)NULL);_exit(127);
            }
            if(device_capture>0){setpgid(device_capture,device_capture);device_capture_at=ms();}
            else result=-1;
        }else return;
    }else return;
    snprintf(device_error,sizeof(device_error),"%s",result?"Panel command could not be applied":"");device_report=1;
}
static void device_tick(void){
    long long now=ms();
    if(device_capture>0){
        int status;
        if(waitpid(device_capture,&status,WNOHANG)==device_capture){
            kill(-device_capture,SIGTERM);device_capture=-1;
            if(WIFEXITED(status)&&WEXITSTATUS(status)==0){device_capture_seq++;device_error[0]=0;}
            else snprintf(device_error,sizeof(device_error),"Screenshot capture or MQTT delivery failed");
            device_report=1;
        }else if(now-device_capture_at>25000)kill(-device_capture,SIGKILL);
    }
    if(device_reboot_at&&now>=device_reboot_at&&device_rebooter<0){
        device_reboot_at=0;device_rebooter=fork();
        if(!device_rebooter){
            int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}
            /* This firmware's svc has no shebang; exec it through Android sh. */
            execl("/system/bin/sh","sh","/system/bin/svc","power","reboot",(char*)NULL);_exit(127);
        }
        if(device_rebooter<0)snprintf(device_error,sizeof(device_error),"Could not start graceful reboot");
        device_report=1;
    }
    if(device_rebooter>0){int status;
        if(waitpid(device_rebooter,&status,WNOHANG)==device_rebooter){
            device_rebooter=-1;
            if(!WIFEXITED(status)||WEXITSTATUS(status))snprintf(device_error,sizeof(device_error),"Android rejected graceful reboot");
            device_report=1;
        }
    }
}
static int device_publish(void){
    int actual=device_integer("/sys/class/backlight/backlight/brightness",0,255);
    int wanted=device_integer(STATE"/display-brightness",1,255);if(wanted<0)wanted=120;
    char state[1024];
    snprintf(state,sizeof(state),"{\"protocol_version\":1,\"owner\":\"legacy\",\"management_available\":true,\"display_on\":%s,\"brightness\":%d,\"actual_brightness\":%d,\"screenshot_sequence\":%u,\"screenshot_busy\":%s,\"reboot_pending\":%s,\"error\":\"%s\"}",
        actual>0?"true":"false",actual>0?actual:wanted,actual,device_capture_seq,device_capture>0?"true":"false",device_reboot_at||device_rebooter>0?"true":"false",device_error);
    if(!device_external&&(device_report||strcmp(state,device_sent))){
        if(publish(BASE"/device/state",state,1))return -1;
        strcpy(device_sent,state);device_report=0;
    }
#ifdef WALLPANEL_DIRECT_MCU
    if(!wp_lock_held(WP_DISPLAY_REQUEST)){
#endif
        char display[768];if(!dp_feedback(display,sizeof(display))&&publish(BASE"/display/state",display,1))return -1;
#ifdef WALLPANEL_DIRECT_MCU
    }
#endif
    return 0;
}
static void device_cleanup(void){if(device_capture>0)kill(-device_capture,SIGTERM);}
