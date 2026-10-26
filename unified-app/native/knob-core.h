/* Reuses the proven renderer, physical half-step decoder and gesture handling.
 * This worker has no network; MQTT publications become private JSON pipe events. */
#include <stdarg.h>
#include <linux/input.h>
#define STATE "/data/local/panalume"
#define BASE ""
static int dry_run,net,gesture_publish_failed;
static int core_knob_owner=-1,core_knob_input=-1,core_knob_started;
static long long ms(void){return g10_ms();}
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
static int core_knob_input_ready(void){return core_knob_started&&core_knob_input>=0&&rotary_fd>=0;}
static int core_knob_display_ready(void){return core_knob_started&&knob_ready;}
static void core_knob_close(void){
    gesture_cancel(0);rotary_cleanup();if(core_knob_input>=0)close(core_knob_input);core_knob_input=-1;
    if(knob_fb>=0)close(knob_fb);knob_fb=-1;knob_ready=0;core_knob_started=0;
}
static void core_knob_tick(void){
    if(core_knob_owner<0){
        core_knob_owner=wp_lock_acquire(WP_KNOB_RESOURCE);if(core_knob_owner<0)return;
    }
    if(!core_knob_started){
        core_knob_started=1;
        if(core_config.knob_input){core_knob_input=input_device("knod-aispeech");rotary_init();}
        if(core_config.knob_display){knob_physical=-1;knob_check_at=0;knob_dirty=knob_report=1;knob_init();}
        core_status(0);
    }
    int before=core_knob_input_ready();
    if(core_config.knob_input){
        if(core_knob_input>=0&&process_input(core_knob_input,0,1)){close(core_knob_input);core_knob_input=-1;core_status(0);}
        if(rotary_receive(1))core_alive=0;
    }
    if(before!=core_knob_input_ready())core_status(0);
    gestures_tick_at(ms());
    if(core_config.knob_display){knob_tick();knob_publish();}
}
static int core_knob_payload(const char *field,const char *payload){
    if(!strcmp(field,"frame")){
        char b[512];if(strlen(payload)>=sizeof(b))return 0;strcpy(b,payload);
        char *a=strchr(b,'\n');if(!a)return 0;*a++=0;char *c=strchr(a,'\n');if(!c)return 0;*c++=0;
        return !strchr(c,'\n')&&knob_valid_text(b,32)&&knob_valid_text(a,16)&&knob_valid_text(c,32);
    }
    for(int i=0;i<3;i++)if(!strcmp(field,knob_fields[i]))return knob_valid_text(payload,i==1?16:32);
    if(!strcmp(field,"power")||!strcmp(field,"follow_main"))return !strcmp(payload,"ON")||!strcmp(payload,"OFF");
    if(!strcmp(field,"color")){for(int i=0;i<7;i++)if(!strcmp(payload,knob_colors[i]))return 1;return 0;}
    return !strcmp(field,"led")&&(!strcmp(payload,"Red")||!strcmp(payload,"White"));
}
static int core_knob_command(const char *field,const char *payload){
    if(!core_config.knob_display||!knob_ready||!core_knob_payload(field,payload))return -1;
    char topic[96];if(!strcmp(field,"led"))strcpy(topic,"/knob/led/color/set");
    else snprintf(topic,sizeof(topic),"/knob/display/%s/set",field);
    knob_command(topic,payload,0);return 0;
}
