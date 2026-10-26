/* Actual legacy yield function plus OS locks and private descriptor fixtures. */
#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#define WP_LEASE_UID geteuid()
#define WP_KNOB_REQUEST "/tmp/wallpanel-knob-test-request.lock"
#define WP_KNOB_RESOURCE "/tmp/wallpanel-knob-test-resource.lock"
#define WALLPANEL_DIRECT_MCU 1
#define main bridge_main
#include "../panel-172.16.1.26/panel-mqtt.c"
#undef main
int main(void){
    unlink(WP_KNOB_REQUEST);unlink(WP_KNOB_RESOURCE);
    int input[2],sampler[2],frame[2];assert(!pipe(input));assert(!pipe(sampler));assert(!pipe(frame));
    knob_owner_fd=wp_lock_acquire(WP_KNOB_RESOURCE);assert(knob_owner_fd>=0);
    knob_external=0;knob_ready=1;knob_fb=frame[0];rotary_fd=sampler[0];
    struct pollfd fds[5]={{.fd=input[0]}};
    int request=wp_lock_acquire(WP_KNOB_REQUEST);assert(request>=0);
    assert(wp_lock_acquire(WP_KNOB_RESOURCE)<0); // Request alone never grants resources.
    gesture_pulse_at(0,KEY_FN_F3,ms());gesture_pulse_at(1,1,ms());
    knob_owner_tick(fds);
    assert(knob_external&&knob_owner_fd<0&&knob_fb<0&&!knob_ready&&rotary_fd<0&&fds[0].fd<0);
    assert(fcntl(input[0],F_GETFD)<0&&fcntl(frame[0],F_GETFD)<0&&fcntl(sampler[0],F_GETFD)<0);
    assert(!gesture_pending[0].deadline&&gesture_pending[1].deadline); // MCU buttons remain independent.
    int app=wp_lock_acquire(WP_KNOB_RESOURCE);assert(app>=0);
    int boot[2];assert(!pipe(boot));fds[0].fd=boot[0]; // Legacy service restarted while app already owns resources.
    knob_owner_tick(fds);assert(fds[0].fd<0&&fcntl(boot[0],F_GETFD)<0);close(boot[1]);
    close(request);assert(!wp_lock_held(WP_KNOB_REQUEST));
    assert(wp_lock_acquire(WP_KNOB_RESOURCE)<0); // Reclaim waits until resource cleanup, too.
    close(app);int previous=wp_lock_acquire(WP_KNOB_RESOURCE);assert(previous>=0);close(previous);
    close(input[1]);close(sampler[1]);close(frame[1]);unlink(WP_KNOB_REQUEST);unlink(WP_KNOB_RESOURCE);
    puts("PASS: legacy knob yield closes input/framebuffer/sampler before resource transfer, cancels only knob gestures, and resource lock prevents premature reclaim.");return 0;
}
