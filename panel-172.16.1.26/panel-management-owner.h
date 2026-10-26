#ifdef WALLPANEL_DIRECT_MCU
static int device_owner_fd=-1;
static long long device_yield_at;
static void device_owner_tick(void){
    if(wp_lock_held(WP_MANAGEMENT_REQUEST)){
        if(!device_external){device_external=1;device_reboot_at=0;device_yield_at=ms();}
        pid_t *jobs[]={&device_capture,&device_rebooter};int busy=0;
        for(int i=0;i<2;i++)if(*jobs[i]>0){
            pid_t pid=*jobs[i];kill(-pid,ms()-device_yield_at>3000?SIGKILL:SIGTERM);kill(pid,ms()-device_yield_at>3000?SIGKILL:SIGTERM);
            errno=0;pid_t got=waitpid(pid,NULL,WNOHANG);if(got==pid||(got<0&&errno==ECHILD))*jobs[i]=-1;else busy=1;
        }
        if(!busy&&device_owner_fd>=0){close(device_owner_fd);device_owner_fd=-1;}
        return;
    }
    if(device_owner_fd<0){device_owner_fd=wp_lock_acquire(WP_MANAGEMENT_RESOURCE);if(device_owner_fd<0)return;}
    if(device_external){device_external=0;device_reboot_at=0;device_report=1;device_sent[0]=0;}
}
#else
static void device_owner_tick(void){}
#endif
