/* Vendor IPC adapter. The vendor retains ownership of the UART. This process
 * accepts only the installed adapter UID on an abstract Unix socket; no TCP
 * listener is opened. Relay state is read from MCU replies, never assumed. */
#include <sys/un.h>
#include <sys/wait.h>

static int module_server=-1,module_client=-1,module_uid=-1;
static int module_values[2]={-1,-1},module_online=0,module_availability_last=-1;
static long long module_rx_at,module_query_at,module_start_at;
static pid_t module_starter=-1;
static char module_lines[8192];
static size_t module_lines_len;
static unsigned char module_bytes[4096];
static size_t module_bytes_len;

static void module_disconnect(void) {
    for(int i=1;i<5;i++)gesture_cancel(i);
    if(module_client>=0)close(module_client);
    module_client=-1;module_online=0;module_values[0]=module_values[1]=-1;
    module_lines_len=module_bytes_len=0;
}
static int module_send(const char *line) {
    if(module_client<0)return -1;
    size_t len=strlen(line);
    /* One short, atomic local message. A failed send is never replayed. */
    if(send(module_client,line,len,MSG_NOSIGNAL|MSG_DONTWAIT)!=(ssize_t)len){module_disconnect();return -1;}
    return 0;
}
static int module_init(void) {
    if(dry_run){module_values[0]=module_values[1]=0;return 0;}
    FILE *f=fopen(STATE"/module.uid","r");
    if(!f||fscanf(f,"%d",&module_uid)!=1||module_uid<10000){if(f)fclose(f);fprintf(stderr,"Missing adapter UID\n");return -1;}
    fclose(f);
    module_server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
    const char *name="panalume_module_bridge";
    struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path+1,name,strlen(name));
    if(module_server<0||bind(module_server,(struct sockaddr *)&address,sizeof(sa_family_t)+1+strlen(name))||listen(module_server,2)){perror("module listener");return -1;}
    return 0;
}
static void module_event(int id) {
    int button=id==1?1:id==2?2:id==5?3:id==6?4:0;
    if(!button)return;
    /* Hardware shortcuts work on the first press, independently of backlight.
     * Only a touchscreen contact (or explicit display command) wakes the panel. */
    int local=local_button_enabled(button);
    if(net<0&&!local)return;
    long long now=ms();
    if(gesture_pending[button].deadline&&now>=gesture_pending[button].deadline)gesture_flush(button);
    if(!gesture_pending[button].deadline)local_suppress_ha[button]=0;
    if(net<0)local_suppress_ha[button]=1;
    gesture_pulse_at(button,id,now);
}
static void module_frames(void) {
    while(module_bytes_len>=7) {
        unsigned char *p=module_bytes;
        if(p[0]!=0xfc||p[1]!=0||p[2]!=0||p[6]!=0xfe){memmove(p,p+1,--module_bytes_len);continue;}
        if((p[3]==0xa1||p[3]==0xa2)&&p[4]<=1){module_values[p[3]-0xa1]=p[4];local_relay_feedback(p[3]-0xa1,p[4]);module_rx_at=ms();}
        else if(p[3]==0&&p[4]==1){module_rx_at=ms();module_event(p[5]);}
        memmove(p,p+7,module_bytes_len-7);module_bytes_len-=7;
    }
    module_online=module_values[0]>=0&&module_values[1]>=0&&ms()-module_rx_at<45000;
}
static int module_hex_digit(char c){if(c>='0'&&c<='9')return c-'0';if(c>='A'&&c<='F')return c-'A'+10;if(c>='a'&&c<='f')return c-'a'+10;return -1;}
static void module_line(const char *line) {
    if(!strcmp(line,"READY")){module_query_at=ms();module_send("FC0000AAFF00FE\n");return;}
    if(strncmp(line,"RX ",3))return;
    const char *hex=line+3;size_t len=strlen(hex);
    if(len%2||len/2>sizeof(module_bytes)-module_bytes_len){module_bytes_len=0;return;}
    /* Validate the complete line before appending anything. */
    for(size_t i=0;i<len;i++)if(module_hex_digit(hex[i])<0)return;
    for(size_t i=0;i<len;i+=2)module_bytes[module_bytes_len++]=(module_hex_digit(hex[i])<<4)|module_hex_digit(hex[i+1]);
    module_frames();
}
static void module_receive(void) {
    if(module_client<0)return;
    ssize_t n=recv(module_client,module_lines+module_lines_len,sizeof(module_lines)-1-module_lines_len,MSG_DONTWAIT);
    if(n<=0){if(n==0||(errno!=EAGAIN&&errno!=EINTR))module_disconnect();return;}
    module_lines_len+=n;module_lines[module_lines_len]=0;
    char *end;
    while((end=memchr(module_lines,'\n',module_lines_len))) {
        size_t len=end-module_lines;module_lines[len]=0;
        if(len&&module_lines[len-1]=='\r')module_lines[len-1]=0;
        module_line(module_lines);
        if(module_client<0)return;
        size_t consumed=len+1;memmove(module_lines,module_lines+consumed,module_lines_len-consumed);module_lines_len-=consumed;
    }
    if(module_lines_len==sizeof(module_lines)-1)module_disconnect();
}
static void module_tick(void) {
    if(dry_run)return;
    long long now=ms();
    if(module_starter>0&&waitpid(module_starter,NULL,WNOHANG)==module_starter)module_starter=-1;
    if(module_client<0&&module_starter<0&&now-module_start_at>=10000) {
        module_start_at=now;module_starter=fork();
        if(!module_starter){int fd=open("/dev/null",O_RDWR);if(fd>=0){dup2(fd,0);dup2(fd,1);dup2(fd,2);if(fd>2)close(fd);}execl("/system/bin/am","am","startservice","-n","local.panalume.module/.ModuleService",(char *)NULL);_exit(127);}
    }
    int fd=accept4(module_server,NULL,NULL,SOCK_CLOEXEC|SOCK_NONBLOCK);
    if(fd>=0) {
        struct ucred peer;socklen_t length=sizeof(peer);
        if(module_client>=0||getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&length)||peer.uid!=(unsigned)module_uid)close(fd);
        else {module_client=fd;module_lines_len=module_bytes_len=0;module_values[0]=module_values[1]=-1;module_online=0;module_query_at=now;puts("Authenticated vendor-module adapter connected.");}
    }
    module_receive();
    if(module_client>=0&&now-module_query_at>=20000){module_query_at=now;module_send("FC0000AAFF00FE\n");}
    if(module_online&&now-module_rx_at>=45000){module_online=0;module_values[0]=module_values[1]=-1;}
}
static int module_publish_availability(void) {
    if(module_online==module_availability_last)return 0;
    if(publish(BASE"/module/availability",module_online?"online":"offline",1))return -1;
    module_availability_last=module_online;return 0;
}
static int module_relay_command(int channel,int value) {
    if(!module_online){puts("Relay command ignored: physical module unavailable.");return -1;}
    char line[32];snprintf(line,sizeof(line),"FC0000A%d%02X00FE\n",channel,value);
    if(!module_send(line)){
        /* The MCU does not acknowledge a UART command automatically. Query
         * shortly afterwards; publish only its actual reply. */
        module_query_at=ms()-19500;
        printf("Physical relay %d command %s sent.\n",channel,value?"ON":"OFF");
        return 0;
    }
    return -1;
}
