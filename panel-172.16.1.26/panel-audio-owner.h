#ifdef WALLPANEL_DIRECT_MCU
static int audio_owner_fd=-1;
static pid_t audio_retire=-1;
static long long audio_retire_at;
#ifndef WP_AUDIO_RETIRE
static pid_t audio_retire_old(void){
    pid_t parent=getpid();pid_t pid=fork();if(!pid){
        prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(1);
        int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}
        execl("/system/bin/am","am","force-stop","local.wallpanel.audio",(char*)NULL);_exit(127);
    }return pid;
}
#define WP_AUDIO_RETIRE audio_retire_old
#endif
static void audio_owner_tick(void){
    if(wp_lock_held(WP_AUDIO_REQUEST)){
        if(!audio_external){audio_external=1;audio_cleanup();audio_server=-1;}
        /* Close an old server inherited from startup before granting resources. */
        if(audio_server>=0){audio_cleanup();audio_server=-1;}
        if(audio_owner_fd<0)return;
        if(audio_retire<0){
            audio_retire=WP_AUDIO_RETIRE();audio_retire_at=ms();
        }
        if(audio_retire>0){
            int result;pid_t done=waitpid(audio_retire,&result,WNOHANG);
            if(done==audio_retire){audio_retire=-1;
                if(WIFEXITED(result)&&WEXITSTATUS(result)==0){
                    /* Current hardware AUX bypass uses no PCM worker. Refuse a
                     * transfer until all earlier root audio jobs have exited. */
                    pid_t *children[]={&audio_worker,&audio_off,&audio_starter,&audio_speaker_saver};int busy=0;
                    for(int i=0;i<4;i++)if(*children[i]>0){
                        pid_t pid=*children[i];errno=0;pid_t got=waitpid(pid,NULL,WNOHANG);if(got==pid||(got<0&&errno==ECHILD))*children[i]=-1;
                        else{kill(pid,SIGTERM);busy=1;}
                    }
                    if(!busy){close(audio_owner_fd);audio_owner_fd=-1;}
                }
            }else if(ms()-audio_retire_at>10000)kill(audio_retire,SIGKILL);
        }
        return;
    }
    if(audio_retire>0){kill(audio_retire,SIGTERM);if(waitpid(audio_retire,NULL,WNOHANG)==audio_retire)audio_retire=-1;else return;}
    if(audio_owner_fd<0){audio_owner_fd=wp_lock_acquire(WP_AUDIO_RESOURCE);if(audio_owner_fd<0)return;}
    if(audio_external){
        audio_external=0;audio_available_last=-1;audio_sent[0]=0;audio_hardware_last=-1;audio_amp_available_last=-1;
        audio_start_at=0;audio_power_last=-1;audio_init();
    }
}
#else
static void audio_owner_tick(void){}
#endif
