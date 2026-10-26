/* Cooperative UART ownership: OS releases the app's exclusive lock on death. */
#ifndef WALLPANEL_MCU_LEASE_H
#define WALLPANEL_MCU_LEASE_H
#include <errno.h>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>
#ifndef WP_LEASE_PATH
#define WP_LEASE_PATH "/data/local/panalume/unified-mcu.lock"
#endif
#ifndef WP_LEASE_UID
#define WP_LEASE_UID 0
#endif
static int wp_lock_fd(const char *path,int create){
    int fd=open(path,O_RDWR|O_CLOEXEC|O_NOFOLLOW|(create?O_CREAT:0),0600);if(fd<0)return -1;
    struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=WP_LEASE_UID||(st.st_mode&0077)){close(fd);errno=EPERM;return -1;}return fd;
}
static int wp_lock_held(const char *path){
    int fd=wp_lock_fd(path,0);if(fd<0)return 0;
    int result=flock(fd,LOCK_EX|LOCK_NB),error=errno;
    if(!result)flock(fd,LOCK_UN);close(fd);
    return result<0&&(error==EWOULDBLOCK||error==EAGAIN);
}
static int wp_lock_acquire(const char *path){
    int fd=wp_lock_fd(path,1);if(fd<0)return -1;
    if(flock(fd,LOCK_EX|LOCK_NB)){int error=errno;close(fd);errno=error;return -1;}return fd;
}
static int wp_lease_fd(int create){return wp_lock_fd(WP_LEASE_PATH,create);}
static int wp_lease_held(void){return wp_lock_held(WP_LEASE_PATH);}
static int wp_lease_acquire(void){return wp_lock_acquire(WP_LEASE_PATH);}
#ifndef WP_KNOB_REQUEST
#define WP_KNOB_REQUEST "/data/local/panalume/unified-knob-request.lock"
#define WP_KNOB_RESOURCE "/data/local/panalume/unified-knob-resource.lock"
#endif
#ifndef WP_AUDIO_REQUEST
#define WP_AUDIO_REQUEST "/data/local/panalume/unified-audio-request.lock"
#define WP_AUDIO_RESOURCE "/data/local/panalume/unified-audio-resource.lock"
#endif
#ifndef WP_DISPLAY_REQUEST
#define WP_DISPLAY_REQUEST "/data/local/panalume/unified-display-request.lock"
#define WP_DISPLAY_RESOURCE "/data/local/panalume/backlight.lock"
#endif
#ifndef WP_MANAGEMENT_REQUEST
#define WP_MANAGEMENT_REQUEST "/data/local/panalume/unified-management-request.lock"
#define WP_MANAGEMENT_RESOURCE "/data/local/panalume/unified-management-resource.lock"
#endif
#endif
