/* AirPlay-only authenticated PTP lease. No shell or caller-supplied paths. */
#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define BASE "/data/local/wpar-airplay"
static volatile sig_atomic_t alive=1;
static void finish(int s){(void)s;alive=0;}
static void terminate(pid_t child){
 if(child<=0)return;kill(child,SIGTERM);
 for(int i=0;i<100;i++){if(waitpid(child,NULL,WNOHANG)==child)return;usleep(10000);}
 kill(child,SIGKILL);waitpid(child,NULL,0);
}
int main(int argc,char **argv){
 if(argc==2&&!strcmp(argv[1],"--describe")){puts("wpar-ptp-helper v1: PTP lease only; SO_PEERCRED; no shell");return 0;}
 if(argc!=2||geteuid()!=0)return 2;char *end;long uid=strtol(argv[1],&end,10);if(*end||uid<10000||uid>19999)return 2;
 umask(077);int lock=open(BASE "/helper.lock",O_CREAT|O_RDWR|O_CLOEXEC|O_NOFOLLOW,0600);if(lock<0||flock(lock,LOCK_EX|LOCK_NB))return 3;
 int server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un a={.sun_family=AF_UNIX};strcpy(a.sun_path,BASE "/ptp.sock");unlink(a.sun_path);
 if(server<0||bind(server,(void*)&a,sizeof(a))||chown(a.sun_path,uid,uid)||chmod(a.sun_path,0600)||listen(server,4))return 4;
 signal(SIGPIPE,SIG_IGN);signal(SIGTERM,finish);signal(SIGINT,finish);
 pid_t active=-1;int lease=-1;
 while(alive){
  if(active>0){struct pollfd l={lease,POLLIN|POLLHUP,0};if(waitpid(active,NULL,WNOHANG)==active){active=-1;close(lease);lease=-1;}else if(poll(&l,1,0)>0){terminate(active);active=-1;close(lease);lease=-1;}}
  struct pollfd p={server,POLLIN,0};if(poll(&p,1,250)<=0)continue;
  int fd=accept4(server,NULL,NULL,SOCK_CLOEXEC);if(fd<0)continue;struct ucred peer;socklen_t len=sizeof(peer);
  if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&len)||peer.uid!=(uid_t)uid){close(fd);continue;}
  char req[16]={0};int n=0,waited=0;
  while(n<15&&alive&&waited<2000){struct pollfd q={fd,POLLIN,0};int r=poll(&q,1,100);waited+=100;if(r<=0)continue;if(read(fd,req+n,1)!=1)break;if(req[n++]=='\n')break;}
  if(!strcmp(req,"STATUS\n")){dprintf(fd,"OK PTP_HELPER 1 UID %ld\n",uid);close(fd);continue;}
  if(strcmp(req,"PTP\n")||active>0){close(fd);continue;}
  pid_t parent=getpid(),child=fork();
  if(!child){
   prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(127);close(server);close(fd);
   signal(SIGTERM,SIG_DFL);signal(SIGINT,SIG_DFL);
   int log=open(BASE "/nqptp.log",O_CREAT|O_WRONLY|O_APPEND|O_NOFOLLOW,0600);if(log<0)_exit(127);dup2(log,1);dup2(log,2);close(log);
   char u[48];snprintf(u,sizeof(u),"WPAR_APP_UID=%ld",uid);
   char *env[]={u,"WPAR_SHM_DIR=" BASE "/shm","PATH=/system/bin",NULL};char *args[]={BASE "/nqptp","-v",NULL};execve(args[0],args,env);_exit(127);
  }
  if(child<0){close(fd);continue;}dprintf(fd,"OK PTP_LEASE %d\n",child);
  active=child;lease=fd;
 }
 terminate(active);if(lease>=0)close(lease);
 unlink(a.sun_path);close(server);close(lock);return 0;
}
