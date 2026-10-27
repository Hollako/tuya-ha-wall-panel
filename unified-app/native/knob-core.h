/* Reuses the proven renderer, physical half-step decoder and gesture handling.
 * This worker has no network; MQTT publications become private JSON pipe events. */
#include <stdarg.h>
#include <linux/input.h>
#define STATE "/data/local/panalume"
#define BASE ""
static int dry_run,net,gesture_publish_failed;
static int core_knob_owner=-1,core_knob_input=-1,core_knob_started;
static long long ms(void){return g10_ms();}
static int core_knob_ring=1;
#define WP_KNOB_RING_VISIBLE() core_knob_ring
static int core_knob_speaker_muted,core_knob_page_muted;
static void core_knob_mute_draw(void);
static void core_knob_detail_draw(void);
#define WP_KNOB_DRAW_DETAIL() core_knob_detail_draw()
#define WP_KNOB_DRAW_OVERLAY() core_knob_mute_draw()
static int core_knob_saver_draw(void);
#define WP_KNOB_DRAW_SCREENSAVER() core_knob_saver_draw()
static void core_knob_activity_at(long long now);
static int core_knob_activity_power(long long now);
static int core_knob_activity_led(long long now);
#define WP_KNOB_EFFECTIVE_POWER() core_knob_activity_power(ms())
#define WP_KNOB_EFFECTIVE_LED() core_knob_activity_led(ms())
#define WP_KNOB_POWER_STATE() core_knob_activity_power(ms())
#define WP_KNOB_ACTIVITY() core_knob_activity_at(ms())
static void core_json_string(char *out,size_t size,const char *s){
    size_t n=0;for(const unsigned char *p=(const unsigned char*)s;*p&&n+7<size;p++){
        if(*p=='"'||*p=='\\'){out[n++]='\\';out[n++]=(char)*p;}
        else if(*p<32){n+=(size_t)snprintf(out+n,size-n,"\\u%04x",*p);}
        else out[n++]=(char)*p;
    }out[n]=0;
}
static int publish(const char *topic,const char *payload,int retain){
    if(!strcmp(topic,"/knob/input/availability"))return 0; /* Worker status owns input readiness. */
    (void)retain;char escaped[1024],line[1400];core_json_string(escaped,sizeof(escaped),payload);
    snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"knob_state\",\"topic\":\"%s\",\"payload\":\"%s\"}\n",topic,escaped);return WP_OUTPUT(line);
}
static int knob_notice(const char *format,...){
    /* Header diagnostics must not corrupt the structured stdout protocol. */
    (void)format;return 0;
}
#define puts knob_notice
#define printf knob_notice
#define running core_alive
#include "../../panel-172.16.1.26/panel-knob-display.h"
#include "../../panel-172.16.1.26/panel-rotary.h"
#include "../../panel-172.16.1.26/panel-input.h"
#undef puts
#undef printf
#undef running
#ifndef WP_KNOB_ACTIVITY_PATH
#define WP_KNOB_ACTIVITY_PATH STATE"/knob-activity"
#endif
/* Only local physical input extends this RAM-only deadline. HA frames, clock
 * updates and main-display changes must never wake the auxiliary LCD. */
static int core_knob_activity_enabled=1,core_knob_activity_last=-1,core_knob_led_physical=-1;
static long long core_knob_activity_until;
#include "knob-screensaver.h"
static void core_knob_detail_draw(void){
    // Muted media uses the old status-line position for the symbol. Retain
    // any track title underneath it, with enough clearance from the volume.
    if(core_knob_page_muted)knob_line(knob_text[2],214,18,120,0xb8c8d0);
    else knob_line(knob_text[2],188,22,180,0xb8c8d0);
}
static void core_knob_mute_pixel(int cx,int cy,int x,int y,unsigned int color){
    // Scale the 21-pixel symbol to about 29 pixels around its existing center.
    for(int yy=(y+2)*4/3;yy<(y+3)*4/3;yy++)for(int xx=(x+2)*4/3;xx<(x+3)*4/3;xx++)knob_pixel(cx+xx-14,cy+yy-14,color,255);
}
static void core_knob_mute_draw(void){
    int idle=saver_idle(ms());if(!(idle?core_knob_speaker_muted:core_knob_page_muted))return;
    int cx=idle?186:120,cy=idle?156:180;
    for(int yy=0;yy<=16;yy++)for(int xx=0;xx<=12;xx++){
        if((xx<=4&&yy>=5&&yy<=11)||(xx>=4&&abs(yy-8)<=xx-4))core_knob_mute_pixel(cx,cy,xx,yy,0xff4040);
    }
    for(int i=-2;i<=18;i++)for(int d=-1;d<=1;d++)core_knob_mute_pixel(cx,cy,i,i+d,0);
    for(int i=-2;i<=18;i++)core_knob_mute_pixel(cx,cy,i,i,0xff4040);
}
static int core_knob_activity_power(long long now){
    return core_knob_activity_enabled?(saver_enabled||core_knob_activity_until>now):knob_power&&(!knob_follow||knob_main_awake());
}
static int core_knob_activity_led(long long now){return core_knob_activity_enabled?core_knob_activity_until>now:knob_led;}
static void core_knob_activity_at(long long now){
    if(core_config.knob_input&&core_config.knob_display&&core_knob_activity_enabled){
        core_knob_activity_until=now+15000;knob_check_at=0;
    }
}
static void core_knob_activity_load(void){
    core_knob_activity_enabled=1;FILE *f=fopen(WP_KNOB_ACTIVITY_PATH,"r");if(!f)return;
    char b[32]={0};if(fgets(b,sizeof(b),f)&&(!strcmp(b,"0\n")||!strcmp(b,"1\n")))core_knob_activity_enabled=b[0]-'0';fclose(f);
}
static int core_knob_activity_save(int enabled){
    char temp[512];int n=snprintf(temp,sizeof(temp),"%s.new",WP_KNOB_ACTIVITY_PATH);if(n<0||(size_t)n>=sizeof(temp))return -1;
    FILE *f=fopen(temp,"w");if(!f)return -1;
    int bad=fprintf(f,"%d\n",enabled)<0||fflush(f)||fsync(fileno(f));if(fclose(f))bad=1;
    if(bad||chmod(temp,0600)||rename(temp,WP_KNOB_ACTIVITY_PATH))return -1;return 0;
}
#ifndef WP_KNOB_RING_PATH
#define WP_KNOB_RING_PATH STATE"/knob-ring"
#endif
static void core_knob_ring_load(void){
    core_knob_ring=1;FILE *f=fopen(WP_KNOB_RING_PATH,"r");if(!f)return;
    char b[8]={0};if(fgets(b,sizeof(b),f)&&(!strcmp(b,"0\n")||!strcmp(b,"1\n")))core_knob_ring=b[0]-'0';fclose(f);
}
static int core_knob_ring_save(int enabled){
    char temp[512];snprintf(temp,sizeof(temp),"%s.new",WP_KNOB_RING_PATH);
    FILE *f=fopen(temp,"w");if(!f)return -1;
    int bad=fprintf(f,"%d\n",enabled)<0||fflush(f)||fsync(fileno(f));if(fclose(f))bad=1;
    if(bad||chmod(temp,0600)||rename(temp,WP_KNOB_RING_PATH))return -1;return 0;
}
static void core_knob_activity_tick(long long now){
    if(!core_config.knob_display||!knob_ready)return;
    saver_tick(now);
    int active=core_knob_activity_power(now),led=core_knob_activity_led(now);
    if(active!=core_knob_activity_last){if(active)knob_dirty=1;knob_report=1;knob_check_at=0;core_knob_activity_last=active;}
    if(led!=core_knob_led_physical&&!knob_node("/proc/vendor/led_ctrl",led)){core_knob_led_physical=led;knob_report=1;}
}
static int core_knob_input_ready(void){return core_knob_started&&core_knob_input>=0&&rotary_fd>=0;}
static int core_knob_display_ready(void){return core_knob_started&&knob_ready;}
static void core_knob_close(void){
    gesture_cancel(0);rotary_cleanup();if(core_knob_input>=0)close(core_knob_input);core_knob_input=-1;
    if(knob_fb>=0)close(knob_fb);knob_fb=-1;knob_ready=0;core_knob_started=0;
    core_knob_activity_until=0;core_knob_activity_last=-1;core_knob_led_physical=-1;
}
static void core_knob_tick(void){
    if(core_knob_owner<0){
        core_knob_owner=wp_lock_acquire(WP_KNOB_RESOURCE);if(core_knob_owner<0)return;
    }
    if(!core_knob_started){
        core_knob_started=1;
        if(core_config.knob_input){core_knob_input=input_device("knod-aispeech");rotary_init();}
        if(core_config.knob_display){core_knob_activity_load();core_knob_ring_load();saver_load();knob_physical=-1;knob_check_at=0;knob_dirty=knob_report=1;knob_init();}
        core_status(0);
    }
    int before=core_knob_input_ready();
    if(core_config.knob_input){
        if(core_knob_input>=0&&process_input(core_knob_input,0,1)){close(core_knob_input);core_knob_input=-1;core_status(0);}
        if(rotary_receive(1))core_alive=0;
    }
    if(before!=core_knob_input_ready())core_status(0);
    gestures_tick_at(ms());
    if(core_config.knob_display){
        core_knob_activity_tick(ms());knob_tick();
        if(knob_report&&knob_ready)publish("/knob/display/activity/state",core_knob_activity_enabled?"ON":"OFF",1);
        if(knob_report&&knob_ready)publish("/knob/display/ring/state",core_knob_ring?"ON":"OFF",1);
        knob_publish();
    }
}
static int core_knob_payload(const char *field,const char *payload){
    if(!strcmp(field,"speaker_mute"))return !strcmp(payload,"ON")||!strcmp(payload,"OFF");
    if(!strcmp(field,"media_frame")){
        char b[512];if(strlen(payload)>=sizeof(b))return 0;strcpy(b,payload);
        char *flag=strrchr(b,'\n');if(!flag)return 0;*flag++=0;
        return (!strcmp(flag,"ON")||!strcmp(flag,"OFF"))&&core_knob_payload("frame",b);
    }
    if(!strcmp(field,"screensaver"))return saver_parse(payload,0);
    if(!strcmp(field,"frame")){
        char b[512];if(strlen(payload)>=sizeof(b))return 0;strcpy(b,payload);
        char *a=strchr(b,'\n');if(!a)return 0;*a++=0;char *c=strchr(a,'\n');if(!c)return 0;*c++=0;
        return !strchr(c,'\n')&&knob_valid_text(b,32)&&knob_valid_text(a,16)&&knob_valid_text(c,32);
    }
    for(int i=0;i<3;i++)if(!strcmp(field,knob_fields[i]))return knob_valid_text(payload,i==1?16:32);
    if(!strcmp(field,"power")||!strcmp(field,"follow_main")||!strcmp(field,"activity")||!strcmp(field,"ring"))return !strcmp(payload,"ON")||!strcmp(payload,"OFF");
    if(!strcmp(field,"color")){for(int i=0;i<7;i++)if(!strcmp(payload,knob_colors[i]))return 1;return 0;}
    return !strcmp(field,"led")&&(!strcmp(payload,"Red")||!strcmp(payload,"White"));
}
static int core_knob_command(const char *field,const char *payload){
    if(!core_config.knob_display||!knob_ready||!core_knob_payload(field,payload))return -1;
    if(!strcmp(field,"speaker_mute")){
        int muted=!strcmp(payload,"ON");if(muted!=core_knob_speaker_muted){core_knob_speaker_muted=muted;knob_dirty=1;knob_check_at=0;}return 0;
    }
    if(!strcmp(field,"media_frame")){
        char b[512];strcpy(b,payload);char *flag=strrchr(b,'\n');*flag++=0;
        int muted=!strcmp(flag,"ON");if(muted!=core_knob_page_muted){core_knob_page_muted=muted;knob_dirty=1;knob_check_at=0;}
        // Accept earlier HA media frames without displaying their textual mute label.
        if(muted){char *detail=strchr(b,'\n');if(detail)detail=strchr(detail+1,'\n');if(detail){detail++;
            if(!strcmp(detail,"Muted"))*detail=0;
            else if(!strncmp(detail,"Muted / ",8))memmove(detail,detail+8,strlen(detail+8)+1);
        }}
        knob_command("/knob/display/frame/set",b,0);return 0;
    }
    if(!strcmp(field,"frame")){if(core_knob_page_muted){core_knob_page_muted=0;knob_dirty=1;knob_check_at=0;}}
    if(!strcmp(field,"screensaver")){if(saver_save(payload))return -1;saver_parse(payload,1);return 0;}
    if(!strcmp(field,"ring")){
        int enabled=!strcmp(payload,"ON");
        if(enabled!=core_knob_ring&&core_knob_ring_save(enabled))return -1;
        core_knob_ring=enabled;knob_dirty=knob_report=1;knob_check_at=0;return 0;
    }
    if(!strcmp(field,"activity")){
        int enabled=!strcmp(payload,"ON");
        if(enabled!=core_knob_activity_enabled){
            if(core_knob_activity_save(enabled))return -1;
            core_knob_activity_enabled=enabled;core_knob_activity_until=0;core_knob_activity_last=-1;
        }
        knob_check_at=0;knob_report=1;return 0;
    }
    char topic[96];if(!strcmp(field,"led"))strcpy(topic,"/knob/led/color/set");
    else snprintf(topic,sizeof(topic),"/knob/display/%s/set",field);
    knob_command(topic,payload,0);return 0;
}
