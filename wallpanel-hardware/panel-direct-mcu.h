/* Native module adapter for the existing panel bridge. No vendor Java IPC,
 * package/service launch, raw shell commands, relay replay or network server.
 * Only the current G10/PX30 layout is accepted. The four verified event-mode
 * settings replace the Tuya app's button setup; never restore relay outputs.
 */
#include <sys/un.h>
#include <sys/wait.h>
#include "g10_mcu.h"
#include "g10_uart.h"
#include "g10_profile.h"
#include "mcu-lease.h"
#ifndef WP_UART_OPEN
#define WP_UART_OPEN g10_uart_open
#define WP_UART_SEND g10_uart_send
#define WP_UART_CLOSE g10_uart_close
#define WP_PROFILE_VALID g10_profile_valid
#endif
static const char *module_direction="/proc/vendor/485_tx_mode";
static struct g10_uart module_uart={.fd=-1};
static struct g10_parser module_parser;
static int module_server=-1,module_client=-1;
static int module_values[2]={-1,-1},module_online,module_availability_last=-1;
static int module_external;
static long long module_relay_at[2],module_query_at,module_start_at;
static long long module_early_query;
static int module_await_query[2];
#ifndef WP_MCU_STATUS_PATH
#define WP_MCU_STATUS_PATH "/dev/wallpanel-mcu-status.json"
#endif
static int module_status_last[3]={-2,-2,-2};
static void module_status(void){
    if(module_status_last[0]==module_online&&module_status_last[1]==module_values[0]&&module_status_last[2]==module_values[1])return;
    int fd=open(WP_MCU_STATUS_PATH ".new",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);
    if(fd<0)return;
    FILE *f=fdopen(fd,"w");if(!f){close(fd);return;}
    fprintf(f,"{\"transport\":\"direct-uart\",\"pid\":%d,\"online\":%s,\"relays\":[%d,%d],\"button_edges\":false,\"button_hold\":false}\n",getpid(),module_online?"true":"false",module_values[0],module_values[1]);
    int bad=fflush(f);if(fclose(f))bad=1;
    if(!bad&&!rename(WP_MCU_STATUS_PATH ".new",WP_MCU_STATUS_PATH)){
        module_status_last[0]=module_online;module_status_last[1]=module_values[0];module_status_last[2]=module_values[1];
    }
}
static void module_disconnect(void){
    for(int b=1;b<=4;b++)gesture_cancel(b);
    local_module_disconnected();
    if(WP_UART_CLOSE(&module_uart,module_direction))fprintf(stderr,"Native MCU cleanup failed\n");
    module_client=-1;module_online=0;
    module_values[0]=module_values[1]=-1;
    module_relay_at[0]=module_relay_at[1]=0;
    module_await_query[0]=module_await_query[1]=0;module_early_query=0;
    memset(&module_parser,0,sizeof(module_parser));
    if(!dry_run)module_status();
}
static int module_init(void){
    if(dry_run){module_values[0]=module_values[1]=0;return 0;}
    if(!WP_PROFILE_VALID()){fprintf(stderr,"Independent MCU: unsupported G10 hardware layout\n");return -1;}
    unlink(WP_MCU_STATUS_PATH);module_status();
    puts("Independent G10 MCU adapter selected; no Tuya app/service dependency.");
    return 0;
}
static void module_event(int id){
    int button=g10_key((unsigned char)id);if(!button)return;
    int local=local_button_enabled(button);
    if(net<0&&!local)return;
    long long now=ms();
    if(gesture_pending[button].deadline&&now>=gesture_pending[button].deadline)gesture_flush(button);
    if(!gesture_pending[button].deadline)local_suppress_ha[button]=0;
    if(net<0)local_suppress_ha[button]=1;
    gesture_pulse_at(button,(unsigned int)id,now);
}
static void module_callback(void *context,const struct g10_event *e){
    (void)context;
    if(e->kind==G10_RELAY_STATE){
        int i=e->channel-1;
        /* A write echoed by some RS485 adapters has the same frame as state.
         * Drain it before the next query, and keep prior feedback until then. */
        if(module_await_query[i])return;
        module_values[i]=e->value;module_relay_at[i]=ms();
        local_relay_feedback(i,e->value);
        module_online=module_values[0]>=0&&module_values[1]>=0&&
            ms()-module_relay_at[0]<45000&&ms()-module_relay_at[1]<45000;
    }else if(e->kind==G10_BUTTON_CLICK)module_event(e->value);
}
static int module_send_frame(const unsigned char frame[7]){
    if(module_client<0)return -1;
    if(WP_UART_SEND(&module_uart,module_direction,frame)){
        module_disconnect();return -1;
    }
    return 0;
}
static void module_receive(void){
    if(module_client<0)return;
    for(int chunks=0;chunks<16;chunks++){
        struct pollfd p={.fd=module_client,.events=POLLIN};
        int ready=poll(&p,1,0);
        if(ready<0){if(errno!=EINTR)module_disconnect();return;}
        if(p.revents&(POLLERR|POLLHUP|POLLNVAL)){module_disconnect();return;}
        if(!(p.revents&POLLIN))return;
        unsigned char buffer[256];ssize_t n=read(module_client,buffer,sizeof(buffer));
        if(n>0)g10_feed(&module_parser,buffer,(size_t)n,module_callback,NULL);
        else {if(n<0&&errno!=EAGAIN&&errno!=EINTR)module_disconnect();return;}
    }
}
static void module_tick(void){
    if(dry_run)return;
    if(wp_lease_held()){
        if(!module_external){module_disconnect();module_external=1;if(net>=0)publish(BASE"/module/availability","offline",1);}
        return;
    }
    if(module_external){
        module_external=0;module_start_at=ms()-3000;module_availability_last=-1;
        relay_last[0]=relay_last[1]=-1;local_module_resumed();
    }
    long long now=ms();
    if(module_client<0){
        if(now-module_start_at<3000)return;
        module_start_at=now;
        if(WP_UART_OPEN(&module_uart,"/dev/ttyS2",module_direction)){
            fprintf(stderr,"Independent MCU waiting for exclusive UART access (errno=%d)\n",errno);return;
        }
        module_client=module_uart.fd;module_query_at=0;
        memset(&module_parser,0,sizeof(module_parser));
        module_values[0]=module_values[1]=-1;
        module_relay_at[0]=module_relay_at[1]=0;module_online=0;
        /* These exact four non-actuating settings were previously applied and
         * verified on this panel. They prevent factory direct-relay bindings
         * from competing with our saved offline button assignments after boot. */
        for(int button=1;button<=4;button++){
            unsigned char config[7];g10_scene(config,button);
            if(module_send_frame(config))return;
        }
        puts("Independent MCU UART acquired; reading actual state without restoring relay outputs.");
    }
    module_receive();
    if(module_client>=0&&(!module_query_at||now-module_query_at>=20000||(module_early_query&&now>=module_early_query))){
        unsigned char frame[7];g10_query(frame);module_query_at=now;
        if(!module_send_frame(frame)){
            module_await_query[0]=module_await_query[1]=0;module_early_query=0;
        }
    }
    if(module_online&&(now-module_relay_at[0]>=45000||now-module_relay_at[1]>=45000)){
        module_online=0;module_values[0]=module_values[1]=-1;
        local_module_disconnected();
        for(int b=1;b<=4;b++)gesture_cancel(b);
    }
    module_status();
}
static int module_publish_availability(void){
    if(module_external)return 0;
    if(module_online==module_availability_last)return 0;
    if(publish(BASE"/module/availability",module_online?"online":"offline",1))return -1;
    module_availability_last=module_online;return 0;
}
static int module_relay_command(int channel,int value){
    if(module_external||wp_lease_held())return -1;
    unsigned char frame[7];
    if(!module_online||g10_relay(frame,channel,value))return -1;
    module_await_query[channel-1]=1;
    if(module_send_frame(frame))return -1;
    /* Commands are not state feedback. Query shortly after the write; never
     * retry/replay relay writes on reconnect or restore their previous state. */
    long long due=ms()+500;
    if(!module_early_query||due<module_early_query)module_early_query=due;
    printf("Independent MCU relay %d command %s sent; waiting for query feedback.\n",channel,value?"ON":"OFF");
    return 0;
}
