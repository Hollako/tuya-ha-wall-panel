/* Fixed root audio operations. Board AUX/horn controls use the Android engine;
 * no recorder, PCM loopback, socket listener or separate audio APK is started. */
#include <sys/prctl.h>
static int core_audio_owner=-1,core_audio_started,core_audio_target=-1,core_audio_level_last=-1;
static long long core_audio_check;
static pid_t core_audio_jobs[2]={-1,-1};
static int core_audio_pending[2]={-1,-1};
static long long core_audio_job_at[2];
static int core_audio_owned(void){return core_audio_owner>=0;}
static void core_audio_result(int kind,int ok){
    char b[160];snprintf(b,sizeof(b),"{\"v\":1,\"type\":\"audio_result\",\"command\":\"%s\",\"ok\":%s}\n",kind?"speaker_saved":"route",ok?"true":"false");WP_OUTPUT(b);
}
static int core_audio_raw(void){
    FILE *f=fopen("/sys/class/rk817_codec/rk817_spk_volume","r");if(!f)return -1;
    char b[128];int value=-1;if(fgets(b,sizeof(b),f)){char *p=strchr(b,'[');if(p)value=atoi(p+1);}fclose(f);
    return value>=3&&value<=80?value:-1;
}
static int core_audio_write(int value){
    int fd=open("/sys/class/rk817_codec/rk817_spk_volume",O_WRONLY|O_CLOEXEC);if(fd<0)return -1;
    char b[16];int n=snprintf(b,sizeof(b),"%d\n",80-(77*value+50)/100);int ok=write(fd,b,n)==n;close(fd);return ok?0:-1;
}
static int core_audio_trim(int value){
    const char *path=STATE"/audio-output-trim.app-new";int fd=open(path,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);if(fd<0)return -1;
    struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=0||(st.st_mode&0077)){close(fd);return -1;}
    char b[16];int n=snprintf(b,sizeof(b),"%d\n",value);int ok=write(fd,b,n)==n&&!fsync(fd);if(close(fd))ok=0;
    if(!ok||rename(path,STATE"/audio-output-trim")){unlink(path);return -1;}
    core_audio_target=value;return core_audio_write(value);
}
#ifndef WP_AUDIO_RAW
#define WP_AUDIO_RAW core_audio_raw
#define WP_AUDIO_WRITE core_audio_write
#define WP_AUDIO_TRIM core_audio_trim
#endif
static void core_audio_close(void){
    for(int i=0;i<2;i++){
        core_audio_pending[i]=-1;
        if(core_audio_jobs[i]>0){kill(core_audio_jobs[i],SIGKILL);while(waitpid(core_audio_jobs[i],NULL,0)<0&&errno==EINTR){}core_audio_jobs[i]=-1;}
    }
    core_audio_started=0;core_audio_target=core_audio_level_last=-1;core_audio_check=0;
}
static void core_audio_tick(void){
    if(core_audio_owner<0){core_audio_owner=wp_lock_acquire(WP_AUDIO_RESOURCE);if(core_audio_owner<0)return;core_status(0);}
    if(!core_audio_started){
        core_audio_started=1;
        if(core_config.amplifier){FILE *f=fopen(STATE"/audio-output-trim","r");int v=-1;if(f){if(fscanf(f,"%d",&v)==1&&v>=0&&v<=100)core_audio_target=v;fclose(f);}}
    }
    long long now=ms();
    for(int i=0;i<2;i++){
        if(core_audio_jobs[i]>0){
            int status;pid_t done=waitpid(core_audio_jobs[i],&status,WNOHANG);
            if(done==core_audio_jobs[i]){core_audio_jobs[i]=-1;core_audio_result(i,WIFEXITED(status)&&WEXITSTATUS(status)==0);}
            else if(now-core_audio_job_at[i]>5000)kill(core_audio_jobs[i],SIGKILL);
        }
        if(core_audio_jobs[i]<0&&core_audio_pending[i]>=0){
            int value=core_audio_pending[i];core_audio_pending[i]=-1;pid_t parent=getpid();core_audio_jobs[i]=fork();
            if(!core_audio_jobs[i]){
                prctl(PR_SET_PDEATHSIG,SIGKILL);if(getppid()!=parent)_exit(1);
                int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}
                if(i)execl("/system/bin/settings","settings","put","system","com.sznaner.pulldownmenu.trumpet.state",value?"true":"false",(char*)NULL);
                else execl("/system/bin/tinymix","tinymix","Playback Path",value?"SPK":"OFF",(char*)NULL);
                _exit(127);
            }
            core_audio_job_at[i]=now;if(core_audio_jobs[i]<0)core_audio_result(i,0);
        }
    }
    if(core_config.amplifier&&now>=core_audio_check){
        core_audio_check=now+1000;int raw=WP_AUDIO_RAW();
        if(core_audio_target>=0&&raw!=80-(77*core_audio_target+50)/100){WP_AUDIO_WRITE(core_audio_target);raw=WP_AUDIO_RAW();}
        int level=raw<0?-1:((80-raw)*100+38)/77;
        if(level>=0&&level!=core_audio_level_last){char b[128];snprintf(b,sizeof(b),"{\"v\":1,\"type\":\"audio_level\",\"value\":%d}\n",level);WP_OUTPUT(b);core_audio_level_last=level;}
    }
}
static int core_audio_command(const char *field,int value){
    if(!core_audio_owned())return -1;
    if(!strcmp(field,"level"))return core_config.amplifier?WP_AUDIO_TRIM(value):-1;
    if(!strcmp(field,"power")&&core_config.amplifier){core_audio_pending[0]=value;return 0;}
    if(!strcmp(field,"speakers")&&core_config.audio){core_audio_pending[1]=value;return 0;}
    return -1;
}
