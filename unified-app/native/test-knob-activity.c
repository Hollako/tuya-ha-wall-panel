/* Deterministic clock, fake LED writes and input pipes only. No panel I/O. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <string.h>
#include <stdio.h>
static char settings_path[256];
static int led_value=-1,led_writes;
static int fake_node(const char *path,int value){
    assert(!strcmp(path,"/proc/vendor/led_ctrl"));led_value=value;led_writes++;return 0;
}
#define WP_KNOB_NODE fake_node
#define WP_KNOB_ACTIVITY_PATH settings_path
static int capture(const char *s){(void)s;return 0;}
#define WP_OUTPUT capture
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    char directory[]="/tmp/wp-knob-activity-XXXXXX";assert(mkdtemp(directory));
    snprintf(settings_path,sizeof(settings_path),"%s/policy",directory);
    core_config.knob_input=core_config.knob_display=1;knob_ready=1;
    core_knob_activity_load();assert(core_knob_activity_enabled);
    knob_power=1;knob_follow=0;knob_led=1;
    core_knob_activity_tick(1000);assert(!core_knob_activity_power(1000)&&led_value==0);
    core_knob_activity_at(2000);core_knob_activity_tick(2000);
    assert(core_knob_activity_until==17000&&core_knob_activity_power(2000)&&led_value==1);
    core_knob_activity_tick(16999);assert(core_knob_activity_power(16999)&&led_value==1);
    int writes=led_writes;core_knob_activity_at(16999);core_knob_activity_tick(17000);
    assert(core_knob_activity_until==31999&&core_knob_activity_power(17000)&&led_writes==writes);
    core_knob_activity_tick(31998);assert(led_value==1);
    core_knob_activity_tick(31999);assert(!core_knob_activity_power(31999)&&led_value==0);
    assert(access(settings_path,F_OK)<0); // No flash writes for use or expiry.
    knob_power=0;knob_follow=1;core_knob_activity_at(40000);
    assert(core_knob_activity_power(40000)); // Bypasses main screen and manual power.
    long long deadline=core_knob_activity_until;
    assert(!core_knob_command("frame","Clock\n12:34\nMonday"));
    assert(core_knob_activity_until==deadline); // Background data cannot wake/reset it.
    core_config.knob_input=0;core_knob_activity_at(50000);assert(core_knob_activity_until==deadline);
    core_config.knob_input=1;core_config.knob_display=0;core_knob_activity_at(50000);assert(core_knob_activity_until==deadline);
    core_config.knob_display=1;
    assert(!core_knob_command("activity","OFF"));assert(!core_knob_activity_enabled);
    knob_power=1;knob_follow=0;knob_led=1;
    assert(core_knob_activity_power(999999)&&core_knob_activity_led(999999)==1);
    core_knob_activity_enabled=1;core_knob_activity_load();assert(!core_knob_activity_enabled);
    assert(!core_knob_command("activity","ON"));assert(!core_knob_activity_until);
    assert(core_knob_command("activity","toggle")==-1);
    struct stat st;assert(!stat(settings_path,&st)&&(st.st_mode&0777)==0600);
    int pipefd[2];assert(!pipe2(pipefd,O_NONBLOCK));
    struct input_event e={.type=EV_KEY,.code=KEY_FN_F3,.value=1};
    assert(write(pipefd[1],&e,sizeof(e))==sizeof(e));
    core_knob_activity_until=0;long long before=ms();
    assert(!process_input(pipefd[0],0,1));
    assert(core_knob_activity_until>=before+15000); // Wake before 400 ms click detection.
    assert(gesture_pending[0].deadline>0); // Original click remains queued for HA.
    close(pipefd[0]);close(pipefd[1]);unlink(settings_path);rmdir(directory);
    puts("PASS: knob-only activity wake, 15-second boundary/reset, local white/red transitions, no flash churn, no frame-update wake, module guards, persisted mode and immediate first press.");
    return 0;
}
