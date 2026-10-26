/* Private pipes only: never opens GPIO, framebuffers or panel state files. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <string.h>
static char output[32768];static size_t used;
static int capture(const char *line){size_t n=strlen(line);assert(used+n<sizeof(output));memcpy(output+used,line,n+1);used+=n;return 0;}
#define WP_OUTPUT capture
#define main worker_main
#include "wallpanel-core.c"
#undef main
static void clear(void){used=0;output[0]=0;}
static void key(int fd,int code){struct input_event e={.type=EV_KEY,.code=(unsigned short)code,.value=1};assert(write(fd,&e,sizeof(e))==sizeof(e));}
int main(void){
    int input[2],sampler[2];assert(!pipe2(input,O_NONBLOCK));assert(!pipe2(sampler,O_NONBLOCK));
    core_config.knob_input=1;rotary_fd=sampler[0];
    for(int i=0;i<4;i++){
        clear();key(input[1],KEY_FN_F2);assert(!process_input(input[0],0,1));assert(!used); // Suppress vendor duplicate.
        struct rotary_step step={.at=ms(),.direction=1};assert(write(sampler[1],&step,sizeof(step))==sizeof(step));
        assert(!rotary_receive(1));assert(strstr(output,"\"event\":\"rotate_right\""));
        assert(!strstr(strchr(output,'\n')+1,"\"type\":\"knob\""));
    }
    clear();struct rotary_step left={.at=ms(),.direction=-1};assert(write(sampler[1],&left,sizeof(left))==sizeof(left));assert(!rotary_receive(1));assert(strstr(output,"rotate_left"));
    clear();left.at=ms()-2000;assert(write(sampler[1],&left,sizeof(left))==sizeof(left));assert(!rotary_receive(1));assert(!used);
    key(input[1],KEY_FN_F3);assert(!process_input(input[0],0,1));assert(!used);gestures_tick_at(ms()+401);assert(strstr(output,"\"event\":\"press\""));
    clear();key(input[1],KEY_FN_F3);key(input[1],KEY_FN_F3);assert(!process_input(input[0],0,1));assert(strstr(output,"double_press"));
    clear();key(input[1],KEY_FN_F8);assert(!process_input(input[0],0,1));assert(strstr(output,"long_press"));
    clear();core_config.knob_input=0;gesture_publish(0,"rotate_right",KEY_FN_F2);assert(!used);
    assert(core_knob_payload("frame","Clock\n12:34\nMon"));assert(!core_knob_payload("frame","bad\nextra\nlines\nno"));
    assert(!core_knob_payload("frame","Title\n12345678901234567\nDetail"));assert(!core_knob_payload("title","bad\xc0\xaf"));
    assert(core_knob_payload("led","Red")&&!core_knob_payload("led","OFF"));assert(!core_knob_payload("power","toggle"));
    core_config.knob_display=1;knob_ready=1;knob_dirty=0; // frame command only changes RAM; it does not render or save.
    core_command("v1 1 knob frame 436c6f636b0a31323a33340a4d6f6e");assert(!strcmp(knob_text[0],"Clock")&&!strcmp(knob_text[1],"12:34"));
    core_command("v1 1 knob frame 580a590a5a");assert(!strcmp(knob_text[0],"Clock"));
    core_command("v1 2 knob frame 580059");assert(!strcmp(knob_text[0],"Clock"));
    core_command("v1 3 knob frame 580a590a5a;reboot");assert(!strcmp(knob_text[0],"Clock"));
    assert(core_config.knob_display);core_command("v1 4 knobmodules 0 0");assert(!core_config.knob_display&&!knob_ready);
    close(input[0]);close(input[1]);close(sampler[1]);rotary_cleanup();
    puts("PASS: shared knob input, one event per physical step, CW/CCW direction, vendor duplicate/stale-step rejection, single/double/long press, disabled input/display, validated RAM frames, native dedup and no shell/NUL injection.");return 0;
}
