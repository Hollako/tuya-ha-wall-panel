/* Separate Android audio service; only its installed UID may connect. */
static int audio_server=-1,audio_client=-1,audio_uid=-1,audio_available_last=-1;
static pid_t audio_starter=-1,audio_worker=-1,audio_off=-1;
static int audio_worker_fd=-1,audio_wanted=0,audio_ready=0,audio_failed=0,audio_power_last=-1;
static int audio_gain=-1,audio_hardware_last=-1;
static int audio_amp_available_last=-1;
static int audio_hardware_target=-1;
static long long audio_hardware_at;
static pid_t audio_speaker_saver=-1;
static int audio_speaker_pending=-1;
static long long audio_speaker_started;
static int audio_stop_notify=0;
static long long audio_start_at,audio_rx_at,audio_worker_at,audio_stopping_at;
static char audio_lines[8192],audio_state[4096],audio_sent[4096],audio_worker_line[512];
static size_t audio_lines_len,audio_worker_len;
static void audio_line_stop(void){
    audio_ready=0;
    if(audio_worker>0&&!audio_stopping_at){kill(-audio_worker,SIGTERM);audio_stopping_at=ms();}
}
static void audio_disconnect(void){
    if(audio_client>=0)close(audio_client);audio_client=-1;audio_lines_len=0;
    audio_wanted=0;audio_line_stop();audio_state[0]=audio_sent[0]=0;
}
static int audio_send(const char *payload){
    if(audio_client<0)return -1;char line[4200];int n=snprintf(line,sizeof(line),"%s\n",payload);
    if(n<=0||(size_t)n>=sizeof(line))return -1;
    if(send(audio_client,line,n,MSG_NOSIGNAL|MSG_DONTWAIT)!=n){audio_disconnect();return -1;}return 0;
}
static void audio_gain_write(int gain){
    if(gain==audio_gain)return;
    char path[160];snprintf(path,sizeof(path),STATE"/audio-gain.new");
    int fd=open(path,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600);if(fd<0)return;
    char value[16];int n=snprintf(value,sizeof(value),"%d\n",gain);
    int ok=write(fd,value,n)==n;close(fd);if(ok&&!rename(path,STATE"/audio-gain"))audio_gain=gain;
}
static int audio_hardware_raw(void){
    FILE *f=fopen("/sys/class/rk817_codec/rk817_spk_volume","r");if(!f)return -1;
    char line[128];int raw=-1;if(fgets(line,sizeof(line),f)){char *start=strchr(line,'[');if(start)raw=atoi(start+1);}fclose(f);
    return raw>=3&&raw<=80?raw:-1;
}
static int audio_hardware_level(void){int raw=audio_hardware_raw();return raw<0?-1:((80-raw)*100+38)/77;}
static int audio_hardware_write(int value){
    int raw=80-(77*value+50)/100;
    int fd=open("/sys/class/rk817_codec/rk817_spk_volume",O_WRONLY|O_CLOEXEC);if(fd<0)return -1;
    char buffer[16];int n=snprintf(buffer,sizeof(buffer),"%d\n",raw);int ok=write(fd,buffer,n)==n;close(fd);return ok?0:-1;
}
static void audio_command(const char *topic,const char *payload,int retained){
#ifdef WALLPANEL_DIRECT_MCU
    if(audio_external||wp_lock_held(WP_AUDIO_REQUEST))return;
#endif
    if(retained||dry_run)return;
    if(!strcmp(topic,BASE"/audio/command/set")){
        size_t n=strlen(payload);if(n>4096||n<2||payload[0]!='{'||payload[n-1]!='}'||strchr(payload,'\n')||strchr(payload,'\r'))return;
        audio_send(payload);
    }else if(!strcmp(topic,BASE"/audio/hardware_level/set")){
        char *end;errno=0;long value=strtol(payload,&end,10);if(errno||end==payload||*end||value<0||value>100)return;
        if(!audio_hardware_write(value)){
            audio_hardware_target=value;audio_hardware_last=-1;
            int fd=open(STATE"/audio-output-trim.new",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600);
            if(fd>=0){char buffer[16];int n=snprintf(buffer,sizeof(buffer),"%ld\n",value);int ok=write(fd,buffer,n)==n;close(fd);if(ok)rename(STATE"/audio-output-trim.new",STATE"/audio-output-trim");}
        }
    }
}
static void audio_status(const char *line){
    // Only the verified audio APK UID reaches this IPC. Mirror the same
    // Settings.System preference used by Tuya's quick settings and boot code.
    if(!strcmp(line,"SPEAKERS ON")){audio_speaker_pending=1;return;}
    if(!strcmp(line,"SPEAKERS OFF")){audio_speaker_pending=0;return;}
    int volume,muted,wanted,power,offset=0;
    if(sscanf(line,"STATE %d %d %d %d %n",&volume,&muted,&wanted,&power,&offset)!=4||!offset)return;
    const char *json=line+offset;size_t n=strlen(json);
    if(volume<0||volume>100||muted<0||muted>1||wanted<0||wanted>1||power<0||power>1||n<2||n>=sizeof(audio_state)||json[0]!='{'||json[n-1]!='}')return;
    memcpy(audio_state,json,n+1);audio_rx_at=ms();if(audio_wanted&&!wanted)audio_stop_notify=1;audio_wanted=wanted;
    audio_gain_write(muted?0:volume);
    if(!wanted){audio_line_stop();audio_failed=0;}
    if(!power&&audio_power_last!=0&&audio_off<0){
        audio_off=fork();if(!audio_off){execl(STATE"/panel-line-in","panel-line-in","--off",(char*)NULL);_exit(127);}
    }
    audio_power_last=power;
}
static void audio_receive(void){
    if(audio_client<0)return;
    ssize_t n=recv(audio_client,audio_lines+audio_lines_len,sizeof(audio_lines)-1-audio_lines_len,MSG_DONTWAIT);
    if(n<=0){if(n==0||(errno!=EAGAIN&&errno!=EINTR))audio_disconnect();return;}
    audio_lines_len+=n;audio_lines[audio_lines_len]=0;char *end;
    while((end=memchr(audio_lines,'\n',audio_lines_len))){
        size_t length=end-audio_lines;audio_lines[length]=0;audio_status(audio_lines);
        size_t used=length+1;memmove(audio_lines,audio_lines+used,audio_lines_len-used);audio_lines_len-=used;
    }
    if(audio_lines_len==sizeof(audio_lines)-1)audio_disconnect();
}
static void audio_init(void){
    if(dry_run)return;
    FILE *saved=fopen(STATE"/audio-output-trim","r");int target=-1;
    if(saved){if(fscanf(saved,"%d",&target)==1&&target>=0&&target<=100)audio_hardware_target=target;fclose(saved);}
    FILE *f=fopen(STATE"/audio.uid","r");
    if(!f)return;int ok=fscanf(f,"%d",&audio_uid)==1;fclose(f);if(!ok||audio_uid<10000){audio_uid=-1;return;}
    audio_server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
    const char *name="wallpanel_audio_bridge";struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path+1,name,strlen(name));
    if(audio_server<0||bind(audio_server,(struct sockaddr*)&address,sizeof(sa_family_t)+1+strlen(name))||listen(audio_server,2)){
        if(audio_server>=0)close(audio_server);audio_server=-1;fprintf(stderr,"Audio IPC listener unavailable\n");
    }
}
static void audio_tick(void){
    if(audio_server<0)return;long long now=ms();
    if(audio_speaker_saver>0){
        int status;
        if(waitpid(audio_speaker_saver,&status,WNOHANG)==audio_speaker_saver){
            audio_speaker_saver=-1;
            audio_send(WIFEXITED(status)&&WEXITSTATUS(status)==0?"{\"command\":\"speaker_saved\",\"ok\":true}":"{\"command\":\"speaker_saved\",\"ok\":false}");
        }else if(now-audio_speaker_started>10000)kill(audio_speaker_saver,SIGKILL);
    }
    if(audio_speaker_saver<0&&audio_speaker_pending>=0){
        int chosen=audio_speaker_pending;audio_speaker_saver=fork();
        if(!audio_speaker_saver){
            int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}
            execl("/system/bin/settings","settings","put","system","com.sznaner.pulldownmenu.trumpet.state",chosen?"true":"false",(char*)NULL);_exit(127);
        }
        if(audio_speaker_saver>0){audio_speaker_pending=-1;audio_speaker_started=now;}
    }
    // The codec driver restores its DT level when the route opens. Reapply a
    // user-selected trim without rewriting persistent settings or opening PCM.
    if(now>=audio_hardware_at){audio_hardware_at=now+1000;if(audio_hardware_target>=0&&audio_hardware_raw()!=80-(77*audio_hardware_target+50)/100)audio_hardware_write(audio_hardware_target);}
    if(audio_starter>0&&waitpid(audio_starter,NULL,WNOHANG)==audio_starter)audio_starter=-1;
    if(audio_off>0&&waitpid(audio_off,NULL,WNOHANG)==audio_off)audio_off=-1;
    if(audio_client<0&&audio_starter<0&&now-audio_start_at>10000){
        audio_start_at=now;audio_starter=fork();
        if(!audio_starter){int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);}execl("/system/bin/am","am","startservice","-n","local.wallpanel.audio/.AudioService",(char*)NULL);_exit(127);}
    }
    int fd=accept4(audio_server,NULL,NULL,SOCK_CLOEXEC|SOCK_NONBLOCK);
    if(fd>=0){struct ucred peer;socklen_t len=sizeof(peer);
        if(audio_client>=0||getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&len)||peer.uid!=(unsigned)audio_uid)close(fd);
        else{audio_client=fd;audio_rx_at=now;audio_lines_len=0;audio_power_last=-1;audio_send("{\"command\":\"status\"}");}
    }
    audio_receive();if(audio_client>=0&&now-audio_rx_at>8000)audio_disconnect();
    if(audio_worker>0){
        char chunk[256];ssize_t n;
        while(audio_worker_fd>=0&&(n=read(audio_worker_fd,chunk,sizeof(chunk)))>0){
            for(ssize_t i=0;i<n;i++){
                if(chunk[i]=='\n'){
                    audio_worker_line[audio_worker_len]=0;
                    if(!strcmp(audio_worker_line,"READY")&&audio_wanted&&!audio_stopping_at){audio_ready=1;audio_send("{\"command\":\"line_status\",\"ready\":true}");}
                    audio_worker_len=0;
                }else if(audio_worker_len<sizeof(audio_worker_line)-1)audio_worker_line[audio_worker_len++]=chunk[i];
            }
        }
        int status;if(waitpid(audio_worker,&status,WNOHANG)==audio_worker){
            int unexpected=!audio_stopping_at&&audio_wanted;audio_worker=-1;audio_ready=0;audio_stopping_at=0;
            if(audio_worker_fd>=0)close(audio_worker_fd);audio_worker_fd=-1;
            if(unexpected){audio_failed=1;audio_send("{\"command\":\"line_status\",\"ready\":false,\"error\":\"Line Input stream could not run\"}");}
        }else if(audio_stopping_at&&now-audio_stopping_at>3000)kill(-audio_worker,SIGKILL);
        else if(!audio_ready&&!audio_stopping_at&&now-audio_worker_at>15000){audio_failed=1;audio_line_stop();audio_send("{\"command\":\"line_status\",\"ready\":false,\"error\":\"Line Input startup timed out\"}");}
    }
    if(audio_wanted&&audio_worker<0&&!audio_failed){
        int pipes[2];if(pipe2(pipes,O_CLOEXEC|O_NONBLOCK))return;
        audio_worker=fork();
        if(!audio_worker){setpgid(0,0);dup2(pipes[1],1);close(pipes[0]);close(pipes[1]);execl(STATE"/panel-line-in","panel-line-in",(char*)NULL);_exit(127);}
        close(pipes[1]);if(audio_worker>0){setpgid(audio_worker,audio_worker);audio_worker_fd=pipes[0];audio_worker_at=now;audio_stopping_at=0;audio_worker_len=0;}else close(pipes[0]);
    }
    if(audio_stop_notify&&audio_worker<0){audio_stop_notify=0;audio_send("{\"command\":\"line_status\",\"ready\":false,\"stopped\":true}");}
}
static int audio_publish(void){
#ifdef WALLPANEL_DIRECT_MCU
    if(audio_external)return 0;
#endif
    int available=audio_client>=0&&audio_state[0];
    if(available!=audio_available_last){if(publish(BASE"/audio/owner","legacy",1)||publish(BASE"/audio/availability",available?"online":"offline",1))return -1;audio_available_last=available;}
    if(available&&strcmp(audio_state,audio_sent)){if(publish(BASE"/audio/state",audio_state,1))return -1;strcpy(audio_sent,audio_state);}
    int level=audio_hardware_level();int amp=level>=0;
    if(amp!=audio_amp_available_last){if(publish(BASE"/audio/amplifier/availability",amp?"online":"offline",1))return -1;audio_amp_available_last=amp;}
    if(level>=0&&level!=audio_hardware_last){char value[16];snprintf(value,sizeof(value),"%d",level);if(publish(BASE"/audio/hardware_level/state",value,1))return -1;audio_hardware_last=level;}
    return 0;
}
static void audio_cleanup(void){
    if(audio_speaker_saver>0)kill(audio_speaker_saver,SIGTERM);
    audio_disconnect();if(audio_server>=0)close(audio_server);audio_server=-1;audio_speaker_pending=-1;
    if(audio_worker>0){kill(-audio_worker,SIGTERM);}
    if(audio_worker_fd>=0)close(audio_worker_fd);audio_worker_fd=-1;
}
