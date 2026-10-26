#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <unistd.h>
#define WP_LEASE_UID geteuid()
#define WP_MANAGEMENT_REQUEST "/tmp/wallpanel-test-management-request"
#define WP_MANAGEMENT_RESOURCE "/tmp/wallpanel-test-management-resource"
static int launches;
static pid_t fake_reboot(void){launches++;pid_t pid=fork();if(!pid)_exit(0);return pid;}
#define WP_REBOOT_SPAWN fake_reboot
static int output(const char *line){(void)line;return 0;}
#define WP_OUTPUT output
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    unlink(WP_MANAGEMENT_REQUEST);unlink(WP_MANAGEMENT_RESOURCE);
    assert(core_management_command(1,100)==-1);assert(!core_reboot_at&&!launches);
    core_management_tick(100);assert(core_management_owned());assert(!core_reboot_at&&!launches);
    assert(!core_management_command(1,100));assert(core_reboot_at==1600);
    core_management_command(1,200);assert(core_reboot_at==1600);core_management_tick(1599);assert(!launches);
    core_management_tick(1600);assert(launches==1);usleep(20000);core_management_tick(1620);assert(core_reboot_pid<0);
    core_last_request=0;
    const char *bad[]={"v1 1 management reboot 1","v1 01 management reboot 0","v1 1 management reboot 0;id","v1 1 management unknown 0","v1 1 management reboot 0 "};
    for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)core_command(bad[i]);assert(!core_reboot_at&&launches==1);
    core_command("v1 2 management reboot 0");long long at=core_reboot_at;assert(at>0);
    core_command("v1 2 management reboot 0");assert(core_reboot_at==at);core_management_close();assert(!core_reboot_at&&!core_management_owned());
    core_management_tick(at+3000);assert(launches==1&&!core_reboot_at);core_management_close();
    assert(!wp_lock_held(WP_MANAGEMENT_REQUEST)&&!wp_lock_held(WP_MANAGEMENT_RESOURCE));unlink(WP_MANAGEMENT_REQUEST);unlink(WP_MANAGEMENT_RESOURCE);
    puts("PASS: management exclusive ownership, explicit delayed reboot, duplicates/injection rejected, pending cancellation, recovery without replay. Fake reboot only.");
}
