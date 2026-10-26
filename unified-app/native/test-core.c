/* No real hardware, broker, Android commands or live configuration files. */
#define _GNU_SOURCE
#define WP_LEASE_UID geteuid()
#define WP_BINDINGS_PATH "/tmp/wallpanel-unified-core-bindings"
#undef NDEBUG
#include <assert.h>
#include <sys/socket.h>
#include "../../wallpanel-hardware/g10_uart.h"
static int peer=-1,opens,sends,closes,profile_checks,profile_ok=1,busy,fail_send;
static unsigned char frames[256][7];
static char output[32768];static size_t output_used;
static int fake_output(const char *line){size_t n=strlen(line);assert(output_used+n<sizeof(output));memcpy(output+output_used,line,n+1);output_used+=n;return 0;}
static int fake_profile(void){profile_checks++;return profile_ok;}
static int fake_open(struct g10_uart *u,const char *device,const char *node){
    assert(!strcmp(device,"/dev/ttyS2"));(void)node;opens++;if(busy){errno=EBUSY;return -1;}
    int pair[2];assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0,pair));u->fd=pair[0];peer=pair[1];return 0;
}
static int fake_send(struct g10_uart *u,const char *node,const unsigned char frame[7]){
    (void)node;assert(u->fd>=0&&g10_allowed(frame));if(fail_send){errno=EIO;return -1;}assert(sends<256);memcpy(frames[sends++],frame,7);return 0;
}
static int fake_close(struct g10_uart *u,const char *node){(void)node;if(u->fd>=0){closes++;close(u->fd);}u->fd=-1;return 0;}
#define WP_OUTPUT fake_output
#define WP_UART_OPEN fake_open
#define WP_UART_SEND fake_send
#define WP_UART_CLOSE fake_close
#define WP_PROFILE_VALID fake_profile
#define main worker_main
#include "wallpanel-core.c"
#undef main
static void reset(void){
    if(core_uart.fd>=0)fake_close(&core_uart,"");if(peer>=0)close(peer);peer=-1;
    memset(&core_config,0,sizeof(core_config));strcpy(core_config.device_id,"test_panel");
    core_alive=1;core_exit_code=0;core_last_request=0;core_retry=0;core_query_at=0;core_early_query=0;core_online=0;
    for(int i=0;i<2;i++){core_values[i]=-1;core_seen[i]=0;core_wait_query[i]=0;core_targets[i]=-1;core_target_until[i]=0;}
    opens=sends=closes=profile_checks=busy=fail_send=0;profile_ok=1;output_used=0;output[0]=0;gestures_cancel_all();
}
static void rx(unsigned char op,unsigned char value,unsigned char key){unsigned char f[]={0xfc,0,0,op,value,key,0xfe};assert(write(peer,f,7)==7);core_receive();}
static void feedback(void){rx(0xa1,0,0);rx(0xa2,0,0);assert(core_online);}
static void startup_frames(int from){for(int b=0;b<4;b++)assert(frames[from+b][2]==0x0b&&g10_key(frames[from+b][5])==b+1);assert(frames[from+4][3]==0xaa);}
static void write_config(const char *path,const char *value){FILE *f=fopen(path,"w");assert(f);assert(fputs(value,f)>=0);assert(!fclose(f));assert(!chmod(path,0600));}
static void configs(void){
    char path[]="/tmp/wallpanel-config-XXXXXX";int fd=mkstemp(path);assert(fd>=0);close(fd);
    const char *valid="version=1\nprofile=g10\ndevice_id=test_panel\nbuttons=1\nrelays=1\nbutton_1_single=1\n";
    struct core_config result={0};write_config(path,valid);assert(!core_config_read(path,&result));assert(result.buttons&&result.relays&&result.bindings[0][0]==1);
    assert(!chmod(path,0644));assert(core_config_read(path,&result));assert(!chmod(path,0600));
    char link[256];snprintf(link,sizeof(link),"%s-link",path);assert(!symlink(path,link));assert(core_config_read(link,&result));unlink(link);
    const char *bad[]={
        "version=1\nprofile=unknown\ndevice_id=a\nbuttons=1\nrelays=1\n",
        "version=1\nprofile=g10\ndevice_id=a;reboot\nbuttons=1\nrelays=1\n",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=1\nrelays=1",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=11\nrelays=1\n",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=1\nrelays=1\nrelays=0\n",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=1\nrelays=1\nhost=mqtt\n",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=1\nrelays=1\nbutton_4_double=3\n",
        "version=1\nprofile=g10\ndevice_id=\nbuttons=1\nrelays=1\n",
        "version=2\nprofile=g10\ndevice_id=a\nbuttons=1\nrelays=1\n",
        "version=1\nprofile=g10\ndevice_id=a\nbuttons=1\n"
    };
    for(size_t i=0;i<sizeof(bad)/sizeof(bad[0]);i++){write_config(path,bad[i]);assert(core_config_read(path,&result));assert(!strcmp(result.device_id,"test_panel"));}
    unlink(path);
}
int main(void){
    configs();reset();core_tick(g10_ms());assert(!opens&&!profile_checks&&!sends); // Disabled means no hardware access.
    core_config.buttons=core_config.relays=1;profile_ok=0;core_tick(g10_ms());assert(!opens&&!sends&&!core_alive&&core_exit_code==6);
    reset();core_config.buttons=core_config.relays=1;busy=1;core_tick(g10_ms());assert(opens==1&&!sends);
    busy=0;core_retry=0;core_tick(g10_ms());assert(opens==2&&sends==5);startup_frames(0);feedback();
    for(int b=1;b<=4;b++){int id=b<=2?b:b+2;rx(0,1,(unsigned char)id);gestures_tick_at(g10_ms()+401);char needle[64];snprintf(needle,sizeof(needle),"\"button\":%d,\"event\":\"press\"",b);assert(strstr(output,needle));}
    rx(0,1,1);rx(0,1,1);assert(strstr(output,"\"event\":\"double_press\""));
    int before=sends;
    const char *invalid[]={"v1 1 relay 3 1","v1 1 relay 1 2","v1 1 relay 1 1;reboot","v1 01 relay 1 1","v1 -1 relay 1 1","v2 1 relay 1 1","v1 1 relay 1 -0","v1 1 led 0","v1 1 relay 1 1 trailing"};
    for(size_t i=0;i<sizeof(invalid)/sizeof(invalid[0]);i++)core_command(invalid[i]);assert(sends==before);
    core_command("v1 1 relay 1 1");assert(sends==before+1&&frames[sends-1][3]==0xa1&&frames[sends-1][4]==1&&core_values[0]==0);
    core_command("v1 1 relay 1 1");assert(sends==before+1);assert(strstr(output,"duplicate_or_old_request"));
    rx(0xa1,1,0);assert(core_values[0]==0); // No optimistic state or TX echo.
    core_tick(g10_ms()+501);assert(frames[sends-1][3]==0xaa);rx(0xa1,1,0);assert(core_values[0]==1);
    core_config.bindings[0][0]=1;rx(0,1,1);gestures_tick_at(g10_ms()+401);assert(frames[sends-1][3]==0xa1&&frames[sends-1][4]==0);assert(core_targets[0]==0);
    rx(0,1,1);gestures_tick_at(g10_ms()+401);assert(frames[sends-1][3]==0xa1&&frames[sends-1][4]==1); // Pending-target toggle, no broker needed.
    core_config.relays=0;before=sends;core_command("v1 2 relay 2 1");rx(0,1,1);gestures_tick_at(g10_ms()+401);assert(sends==before);
    core_config.buttons=0;output_used=0;output[0]=0;rx(0,1,2);gestures_tick_at(g10_ms()+401);assert(!strstr(output,"\"type\":\"button\""));
    core_config.buttons=core_config.relays=1;fail_send=1;core_command("v1 3 relay 2 1");assert(!core_online&&core_uart.fd==-1&&core_targets[0]==-1&&!gesture_pending[1].deadline);
    close(peer);peer=-1;fail_send=0;before=sends;core_retry=0;core_tick(g10_ms());assert(sends==before+5);startup_frames(before);
    feedback();core_seen[0]=g10_ms()-46000;core_tick(g10_ms());assert(!core_online);before=sends;core_command("v1 4 relay 1 1");assert(sends==before);
    core_disconnect();close(peer);
    core_leased=1;unlink(WP_BINDINGS_PATH);before=sends;core_command("v1 5 bind 1 0 2");
    assert(sends==before&&core_config.bindings[0][0]==0&&core_config.bindings[0][1]==2);
    memset(core_config.bindings,0,sizeof(core_config.bindings));assert(!core_bindings_load()&&core_config.bindings[0][1]==2);
    core_command("v1 5 bind 1 1 1");assert(core_config.bindings[0][1]==2); // Dedup applies to configuration too.
    core_command("v1 6 bind 5 1 1");assert(sends==before);unlink(WP_BINDINGS_PATH);
    core_command("v1 6 modules 0 0");assert(!core_config.buttons&&!core_config.relays&&core_uart.fd==-1&&sends==before);
    core_command("v1 7 relay 1 1");assert(sends==before);
    core_command("v1 8 modules 1 1");core_retry=0;core_tick(g10_ms());assert(sends==before+5);startup_frames(before);core_disconnect();close(peer);
    puts("PASS: strict/atomic private config; disabled/no-owner/unknown-profile guards; startup/reconnect only scene/query; four single/double buttons; command validation/dedup; relay feedback and echo gate; offline local bindings; disabled modules; UART failure/stale-state cleanup; no network or shell.");return 0;
}
