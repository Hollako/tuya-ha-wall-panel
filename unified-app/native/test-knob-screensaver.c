/* Host fixtures only; no real panel, framebuffer or outputs. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <stdio.h>
static char cache_path[256],ring_path[256];
#define WP_KNOB_RING_PATH ring_path
#define WP_KNOB_SAVER_PATH cache_path
static int capture(const char *s){(void)s;return 0;}
#define WP_OUTPUT capture
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    char directory[]="/tmp/wp-saver-XXXXXX";assert(mkdtemp(directory));
    snprintf(cache_path,sizeof(cache_path),"%s/cache",directory);
    snprintf(ring_path,sizeof(ring_path),"%s/ring",directory);
    core_config.knob_display=core_config.knob_input=1;knob_ready=1;
    assert(core_knob_activity_power(1000)&&!core_knob_activity_led(1000));
    assert(saver_idle(1000));core_knob_activity_at(2000);
    assert(!saver_idle(16999)&&saver_idle(17000));
    assert(core_knob_activity_power(17000)&&!core_knob_activity_led(17000));
    const char *config="10800\n24h\nsunny\n28°C";
    long long deadline=core_knob_activity_until;
    assert(!core_knob_command("screensaver",config));
    assert(core_knob_activity_until==deadline&&saver_offset==10800);
    assert(!saver_parse("10800\n24h\nUNKNOWN\n28",0));
    assert(!saver_parse("999999\n24h\nsunny\n28",0));
    assert(!saver_parse("10800\n24h\nsunny\n28\nextra",0));
    assert(!saver_parse("0\n13h\nsunny\n28",0));
    assert(!saver_parse("0junk\n24h\nsunny\n28",0));
    saver_offset=0;saver_enabled=0;saver_load();assert(saver_enabled&&saver_offset==10800);
    knob_var.red.length=5;knob_var.red.offset=11;knob_var.green.length=6;knob_var.green.offset=5;knob_var.blue.length=5;
    core_knob_activity_until=0;memset(knob_pixels,0,sizeof(knob_pixels));assert(core_knob_saver_draw());
    int lit=0;for(int i=0;i<240*240;i++)if(knob_pixels[i])lit++;assert(lit>500);
    /* All advertised conditions draw safely through mirrored/bounded pixels. */
    for(size_t i=0;i<sizeof(saver_conditions)/sizeof(*saver_conditions);i++){strcpy(saver_condition,saver_conditions[i]);saver_icon();}
    core_knob_activity_at(ms());assert(!core_knob_saver_draw());
    assert(!core_knob_command("screensaver","OFF"));assert(!saver_enabled);
    core_knob_activity_until=0;assert(!core_knob_activity_power(ms()));
    saver_enabled=1;saver_load();assert(!saver_enabled);
    core_knob_activity_enabled=0;knob_power=0;assert(!saver_idle(ms())&&!core_knob_activity_power(ms()));
    FILE *pixels=tmpfile();assert(pixels);knob_fb=fileno(pixels);
    core_knob_ring_load();assert(core_knob_ring);
    assert(!knob_render());assert(knob_pixels[120*240+5]!=0);
    assert(!core_knob_command("ring","OFF"));assert(!knob_render());
    assert(knob_pixels[120*240+5]==0); // Circle gone; control content still rendered.
    core_knob_ring=1;core_knob_ring_load();assert(!core_knob_ring);
    assert(core_knob_command("ring","toggle")==-1);
    assert(!core_knob_command("ring","ON"));assert(!knob_render());
    assert(knob_pixels[120*240+5]!=0);
    deadline=core_knob_activity_until;
    assert(!core_knob_command("media_frame","Audio\n40%\nMuted\nON"));assert(!knob_text[2][0]);
    assert(!knob_render());assert(knob_pixels[179*240+239-112]==knob_pack(0xff4040,255));
    assert(knob_pixels[156*240+239-112]==0); // Clear gap below the volume.
    assert(core_knob_activity_until==deadline);
    assert(core_knob_command("media_frame","Audio\n40%\nMuted\ninvalid")==-1);
    assert(!core_knob_command("frame","Light\n50%\nBrightness"));
    assert(!knob_render());assert(!core_knob_page_muted);
    core_knob_activity_enabled=1;saver_enabled=1;core_knob_activity_until=0;
    assert(!core_knob_command("speaker_mute","ON"));assert(!knob_render());
    assert(knob_pixels[154*240+239-178]==knob_pack(0xff4040,255));
    assert(!core_knob_activity_until); // Feedback must not wake/extend activity.
    assert(!core_knob_command("speaker_mute","OFF"));assert(!knob_render());
    assert(knob_pixels[154*240+239-178]==0);
    fclose(pixels);knob_fb=-1;
    unlink(ring_path);unlink(cache_path);rmdir(directory);
    puts("PASS: screensaver 15-second transition, control-page return, cached weather/offset, malformed-frame rejection, all icons, idle red LED and optional LCD-off policy. No hardware accessed.");
}
