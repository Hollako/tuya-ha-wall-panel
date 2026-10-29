#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
static char artwork_path[256];
#define WP_KNOB_ART_PATH artwork_path
static int capture(const char *s){(void)s;return 0;}
#define WP_OUTPUT capture
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    char directory[]="/tmp/wp-art-XXXXXX";assert(mkdtemp(directory));snprintf(artwork_path,sizeof(artwork_path),"%s/art",directory);
    char id[65];memset(id,'a',64);id[64]=0;FILE *f=fopen(artwork_path,"wb");assert(f);assert(fwrite(id,1,64,f)==64);
    for(int i=0;i<6400;i++){unsigned char rgb[3]={20,100,240};assert(fwrite(rgb,1,3,f)==3);}fclose(f);
    core_config.knob_display=core_config.knob_input=1;knob_ready=1;knob_var.red.length=5;knob_var.red.offset=11;knob_var.green.length=6;knob_var.green.offset=5;knob_var.blue.length=5;
    long long deadline=core_knob_activity_until;
    assert(!core_knob_command("artwork",id));assert(core_knob_art_present);assert(core_knob_activity_until==deadline);
    assert(!core_knob_art_draw());assert(!core_knob_command("media_frame","Audio\n50%\nSong\nOFF"));assert(core_knob_art_draw());
    assert(knob_pixels[80*240+239-100]==knob_pack(0x1464f0,255));
    FILE *display=tmpfile();assert(display);knob_fb=fileno(display);saver_enabled=1;core_knob_activity_enabled=1;core_knob_activity_until=0;
    assert(!knob_render());assert(knob_pixels[80*240+239-100]!=knob_pack(0x1464f0,255));
    core_knob_activity_at(ms());assert(!knob_render());assert(knob_pixels[80*240+239-100]==knob_pack(0x1464f0,255));fclose(display);knob_fb=-1;
    assert(!core_knob_command("frame","Light\n50%\nBrightness"));assert(!core_knob_art_draw());
    assert(!core_knob_command("artwork","OFF"));assert(!core_knob_art_present);assert(core_knob_command("artwork","../file")==-1);
    id[0]='b';assert(core_knob_command("artwork",id)==-1);assert(!core_knob_art_present);
    f=fopen(artwork_path,"wb");assert(f);fputs("short",f);fclose(f);assert(core_knob_command("artwork",id)==-1);
    unlink(artwork_path);assert(!symlink("/etc/passwd",artwork_path));assert(core_knob_command("artwork",id)==-1);unlink(artwork_path);rmdir(directory);
    puts("PASS: bounded artwork file, identity, mirrored RGB, media-only display, no wake, clearing and malformed/symlink rejection. No hardware accessed.");
}
