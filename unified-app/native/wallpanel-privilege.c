/* App-specific privilege bootstrap. No MQTT, shell API, hardware I/O or fallback.
 * Authenticated local socket; only fixed worker/capture executables are allowed. */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <time.h>
#ifndef WP_SETUP_SHELL
#define WP_SETUP_SHELL "/system/bin/sh"
#endif
#ifndef WP_UI_PROCESS
#define WP_UI_PROCESS "/system/bin/app_process"
#endif
static long long now_ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
static volatile sig_atomic_t alive=1;
static void stop(int sig){(void)sig;alive=0;}
static int copy_config(const char *source,const char *destination,uid_t uid){
    int in=open(source,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(in<0)return -1;struct stat st;
    if(fstat(in,&st)||!S_ISREG(st.st_mode)||st.st_uid!=uid||(st.st_mode&0077)||st.st_size<1||st.st_size>8192){close(in);return -1;}
    int out=open(destination,O_WRONLY|O_CREAT|O_CLOEXEC|O_NOFOLLOW,0600),ok=0;
    if(out>=0&&!fstat(out,&st)&&S_ISREG(st.st_mode)&&st.st_uid==geteuid()&&!(st.st_mode&0077)&&!ftruncate(out,0)){
        char data[8193];ssize_t n=read(in,data,sizeof(data));if(n>0&&n<=8192&&read(in,data+n,1)==0&&write(out,data,n)==n)ok=1;
    }
    close(in);if(out>=0)close(out);return ok?0:-1;
}
static int request(int fd,char *line,size_t size){
    for(size_t n=0;n+1<size;n++){
        struct pollfd p={.fd=fd,.events=POLLIN};if(poll(&p,1,1000)<=0)return -1;
        if(read(fd,line+n,1)!=1)return -1;if(line[n]=='\n'){line[n]=0;return 0;}
    }return -1;
}
static void error_reply(int fd){const char *s="{\"v\":1,\"type\":\"error\",\"error\":\"privilege_request_rejected\"}\n";ssize_t done=write(fd,s,strlen(s));(void)done;}
static pid_t launch(int fd,const char *binary,const char *arg,const char *value,int listener,const char *classpath){
    pid_t parent=getpid(),pid=fork();if(!pid){
        prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(1);setpgid(0,0);close(listener);dup2(fd,0);dup2(fd,1);if(fd>2)close(fd);
        int log=open("/dev/null",O_WRONLY);if(log>=0){dup2(log,2);if(log>2)close(log);}
        if(classpath&&setenv("CLASSPATH",classpath,1))_exit(127);
        signal(SIGTERM,SIG_DFL);signal(SIGINT,SIG_DFL);execl(binary,binary,arg,value,(char*)NULL);_exit(127);
    }return pid;
}
int main(int argc,char **argv){
    if(argc==2&&!strcmp(argv[1],"--describe")){puts("{\"protocol\":3,\"setup\":true,\"ui_guard\":true,\"local_socket\":true,\"peer_uid_required\":true,\"hardware\":false,\"shell_api\":false,\"network\":false}");return 0;}
    if(argc!=4)return 2;char *end;long parsed=strtol(argv[1],&end,10);if(*end||parsed<1||parsed>1000000)return 2;uid_t uid=(uid_t)parsed;
#ifndef WP_TESTING
    if(geteuid()!=0)return 3;
#endif
    const char *folder=argv[3];char sockpath[108],lockpath[512],pidpath[512],core[512],capture[512],config[512],operation[512],pending[512],ui_classpath[512];
    if(snprintf(sockpath,sizeof(sockpath),"%s/control.sock",folder)>=(int)sizeof(sockpath))return 2;
    snprintf(lockpath,sizeof(lockpath),"%s/privilege.lock",folder);snprintf(pidpath,sizeof(pidpath),"%s/privilege.pid",folder);
    snprintf(core,sizeof(core),"%s/wallpanel-core",folder);snprintf(capture,sizeof(capture),"%s/wallpanel-capture",folder);snprintf(config,sizeof(config),"%s/worker.conf",folder);
    snprintf(operation,sizeof(operation),"%s/setup/operation.lock",folder);
    snprintf(pending,sizeof(pending),"%s/setup/apply-active",folder);
    snprintf(ui_classpath,sizeof(ui_classpath),"%s/wallpanel-system-ui.jar",folder);
    int lock=open(lockpath,O_CREAT|O_RDWR|O_CLOEXEC|O_NOFOLLOW,0600);if(lock<0||flock(lock,LOCK_EX|LOCK_NB))return 4;
    int server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(server<0)return 5;struct sockaddr_un address={.sun_family=AF_UNIX};strcpy(address.sun_path,sockpath);
    uid_t socket_uid=uid;
#ifdef WP_TESTING
    socket_uid=geteuid();
#endif
    unlink(sockpath);if(bind(server,(struct sockaddr*)&address,sizeof(address))||chmod(sockpath,0600)||chown(sockpath,socket_uid,(gid_t)-1)||listen(server,4))return 5;
    FILE *pidfile=fopen(pidpath,"w");if(pidfile){fprintf(pidfile,"%d\n",getpid());fclose(pidfile);chmod(pidpath,0600);}
    signal(SIGPIPE,SIG_IGN);signal(SIGTERM,stop);signal(SIGINT,stop);pid_t worker=-1,shot=-1,setup=-1,ui=-1;long long setup_at=0;
    while(alive){
        int status;pid_t got;while((got=waitpid(-1,&status,WNOHANG))>0){if(got==worker){worker=-1;if(shot>0)kill(shot,SIGTERM);}if(got==shot)shot=-1;if(got==setup)setup=-1;if(got==ui)ui=-1;}
        if(setup>0&&now_ms()-setup_at>150000){kill(-setup,SIGKILL);kill(setup,SIGKILL);}
        struct pollfd p={.fd=server,.events=POLLIN};if(poll(&p,1,200)<=0)continue;
        int fd=accept4(server,NULL,NULL,SOCK_CLOEXEC);if(fd<0)continue;struct ucred peer;socklen_t length=sizeof(peer);char line[96];
        if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&length)||peer.uid!=uid||request(fd,line,sizeof(line))){close(fd);continue;}
        if(!strcmp(line,"WORKER")&&worker<0&&setup<0&&access(operation,F_OK)!=0&&access(pending,F_OK)!=0&&!copy_config(argv[2],config,uid))worker=launch(fd,core,"--run-leased",config,server,NULL);
        else if(!strcmp(line,"UI_GUARD")&&ui<0)ui=launch(fd,WP_UI_PROCESS,"/system/bin","local.wallpanel.system.SystemUiLease",server,ui_classpath);
        else if(!strcmp(line,"SETUP CHECK")||!strcmp(line,"SETUP APPLY")||!strcmp(line,"SETUP RESTORE")){
            int checking=!strcmp(line,"SETUP CHECK");
            if(setup<0&&(checking||(worker<0&&shot<0))){char script[512];snprintf(script,sizeof(script),"%s/setup-panel.sh",folder);setup=launch(fd,WP_SETUP_SHELL,script,checking?"check":!strcmp(line,"SETUP APPLY")?"apply":"restore",server,NULL);setup_at=now_ms();}
            else error_reply(fd);
        }
        else {
            long owner=0;int n=0;char canonical[64];
            if(sscanf(line,"CAPTURE %ld%n",&owner,&n)==1&&line[n]==0){snprintf(canonical,sizeof(canonical),"CAPTURE %ld",owner);}
            else canonical[0]=0;
            if(canonical[0]&&!strcmp(canonical,line)&&worker>0&&owner==worker&&shot<0){char value[32];snprintf(value,sizeof(value),"%ld",owner);shot=launch(fd,capture,value,NULL,server,NULL);}
            else error_reply(fd);
        }
        close(fd);
    }
    close(server);if(worker>0)kill(worker,SIGTERM);if(shot>0)kill(shot,SIGTERM);if(ui>0)kill(ui,SIGTERM);if(setup>0){kill(-setup,SIGTERM);kill(setup,SIGTERM);}
    for(int i=0;i<100;i++){pid_t got=waitpid(-1,NULL,WNOHANG);if(got==worker)worker=-1;if(got==shot)shot=-1;if(got==setup)setup=-1;if(got==ui)ui=-1;if(got<0&&errno==ECHILD)break;usleep(10000);}
    if(worker>0)kill(worker,SIGKILL);if(shot>0)kill(shot,SIGKILL);if(ui>0)kill(ui,SIGKILL);if(setup>0){kill(-setup,SIGKILL);kill(setup,SIGKILL);}unlink(sockpath);unlink(pidpath);close(lock);return 0;
}
