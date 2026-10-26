#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <string.h>
static int writes,level=-1;
static int raw_fake(void){return 40;}
static int trim_fake(int v){writes++;level=v;return 0;}
static int capture(const char *line){(void)line;return 0;}
#define WP_AUDIO_RAW raw_fake
#define WP_AUDIO_WRITE trim_fake
#define WP_AUDIO_TRIM trim_fake
#define WP_OUTPUT capture
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    core_config.audio=core_config.amplifier=1;core_audio_owner=-1;
    core_command("v1 1 audio level 80");assert(!writes);
    core_audio_owner=999;core_command("v1 2 audio level 80");assert(writes==1&&level==80);
    core_command("v1 2 audio level 40");assert(writes==1);
    const char *bad[]={"v1 3 audio level 101","v1 3 audio level -1","v1 03 audio level 40","v1 3 audio level 40;reboot","v1 3 audio power 2","v1 3 audio speakers 9","v1 3 audio file 0"};
    for(size_t i=0;i<sizeof(bad)/sizeof(bad[0]);i++)core_command(bad[i]);assert(writes==1);
    core_command("v1 4 audio power 0");core_command("v1 5 audio power 1");assert(core_audio_pending[0]==1);
    core_command("v1 6 audio speakers 1");assert(core_audio_pending[1]==1);
    core_command("v1 7 audiomodules 0 0");assert(!core_config.audio&&!core_config.amplifier&&core_audio_pending[0]<0&&core_audio_pending[1]<0);
    core_command("v1 8 audio level 20");assert(writes==1);
    core_command("v1 9 audiomodules 0 1");core_command("v1 10 audio level 20");assert(writes==2&&level==20&&!core_config.audio);
    core_command("v1 11 audio speakers 1");assert(core_audio_pending[1]<0);
    puts("PASS: amplifier-only operation, audio/amp isolation, ownership gate, trim range, command dedup/injection guards, latest power target and pending-job cancellation. Simulated board only.");return 0;
}
