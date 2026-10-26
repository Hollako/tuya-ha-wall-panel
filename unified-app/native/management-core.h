/* Only an explicit fresh request can schedule reboot; no boot/reconnect replay. */
static int core_management_request=-1,core_management_owner=-1;
static pid_t core_reboot_pid=-1;
static long long core_reboot_at,core_reboot_started;
static char core_management_sent[320],core_management_error[80];
static int core_management_owned(void){return core_management_owner>=0;}
#ifndef WP_REBOOT_SPAWN
static pid_t core_reboot_spawn(void){
    pid_t parent=getpid(),pid=fork();if(!pid){
        prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(1);setpgid(0,0);
        int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}
        execl("/system/bin/sh","sh","/system/bin/svc","power","reboot",(char*)NULL);_exit(127);
    }return pid;
}
#define WP_REBOOT_SPAWN core_reboot_spawn
#endif
static void core_management_close(void){
    core_reboot_at=0;
    if(core_reboot_pid>0){kill(-core_reboot_pid,SIGKILL);kill(core_reboot_pid,SIGKILL);while(waitpid(core_reboot_pid,NULL,0)<0&&errno==EINTR){}core_reboot_pid=-1;}
    if(core_management_owner>=0){close(core_management_owner);core_management_owner=-1;}
    if(core_management_request>=0){close(core_management_request);core_management_request=-1;}
}
static void core_management_tick(long long now){
    if(core_management_request<0){core_management_request=wp_lock_acquire(WP_MANAGEMENT_REQUEST);if(core_management_request<0)return;}
    if(core_management_owner<0){core_management_owner=wp_lock_acquire(WP_MANAGEMENT_RESOURCE);if(core_management_owner<0)return;core_status(0);}
    if(core_reboot_at&&now>=core_reboot_at&&core_reboot_pid<0){
        core_reboot_at=0;core_reboot_started=now;core_reboot_pid=WP_REBOOT_SPAWN();if(core_reboot_pid<0)strcpy(core_management_error,"Could not start graceful reboot");
    }
    if(core_reboot_pid>0){
        int status;pid_t got=waitpid(core_reboot_pid,&status,WNOHANG);
        if(got==core_reboot_pid){core_reboot_pid=-1;if(!WIFEXITED(status)||WEXITSTATUS(status))strcpy(core_management_error,"Android rejected graceful reboot");}
        else if(now-core_reboot_started>10000){kill(-core_reboot_pid,SIGKILL);kill(core_reboot_pid,SIGKILL);strcpy(core_management_error,"Graceful reboot timed out");}
    }
    int actual=bl_read_actual(),normal=dp_integer(DP_STATE"/display-brightness",1,255);if(normal<1)normal=120;if(actual<0)actual=0;
    char json[320];snprintf(json,sizeof(json),"{\"v\":1,\"type\":\"management_state\",\"reboot_pending\":%s,\"brightness\":%d,\"actual_brightness\":%d,\"display_on\":%s,\"error\":\"%s\"}\n",core_reboot_at||core_reboot_pid>0?"true":"false",normal,actual,actual>0?"true":"false",core_management_error);
    if(strcmp(json,core_management_sent)){WP_OUTPUT(json);strcpy(core_management_sent,json);}
}
static int core_management_command(int reboot,long long now){
    if(!core_management_owned())return -1;
    if(reboot&&!core_reboot_at&&core_reboot_pid<0){core_reboot_at=now+1500;core_management_error[0]=0;}
    core_management_sent[0]=0;return 0;
}
