/* Exercise the actual MQTT/gesture/local-relay code with a fake native UART. */
#define _GNU_SOURCE
#define WP_LEASE_PATH "/tmp/wallpanel-native-test-lease"
#define WP_LEASE_UID geteuid()
#undef NDEBUG
#include <assert.h>
#include <errno.h>
#include <sys/socket.h>
#include "g10_uart.h"
static int peer=-1,opens,sends,fail_send,profile_ok=1;
static int fake_profile(void){return profile_ok;}
static int fake_open(struct g10_uart*u,const char*d,const char*n){
    (void)d;(void)n;int pair[2];assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0,pair));
    u->fd=pair[0];peer=pair[1];opens++;return 0;
}
static int fake_send(struct g10_uart*u,const char*n,const unsigned char frame[7]){
    (void)n;assert(g10_allowed(frame));if(fail_send){errno=EIO;return -1;}
    sends++;return send(u->fd,frame,7,MSG_NOSIGNAL)==7?0:-1;
}
static int fake_close(struct g10_uart*u,const char*n){(void)n;if(u->fd>=0)close(u->fd);u->fd=-1;return 0;}
#define WALLPANEL_DIRECT_MCU 1
#define WP_UART_OPEN fake_open
#define WP_UART_SEND fake_send
#define WP_UART_CLOSE fake_close
#define WP_PROFILE_VALID fake_profile
#define WP_MCU_STATUS_PATH "/tmp/wallpanel-native-test-status.json"
#define main bridge_main
#include "../panel-172.16.1.26/panel-mqtt.c"
#undef main
static int contains(int fd,const char *word){char b[4096];ssize_t n=recv(fd,b,sizeof(b),MSG_DONTWAIT);return n>0&&memmem(b,n,word,strlen(word));}
static int empty(int fd){char b;return recv(fd,&b,1,MSG_DONTWAIT)<0&&errno==EAGAIN;}
static void command(unsigned char op,unsigned char value,unsigned char key){unsigned char b[]={0xfc,0,0,op,value,key,0xfe};assert(send(peer,b,7,MSG_NOSIGNAL)==7);module_receive();}
int main(void){
    int broker[2];assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK,0,broker));net=broker[0];
    profile_ok=0;assert(module_init()<0&&!opens);profile_ok=1;assert(!module_init()&&!opens&&!sends);
    module_start_at=ms()-3000;module_tick();assert(opens==1&&sends==5&&!module_online);
    unsigned char q[7];
    for(int b=1;b<=4;b++)assert(recv(peer,q,7,0)==7&&q[2]==0x0b&&g10_key(q[5])==b);
    assert(recv(peer,q,7,0)==7&&q[3]==0xaa&&q[4]==0xff); /* No startup relay writes. */
    command(0xa1,0,0);assert(!module_online);command(0xa2,1,0);assert(module_online);
    unlink(WP_LEASE_PATH);int lease=wp_lease_acquire();assert(lease>=0&&wp_lease_held());assert(wp_lease_acquire()<0);
    gesture_pulse_at(0,KEY_FN_F3,ms());gesture_pulse_at(1,1,ms());
    int gate_before=sends;module_tick();assert(module_external&&module_client<0&&!module_online&&sends==gate_before);
    assert(gesture_pending[0].deadline&&!gesture_pending[1].deadline);gesture_cancel(0); // Knob remains independent.
    assert(module_relay_command(1,1)<0);assert(!module_publish_availability());assert(!publish_relays());
    local_command(BASE"/button/1/binding/set","1,0",0);assert(!local_bindings[0][0]);
    close(peer);peer=-1;close(lease);assert(!wp_lease_held());unlink(WP_LEASE_PATH);module_tick();
    assert(!module_external&&module_client>=0&&sends==gate_before+5);
    for(int b=1;b<=4;b++)assert(recv(peer,q,7,0)==7&&q[2]==0x0b);
    assert(recv(peer,q,7,0)==7&&q[3]==0xaa);command(0xa1,0,0);command(0xa2,1,0);
    while(!empty(broker[1])){char drain[4096];recv(broker[1],drain,sizeof(drain),MSG_DONTWAIT);}
    for(int button=1;button<=4;button++){
        int key=button==1?1:button==2?2:button==3?5:6;
        command(0,1,key);assert(empty(broker[1]));gestures_tick_at(ms()+DOUBLE_PRESS_MS+1);
        char expected[64];snprintf(expected,sizeof(expected),"button_%d_press",button);assert(contains(broker[1],expected));
    }
    command(0,1,1);command(0,1,1);assert(contains(broker[1],"button_1_double_press"));
    gestures_tick_at(ms()+DOUBLE_PRESS_MS+1);assert(empty(broker[1]));
    int before=sends;
    relay_command(BASE"/relay/1/set","ON",1);relay_command(BASE"/relay/3/set","ON",0);relay_command(BASE"/relay/1/set","TOGGLE",0);assert(sends==before);
    relay_command(BASE"/relay/1/set","ON",0);assert(sends==before+1&&module_values[0]==0);
    assert(recv(peer,q,7,0)==7&&q[3]==0xa1&&q[4]==1);
    command(0xa1,1,0);assert(module_values[0]==0); /* Ignore possible TX echo. */
    module_early_query=ms();module_tick();assert(recv(peer,q,7,0)==7&&q[3]==0xaa);
    command(0xa1,1,0);assert(module_values[0]==1);
    /* Offline local shortcut still works, based on actual MCU feedback. */
    net=-1;local_bindings[0][0]=1;command(0,1,1);gestures_tick_at(ms()+DOUBLE_PRESS_MS+1);
    assert(recv(peer,q,7,0)==7&&q[3]==0xa1&&q[4]==0);assert(local_target_until[0]>0);
    fail_send=1;assert(module_relay_command(2,0)<0);
    assert(!module_online&&module_client==-1&&module_values[0]==-1&&module_values[1]==-1);
    assert(!local_target_until[0]&&local_targets[0]==-1);close(peer);peer=-1;
    before=sends;fail_send=0;module_start_at=ms()-3000;module_tick();assert(sends==before+5);
    for(int b=1;b<=4;b++)assert(recv(peer,q,7,0)==7&&q[2]==0x0b&&g10_key(q[5])==b);
    assert(recv(peer,q,7,0)==7&&q[3]==0xaa); /* Reconnect cannot replay relay write. */
    command(0xa1,0,0);command(0xa2,0,0);module_relay_at[0]=ms()-46000;module_tick();assert(!module_online);
    assert(module_relay_command(1,1)<0);module_disconnect();close(peer);
    close(broker[0]);close(broker[1]);unlink(WP_MCU_STATUS_PATH);unlink(WP_MCU_STATUS_PATH ".new");
    puts("PASS: startup/reconnect only verified event-mode setup and status query; all four single/double events; no retained/invalid relay commands, TX echo or relay replay; offline local relay; stale-target cancellation; per-relay freshness; no Tuya IPC.");return 0;
}
