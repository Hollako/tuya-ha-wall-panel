#include <unistd.h>
#include <grp.h>
#include <sys/prctl.h>
#include <stdlib.h>
static void wpar_drop_privileges(void){
 const char *v=getenv("WPAR_APP_UID");char *end;if(!v)_exit(120);
 unsigned long uid=strtoul(v,&end,10);if(*end||uid<10000||uid>19999||geteuid()!=0)_exit(121);
 pid_t parent=getppid();gid_t inet=3003;
 if(setgroups(1,&inet)||setresgid(uid,uid,uid)||setresuid(uid,uid,uid)||prctl(PR_SET_NO_NEW_PRIVS,1,0,0,0)||prctl(PR_SET_PDEATHSIG,9)||getppid()!=parent)_exit(122);
}
