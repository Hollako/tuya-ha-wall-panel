#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <sys/wait.h>
#include <time.h>
#define WP_LEASE_UID geteuid()
#define WP_MANAGEMENT_REQUEST "/tmp/wp-management-handover-request"
#define WP_MANAGEMENT_RESOURCE "/tmp/wp-management-handover-resource"
#include "mcu-lease.h"
static long long ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000LL+t.tv_nsec/1000000;}
static int device_external,device_report;
static pid_t device_capture=-1,device_rebooter=-1;
static long long device_reboot_at;
static char device_sent[64];
#define WALLPANEL_DIRECT_MCU
#include "../panel-172.16.1.26/panel-management-owner.h"
int main(void){
    unlink(WP_MANAGEMENT_REQUEST);unlink(WP_MANAGEMENT_RESOURCE);
    device_owner_tick();assert(!device_external&&device_owner_fd>=0);
    int request=wp_lock_acquire(WP_MANAGEMENT_REQUEST);assert(request>=0);
    int ready[2];assert(!pipe(ready));device_capture=fork();assert(device_capture>=0);
    if(!device_capture){close(ready[0]);signal(SIGTERM,SIG_IGN);assert(write(ready[1],"1",1)==1);for(;;)pause();}
    close(ready[1]);char c;assert(read(ready[0],&c,1)==1);close(ready[0]);
    device_reboot_at=ms()+5000;device_owner_tick();assert(device_external&&!device_reboot_at&&device_owner_fd>=0);
    assert(wp_lock_acquire(WP_MANAGEMENT_RESOURCE)<0);
    device_yield_at=ms()-3001;
    for(int i=0;i<100&&device_owner_fd>=0;i++){device_owner_tick();usleep(1000);}
    assert(device_owner_fd<0&&device_capture<0);
    int app=wp_lock_acquire(WP_MANAGEMENT_RESOURCE);assert(app>=0);close(request);
    device_owner_tick();assert(device_external&&device_owner_fd<0);
    close(app);device_owner_tick();assert(!device_external&&device_owner_fd>=0&&device_report&&!device_reboot_at);
    close(device_owner_fd);unlink(WP_MANAGEMENT_REQUEST);unlink(WP_MANAGEMENT_RESOURCE);
    puts("PASS: management handover cancels pending reboot/capture, waits for child cleanup, prevents overlapping owners, and resumes fallback without replay. Private locks/processes only.");
}
