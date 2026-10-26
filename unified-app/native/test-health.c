#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#define WP_HEALTH_PATH "/tmp/wallpanel-test-app-health"
static int output(const char *s){(void)s;return 0;}
#define WP_OUTPUT output
#define main worker_main
#include "wallpanel-core.c"
#undef main
int main(void){
    unlink(WP_HEALTH_PATH);unlink(WP_HEALTH_PATH ".new");core_status(1);assert(access(WP_HEALTH_PATH,F_OK));
    core_leased=1;core_status(2);struct stat st;assert(!stat(WP_HEALTH_PATH,&st)&&!(st.st_mode&0077));
    FILE *f=fopen(WP_HEALTH_PATH,"r");assert(f);char text[2048];assert(fgets(text,sizeof(text),f));fclose(f);assert(strstr(text,"\"worker_pid\":")&&strstr(text,"\"request_id\":2"));
    assert(!symlink(WP_HEALTH_PATH,WP_HEALTH_PATH ".new"));core_status(3);f=fopen(WP_HEALTH_PATH,"r");assert(f&&fgets(text,sizeof(text),f));fclose(f);assert(strstr(text,"\"request_id\":2"));
    unlink(WP_HEALTH_PATH ".new");unlink(WP_HEALTH_PATH);puts("PASS: inactive mode has no health writes; active snapshot is private, atomic, credential-free and rejects symlinks. Temporary fixture only.");
}
