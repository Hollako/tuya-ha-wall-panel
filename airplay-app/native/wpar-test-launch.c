#define _GNU_SOURCE
#include <unistd.h>
#include <grp.h>
#include <signal.h>
#include <stdlib.h>
int main(void){if(geteuid()!=0)return 2;gid_t g=3003;if(setgroups(1,&g)||setgid(10063)||setuid(10063))return 3;sigset_t m;sigemptyset(&m);sigprocmask(SIG_SETMASK,&m,0);for(int i=1;i<65;i++)if(i!=SIGKILL&&i!=SIGSTOP)signal(i,SIG_DFL);setenv("WPAR_SHM_DIR","/data/local/wpar-airplay/shm",1);execl("/data/local/tmp/wpar-airplay-stage/shairport-next","shairport-next","-v","-c","/data/local/tmp/wpar-airplay-stage/test.conf",(char*)0);return 4;}
