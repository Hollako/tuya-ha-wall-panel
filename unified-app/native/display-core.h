#include "../../panel-172.16.1.26/display-runtime.h"
static int core_display_request=-1,core_display_owner=-1;
static char core_display_sent[768];
static void core_display_close(void){
    if(core_display_owner>=0){bl_close();close(core_display_owner);core_display_owner=-1;}
    if(core_display_request>=0){close(core_display_request);core_display_request=-1;}
    core_display_ready=0;core_display_sent[0]=0;
#ifdef WP_A133
    a133_restore();
#endif
}
static void core_display_tick(void){
    if(!core_config.display){if(core_display_request>=0)core_display_close();return;}
    if(core_display_request<0){core_display_request=wp_lock_acquire(WP_DISPLAY_REQUEST);if(core_display_request<0)return;}
    if(core_display_owner<0){
        core_display_owner=wp_lock_acquire(WP_DISPLAY_RESOURCE);if(core_display_owner<0)return;
        if(bl_open(1)){close(core_display_owner);core_display_owner=-1;core_display_close();core_exit_code=10;core_alive=0;return;}
        core_display_ready=1;core_status(0);
    }
    if(bl_receive()||bl_tick(bl_now())){core_display_ready=0;core_status(0);core_exit_code=10;core_alive=0;return;}
    char json[768];if(!dp_feedback(json,sizeof(json))&&strcmp(json,core_display_sent)){
        char line[900];snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"display_state\",\"data\":%s}\n",json);WP_OUTPUT(line);strcpy(core_display_sent,json);
    }
}
