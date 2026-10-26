/* Root hardware worker. Private inherited pipes only; no network/server/shell.
 * Not activated by the foundation app. Never stop/disable a competing owner.
 */
#define _GNU_SOURCE
#include <signal.h>
#include <stdint.h>
#include <errno.h>
#include "core-config.h"
#include "../../wallpanel-hardware/g10_uart.h"
#include "../../wallpanel-hardware/g10_profile.h"
#include "../../wallpanel-hardware/mcu-lease.h"
#ifndef WP_BINDINGS_PATH
#define WP_BINDINGS_PATH "/data/local/panalume/button-local-bindings"
#endif
#ifndef WP_UART_OPEN
#define WP_UART_OPEN g10_uart_open
#define WP_UART_SEND g10_uart_send
#define WP_UART_CLOSE g10_uart_close
#define WP_PROFILE_VALID g10_profile_valid
#endif
static volatile sig_atomic_t core_alive=1;
static int core_exit_code;
static int core_leased;
static struct core_config core_config;
static struct g10_uart core_uart={.fd=-1};
static struct g10_parser core_parser;
static const char *core_direction="/proc/vendor/485_tx_mode";
static int core_values[2]={-1,-1},core_wait_query[2],core_online;
static int core_targets[2]={-1,-1};
static long long core_seen[2],core_retry,core_query_at,core_early_query,core_target_until[2];
static unsigned long core_last_request;
#ifndef WP_OUTPUT
#define WP_HEALTH_PATH "/dev/wallpanel-app-status.json"
static int core_output(const char *line){
    size_t n=strlen(line);ssize_t done=write(STDOUT_FILENO,line,n);
    if(done!=(ssize_t)n){core_exit_code=5;core_alive=0;return -1;}return 0;
}
#define WP_OUTPUT core_output
#endif
#include "core-health.h"
#include "../../panel-172.16.1.26/panel-gestures.h"
static int core_ready(long long now){return core_uart.fd>=0&&core_values[0]>=0&&core_values[1]>=0&&now-core_seen[0]<45000&&now-core_seen[1]<45000;}
static int core_knob_input_ready(void);
static int core_knob_display_ready(void);
static int core_audio_owned(void);
static int core_display_ready;
static int core_management_owned(void);
static void core_status(unsigned long request){
    char line[1024];snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"status\",\"request_id\":%lu,\"device_id\":\"%s\",\"online\":%s,\"buttons_enabled\":%s,\"relays_enabled\":%s,\"relays\":[%d,%d],\"button_hold\":false,\"knob_input_enabled\":%s,\"knob_display_enabled\":%s,\"knob_input_ready\":%s,\"knob_display_ready\":%s,\"audio_owned\":%s,\"audio_enabled\":%s,\"amplifier_enabled\":%s,\"display_ready\":%s,\"management_owned\":%s,\"worker_pid\":%d}\n",
        request,core_config.device_id,core_online?"true":"false",core_config.buttons?"true":"false",core_config.relays?"true":"false",core_values[0],core_values[1],core_config.knob_input?"true":"false",core_config.knob_display?"true":"false",core_knob_input_ready()?"true":"false",core_knob_display_ready()?"true":"false",core_audio_owned()?"true":"false",core_config.audio?"true":"false",core_config.amplifier?"true":"false",core_display_ready?"true":"false",core_management_owned()?"true":"false",getpid());core_health(line);WP_OUTPUT(line);
}
#include "knob-core.h"
static void core_result(unsigned long id,int good,const char *error){
    char line[256];snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"result\",\"id\":%lu,\"ok\":%s,\"confirmed\":false,\"error\":\"%s\"}\n",id,good?"true":"false",error);WP_OUTPUT(line);
}
#include "audio-core.h"
#include "display-core.h"
#include "management-core.h"
static void core_bindings_emit(void){
    char line[256];snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"bindings\",\"values\":[[%d,%d],[%d,%d],[%d,%d],[%d,%d]]}\n",
        core_config.bindings[0][0],core_config.bindings[0][1],core_config.bindings[1][0],core_config.bindings[1][1],core_config.bindings[2][0],core_config.bindings[2][1],core_config.bindings[3][0],core_config.bindings[3][1]);WP_OUTPUT(line);
}
static int core_bindings_load(void){
    int fd=open(WP_BINDINGS_PATH,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return errno==ENOENT?0:-1;
    struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=WP_LEASE_UID||(st.st_mode&0077)){close(fd);return -1;}
    FILE *f=fdopen(fd,"r");if(!f){close(fd);return -1;}int proposed[4][2],bad=0;char line[64],extra;
    for(int i=0;i<4;i++)if(!fgets(line,sizeof(line),f)||sscanf(line,"%d %d %c",&proposed[i][0],&proposed[i][1],&extra)!=2
        ||proposed[i][0]<0||proposed[i][0]>2||proposed[i][1]<0||proposed[i][1]>2){bad=1;break;}
    int c;while(!bad&&(c=fgetc(f))!=EOF)if(!isspace((unsigned char)c))bad=1;
    if(ferror(f))bad=1;fclose(f);if(bad)return -1;memcpy(core_config.bindings,proposed,sizeof(proposed));return 0;
}
static int core_bindings_save(int proposed[4][2]){
    const char *tmp=WP_BINDINGS_PATH ".app-new";int fd=open(tmp,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);if(fd<0)return -1;
    struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=WP_LEASE_UID||(st.st_mode&0077)){close(fd);return -1;}
    char b[64];int n=snprintf(b,sizeof(b),"%d %d\n%d %d\n%d %d\n%d %d\n",proposed[0][0],proposed[0][1],proposed[1][0],proposed[1][1],proposed[2][0],proposed[2][1],proposed[3][0],proposed[3][1]);
    int ok=write(fd,b,n)==n&&!fsync(fd);if(close(fd))ok=0;
    if(!ok||rename(tmp,WP_BINDINGS_PATH)){unlink(tmp);return -1;}return 0;
}
static void core_disconnect(void){
    if(WP_UART_CLOSE(&core_uart,core_direction)){
        core_exit_code=8;core_alive=0;WP_OUTPUT("{\"v\":1,\"type\":\"error\",\"error\":\"uart_cleanup_failed\"}\n");
    }
    core_online=0;
    memset(&core_parser,0,sizeof(core_parser));for(int b=1;b<=4;b++)gesture_cancel(b);
    for(int i=0;i<2;i++){core_values[i]=-1;core_seen[i]=0;core_wait_query[i]=0;core_targets[i]=-1;core_target_until[i]=0;}
    core_early_query=0;core_status(0);
}
static int core_send(const unsigned char frame[7]){
    /* This worker exposes no night-light, binding-default or arbitrary-frame op. */
    if(core_uart.fd<0||WP_UART_SEND(&core_uart,core_direction,frame)){core_disconnect();return -1;}return 0;
}
static int core_relay(int channel,int value,long long now){
    if(!core_config.relays||!core_ready(now))return -1;
    unsigned char frame[7];if(g10_relay(frame,channel,value))return -1;
    core_wait_query[channel-1]=1;
    if(core_send(frame))return -1;
    if(!core_early_query)core_early_query=now+500;
    return 0;
}
static void gesture_publish(int control,const char *event,unsigned int code){
    if(core_alive&&control==0&&core_config.knob_input){
        core_knob_activity_at(g10_ms());
        struct timespec t={0};clock_gettime(CLOCK_BOOTTIME,&t);char line[256];
        snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"knob\",\"event\":\"%s\",\"key_code\":%u,\"steps\":1,\"at_ms\":%lld}\n",event,code,(long long)t.tv_sec*1000+t.tv_nsec/1000000);WP_OUTPUT(line);return;
    }
    if(!core_alive||!core_config.buttons||control<1||control>4)return;
    int index=!strcmp(event,"double_press")?1:0;
    int binding=core_config.bindings[control-1][index];
    if(binding&&core_config.relays){
        int i=binding-1;long long now=g10_ms();
        int before=core_targets[i]>=0&&now<core_target_until[i]?core_targets[i]:core_values[i];
        if(before>=0){int target=!before;if(!core_relay(binding,target,now)){core_targets[i]=target;core_target_until[i]=now+3000;}}
    }
    struct timespec boot={0};clock_gettime(CLOCK_BOOTTIME,&boot);long long emitted=(long long)boot.tv_sec*1000+boot.tv_nsec/1000000;
    char line[256];snprintf(line,sizeof(line),"{\"v\":1,\"type\":\"button\",\"button\":%d,\"event\":\"%s\",\"module_id\":%u,\"local_binding\":%d,\"at_ms\":%lld}\n",control,event,code,binding,emitted);WP_OUTPUT(line);
}
static void core_event(void *ignored,const struct g10_event *event){
    (void)ignored;if(!core_alive)return;
    if(event->kind==G10_RELAY_STATE){
        int i=event->channel-1;if(core_wait_query[i])return;
        core_values[i]=event->value;core_seen[i]=g10_ms();
        if(core_targets[i]==event->value){core_targets[i]=-1;core_target_until[i]=0;}
        core_online=core_ready(g10_ms());core_status(0);
    }else if(event->kind==G10_BUTTON_CLICK&&core_config.buttons)
        gesture_pulse_at(event->channel,(unsigned)event->value,g10_ms());
}
static void core_receive(void){
    for(int chunks=0;chunks<16&&core_uart.fd>=0;chunks++){
        struct pollfd p={.fd=core_uart.fd,.events=POLLIN};int ready=poll(&p,1,0);
        if(ready<0){if(errno!=EINTR)core_disconnect();return;}
        if(p.revents&(POLLERR|POLLHUP|POLLNVAL)){core_disconnect();return;}
        if(!(p.revents&POLLIN))return;
        unsigned char data[256];ssize_t n=read(core_uart.fd,data,sizeof(data));
        if(n>0)g10_feed(&core_parser,data,(size_t)n,core_event,NULL);
        else if(n==0||(errno!=EINTR&&errno!=EAGAIN)){core_disconnect();return;}
    }
}
static void core_tick(long long now){
    if(!core_config.buttons&&!core_config.relays)return;
    if(core_uart.fd<0){
        if(now<core_retry)return;core_retry=now+3000;
        if(!WP_PROFILE_VALID()){WP_OUTPUT("{\"v\":1,\"type\":\"error\",\"error\":\"unsupported_hardware\"}\n");core_exit_code=6;core_alive=0;return;}
        if(WP_UART_OPEN(&core_uart,"/dev/ttyS2",core_direction)){
            WP_OUTPUT("{\"v\":1,\"type\":\"error\",\"error\":\"hardware_busy_or_unavailable\"}\n");return;
        }
        core_query_at=0;memset(&core_parser,0,sizeof(core_parser));
        for(int b=1;b<=4;b++){unsigned char frame[7];g10_scene(frame,b);if(core_send(frame))return;}
    }
    core_receive();if(!core_alive)return;
    if(core_uart.fd>=0&&(!core_query_at||now-core_query_at>=20000||(core_early_query&&now>=core_early_query))){
        unsigned char query[7];g10_query(query);
        if(!core_send(query)){core_query_at=now;core_wait_query[0]=core_wait_query[1]=0;core_early_query=0;}
    }
    if(core_online&&!core_ready(now)){
        core_online=0;for(int b=1;b<=4;b++)gesture_cancel(b);
        for(int i=0;i<2;i++){core_values[i]=-1;core_targets[i]=-1;core_target_until[i]=0;}core_status(0);
    }
    if(core_alive)gestures_tick_at(now);
}
static void core_command(const char *line){
    unsigned long id=0;unsigned channel=0,value=0;int end=0;char canonical[128];
    char management_field[16];
    if(strstr(line," management ")&&sscanf(line,"v1 %lu management %15s %u%n",&id,management_field,&value,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu management %s %u",id,management_field,value);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request||value|| (strcmp(management_field,"status")&&strcmp(management_field,"reboot"))){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;int bad=core_management_command(!strcmp(management_field,"reboot"),g10_ms());core_result(id,!bad,bad?"management_not_ready":"");return;
    }
    end=0;
    char display_field[24];
    if(strstr(line," display ")&&sscanf(line,"v1 %lu display %23s %u%n",&id,display_field,&value,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu display %s %u",id,display_field,value);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request||value>86400){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;
        if(!strcmp(display_field,"enabled")&&value<=1){core_config.display=(int)value;if(!value)core_display_close();core_status(id);core_result(id,1,"");return;}
        if(!strcmp(display_field,"status")&&value==0){core_display_sent[0]=0;core_status(id);return;}
        int bad=!core_config.display||!core_display_ready||bl_command(display_field,(int)value,bl_now());
        core_result(id,!bad,bad?"display_unavailable_or_invalid_setting":"");return;
    }
    end=0;
    if(sscanf(line,"v1 %lu audio_status%n",&id,&end)==1&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu audio_status",id);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;core_audio_level_last=-1;core_status(id);return;
    }
    end=0;
    char audio_field[16];
    if(strstr(line," audio ")&&sscanf(line,"v1 %lu audio %15s %u%n",&id,audio_field,&value,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu audio %s %u",id,audio_field,value);
        int max=!strcmp(audio_field,"level")?100:1;
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request||value>(unsigned)max){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;int bad=core_audio_command(audio_field,(int)value);core_result(id,!bad,bad?"audio_unavailable_or_disabled":"");return;
    }
    end=0;unsigned ae=0,ap=0;
    if(sscanf(line,"v1 %lu audiomodules %u %u%n",&id,&ae,&ap,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu audiomodules %u %u",id,ae,ap);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request||ae>1||ap>1){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;if(core_config.audio!=(int)ae||core_config.amplifier!=(int)ap)core_audio_close();
        core_config.audio=(int)ae;core_config.amplifier=(int)ap;core_status(id);core_result(id,1,"");return;
    }
    end=0;
    if(sscanf(line,"v1 %lu knob_status%n",&id,&end)==1&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu knob_status",id);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;knob_report=1;core_status(id);return;
    }
    end=0;char field[17],hex[1025],full[1152];
    if(strstr(line," knob ")&&sscanf(line,"v1 %lu knob %16s %1024s%n",&id,field,hex,&end)==3&&end>0){
        snprintf(full,sizeof(full),"v1 %lu knob %s %s",id,field,hex);
        if(strcmp(full,line)||!id||id>2147483647ul||id<=core_last_request){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;char payload[512];size_t n=strlen(hex);
        if(!strcmp(hex,"-")){payload[0]=0;n=0;}
        else{
            if(n%2||n/2>=sizeof(payload)){core_result(id,0,"invalid_payload");return;}
            for(size_t i=0;i<n;i+=2){
                const char *digits="0123456789abcdef";const char *a=strchr(digits,hex[i]),*b=strchr(digits,hex[i+1]);
                if(!a||!b){core_result(id,0,"invalid_payload");return;}
                int c=(int)(a-digits)*16+(int)(b-digits);
                if(!c){core_result(id,0,"invalid_payload");return;}payload[i/2]=(char)c;
            }payload[n/2]=0;
        }
        int bad=core_knob_command(field,payload);core_result(id,!bad,bad?"knob_unavailable_or_invalid_payload":"");return;
    }
    end=0;unsigned ki=0,kd=0;
    if(sscanf(line,"v1 %lu knobmodules %u %u%n",&id,&ki,&kd,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu knobmodules %u %u",id,ki,kd);
        if(strcmp(canonical,line)||!id||id>2147483647ul||id<=core_last_request||ki>1||kd>1){core_result(id,0,"invalid_or_old_request");return;}
        core_last_request=id;if(core_config.knob_input!=(int)ki||core_config.knob_display!=(int)kd)core_knob_close();
        core_config.knob_input=(int)ki;core_config.knob_display=(int)kd;core_status(id);core_result(id,1,"");return;
    }
    end=0;
    if(sscanf(line,"v1 %lu status%n",&id,&end)==1&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu status",id);
        if(strcmp(canonical,line)||!id||id>2147483647ul){core_result(0,0,"invalid_command");return;}
        if(id<=core_last_request){core_result(id,0,"duplicate_or_old_request");return;}
        core_last_request=id;core_status(id);return;
    }
    end=0;
    unsigned enabled_buttons=0,enabled_relays=0;
    if(sscanf(line,"v1 %lu modules %u %u%n",&id,&enabled_buttons,&enabled_relays,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu modules %u %u",id,enabled_buttons,enabled_relays);
        if(strcmp(canonical,line)||!id||id>2147483647ul||enabled_buttons>1||enabled_relays>1){core_result(0,0,"invalid_command");return;}
        if(id<=core_last_request){core_result(id,0,"duplicate_or_old_request");return;}core_last_request=id;
        core_config.buttons=(int)enabled_buttons;core_config.relays=(int)enabled_relays;gestures_cancel_all();
        for(int i=0;i<2;i++){core_targets[i]=-1;core_target_until[i]=0;}
        if(!enabled_buttons&&!enabled_relays)core_disconnect();
        core_retry=0;core_status(id);core_result(id,1,"");return;
    }
    end=0;
    unsigned single=0,doubled=0;
    if(sscanf(line,"v1 %lu bind %u %u %u%n",&id,&channel,&single,&doubled,&end)==4&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu bind %u %u %u",id,channel,single,doubled);
        if(strcmp(canonical,line)||!id||id>2147483647ul||channel<1||channel>4||single>2||doubled>2){core_result(0,0,"invalid_command");return;}
        if(id<=core_last_request){core_result(id,0,"duplicate_or_old_request");return;}core_last_request=id;
        if(!core_leased){core_result(id,0,"binding_persistence_unavailable");return;}
        int proposed[4][2];memcpy(proposed,core_config.bindings,sizeof(proposed));proposed[channel-1][0]=(int)single;proposed[channel-1][1]=(int)doubled;
        if(memcmp(proposed,core_config.bindings,sizeof(proposed))){
            if(core_bindings_save(proposed)){core_result(id,0,"binding_save_failed");return;}
            memcpy(core_config.bindings,proposed,sizeof(proposed));gesture_cancel((int)channel);
        }
        core_bindings_emit();core_result(id,1,"");return;
    }
    end=0;
    if(sscanf(line,"v1 %lu relay %u %u%n",&id,&channel,&value,&end)==3&&end>0){
        snprintf(canonical,sizeof(canonical),"v1 %lu relay %u %u",id,channel,value);
        if(strcmp(canonical,line)||!id||id>2147483647ul||channel<1||channel>2||value>1){core_result(0,0,"invalid_command");return;}
        if(id<=core_last_request){core_result(id,0,"duplicate_or_old_request");return;}
        core_last_request=id;
        if(!core_config.relays){core_result(id,0,"module_disabled");return;}
        if(!core_ready(g10_ms())){core_result(id,0,"hardware_unavailable");return;}
        core_targets[channel-1]=-1;core_target_until[channel-1]=0;
        int sent=core_relay((int)channel,(int)value,g10_ms());core_result(id,!sent,sent?"uart_failed":"");return;
    }
    core_result(0,0,"invalid_command");
}
static void core_signal(int sig){(void)sig;core_alive=0;}
int main(int argc,char **argv){
    if(argc==2&&!strcmp(argv[1],"--probe")){
        int matched=WP_PROFILE_VALID()&&access("/dev/ttyS2",F_OK)==0&&access(core_direction,F_OK)==0;
        printf("{\"v\":1,\"profile\":\"g10\",\"layout_matched\":%s,\"buttons\":%d,\"relays\":%d,\"knob\":%s,\"hardware_accessed\":false}\n",matched?"true":"false",matched?4:0,matched?2:0,matched?"true":"false");return 0;
    }
    if(argc==2&&!strcmp(argv[1],"--describe")){
        puts("{\"v\":1,\"adapter\":\"g10-direct-uart\",\"profile\":\"g10\",\"profile_verified\":false,\"buttons\":4,\"relays\":2,\"single_double_ms\":400,\"button_hold\":false,\"network\":false,\"root_required\":true}");return 0;
    }
    if(argc!=3||(strcmp(argv[1],"--run")&&strcmp(argv[1],"--run-leased")&&strcmp(argv[1],"--check-config"))){fprintf(stderr,"Use --describe, --check-config FILE, --run FILE or --run-leased FILE\n");return 2;}
    if(core_config_read(argv[2],&core_config)){fprintf(stderr,"Invalid or unsafe hardware configuration\n");return 3;}
    if(!strcmp(argv[1],"--check-config")){puts("{\"v\":1,\"configuration_valid\":true,\"hardware_accessed\":false}");return 0;}
    if(geteuid()!=0){fprintf(stderr,"Privileged hardware activation requires root\n");return 4;}
    int lease=-1,knob_lease=-1,audio_lease=-1;core_leased=!strcmp(argv[1],"--run-leased");
    if(core_leased){
        if(!WP_PROFILE_VALID()){fprintf(stderr,"Unsupported hardware; no lease acquired\n");return 6;}
        lease=wp_lease_acquire();if(lease<0){fprintf(stderr,"Hardware lease unavailable\n");return 9;}
        if(core_bindings_load()){close(lease);fprintf(stderr,"Invalid saved bindings; no UART opened\n");return 3;}
        knob_lease=wp_lock_acquire(WP_KNOB_REQUEST);if(knob_lease<0){close(lease);return 9;}
        audio_lease=wp_lock_acquire(WP_AUDIO_REQUEST);if(audio_lease<0){close(knob_lease);close(lease);return 9;}
    }
    signal(SIGINT,core_signal);signal(SIGTERM,core_signal);signal(SIGHUP,core_signal);signal(SIGPIPE,SIG_IGN);
    if(fcntl(STDOUT_FILENO,F_SETFL,fcntl(STDOUT_FILENO,F_GETFL)|O_NONBLOCK)<0)return 5;
    if(fcntl(STDIN_FILENO,F_SETFL,fcntl(STDIN_FILENO,F_GETFL)|O_NONBLOCK)<0)return 5;
    gestures_cancel_all();core_status(0);if(core_leased)core_bindings_emit();char command[1536];size_t used=0;
    while(core_alive){
        core_tick(g10_ms());if(!core_alive)break;
        if(core_leased)core_knob_tick();if(!core_alive)break;
        if(core_leased)core_audio_tick();if(!core_alive)break;
        if(core_leased)core_display_tick();if(!core_alive)break;
        if(core_leased)core_management_tick(g10_ms());if(!core_alive)break;
        struct pollfd p[12]={{.fd=STDIN_FILENO,.events=POLLIN},{.fd=core_uart.fd,.events=POLLIN},{.fd=core_knob_input,.events=POLLIN},{.fd=rotary_fd,.events=POLLIN}};
        int count=4;if(core_display_ready)for(int i=0;i<bl_count;i++)p[count++]=bl_inputs[i];
        int ready=poll(p,count,gestures_wait_ms(g10_ms(),100));
        if(ready<0){if(errno==EINTR)continue;break;}
        if(p[0].revents&POLLIN){
            char buffer[192];ssize_t n=read(STDIN_FILENO,buffer,sizeof(buffer));if(n<=0){if(n==0||(errno!=EAGAIN&&errno!=EINTR))break;}
            for(ssize_t i=0;i<n&&core_alive;i++){
                unsigned char c=(unsigned char)buffer[i];
                if(c=='\n'){command[used]=0;core_command(command);used=0;}
                else if(c<32||c>126||used>=sizeof(command)-1){core_result(0,0,"invalid_framing");core_exit_code=7;core_alive=0;}
                else command[used++]=(char)c;
            }
        }
        if(p[0].revents&(POLLHUP|POLLERR|POLLNVAL))break;
    }
    core_management_close();core_display_close();core_audio_close();if(core_audio_owner>=0)close(core_audio_owner);if(audio_lease>=0)close(audio_lease);
    core_knob_close();if(core_knob_owner>=0)close(core_knob_owner);if(knob_lease>=0)close(knob_lease);
    core_disconnect();if(lease>=0)close(lease);return core_exit_code;
}
