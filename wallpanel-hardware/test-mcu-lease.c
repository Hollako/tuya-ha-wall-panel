#define _GNU_SOURCE
#undef NDEBUG
#include <assert.h>
#include <limits.h>
#include <stdlib.h>
#include <stdio.h>
#include <signal.h>
#include <sys/wait.h>
static char lease_path[PATH_MAX];
#define WP_LEASE_PATH lease_path
#define WP_LEASE_UID geteuid()
#include "mcu-lease.h"
int main(void){
    char directory[]="/tmp/wallpanel-lease-XXXXXX";assert(mkdtemp(directory));snprintf(lease_path,sizeof(lease_path),"%s/lock",directory);
    assert(!wp_lease_held());int fd=wp_lease_acquire();assert(fd>=0&&wp_lease_held());assert(wp_lease_acquire()<0);close(fd);assert(!wp_lease_held());
    int pipes[2];assert(!pipe(pipes));pid_t child=fork();assert(child>=0);
    if(!child){close(pipes[0]);int lock=wp_lease_acquire();assert(lock>=0);assert(write(pipes[1],"R",1)==1);pause();_exit(1);}
    close(pipes[1]);char ready;assert(read(pipes[0],&ready,1)==1);assert(wp_lease_held());kill(child,SIGKILL);waitpid(child,NULL,0);assert(!wp_lease_held());close(pipes[0]);
    assert(!chmod(lease_path,0644));assert(wp_lease_acquire()<0&&!wp_lease_held());unlink(lease_path);
    assert(!symlink("/dev/null",lease_path));assert(wp_lease_acquire()<0&&!wp_lease_held());unlink(lease_path);rmdir(directory);
    puts("PASS: exclusive lease, duplicate refusal, automatic release on process death, unsafe-mode/symlink refusal.");return 0;
}
