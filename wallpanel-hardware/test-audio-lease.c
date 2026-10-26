#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <unistd.h>
#define WP_LEASE_UID geteuid()
#define WP_AUDIO_REQUEST "/tmp/wallpanel-audio-test-request"
#define WP_AUDIO_RESOURCE "/tmp/wallpanel-audio-test-resource"
#define WALLPANEL_DIRECT_MCU 1
static int retire_fail;
static pid_t retire_fake(void){pid_t pid=fork();if(!pid)_exit(retire_fail?1:0);return pid;}
#define WP_AUDIO_RETIRE retire_fake
#define main legacy_main
#include "../panel-172.16.1.26/panel-mqtt.c"
#undef main
int main(void){
    unlink(WP_AUDIO_REQUEST);unlink(WP_AUDIO_RESOURCE);
    int pair[2];assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair));
    audio_server=pair[0];audio_external=0;audio_owner_fd=wp_lock_acquire(WP_AUDIO_RESOURCE);assert(audio_owner_fd>=0);
    int request=wp_lock_acquire(WP_AUDIO_REQUEST);assert(request>=0);assert(wp_lock_acquire(WP_AUDIO_RESOURCE)<0);
    retire_fail=1;audio_owner_tick();
    for(int i=0;i<100&&audio_retire>0;i++){usleep(1000);audio_owner_tick();}
    assert(audio_external&&audio_server<0&&fcntl(pair[0],F_GETFD)<0);assert(audio_owner_fd>=0);assert(wp_lock_acquire(WP_AUDIO_RESOURCE)<0);
    retire_fail=0;
    for(int i=0;i<200&&audio_owner_fd>=0;i++){usleep(1000);audio_owner_tick();}
    assert(audio_owner_fd<0);int app=wp_lock_acquire(WP_AUDIO_RESOURCE);assert(app>=0);
    audio_owner_tick();assert(audio_server<0&&audio_external); // Restart/yield state does not launch old service.
    close(request);assert(wp_lock_acquire(WP_AUDIO_RESOURCE)<0);close(app);
    int fallback=wp_lock_acquire(WP_AUDIO_RESOURCE);assert(fallback>=0);close(fallback);close(pair[1]);
    unlink(WP_AUDIO_REQUEST);unlink(WP_AUDIO_RESOURCE);
    puts("PASS: audio IPC closes before retirement; failed retirement cannot transfer; successful retirement releases resources; fallback waits for full app cleanup. No audio hardware accessed.");return 0;
}
