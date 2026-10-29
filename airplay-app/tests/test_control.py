"""Host-only production native controller tests with a fake upstream connection."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='wpar-control-') as folder:
    temp=Path(folder);(temp/'remote').mkdir()
    (temp/'rtsp.h').write_text('''#include <pthread.h>
#include <sys/types.h>
enum {ap_1,ap_2};typedef struct {int airplay_type,connection_number,event_channel_fd,ap2_rate;pthread_mutex_t event_sender_mutex,flush_mutex;} rtsp_conn_info;
extern pthread_rwlock_t principal_conn_lock;extern rtsp_conn_info *principal_conn;
''')
    (temp/'remote/remote.h').write_text('enum {rcsc_play=0,rcsc_pause=1,rcsc_play_pause=2,rcsc_next_item=4,rcsc_previous_item=5};')
    harness='''#define _GNU_SOURCE
#include <unistd.h>
static uid_t allowed_uid;static uid_t test_uid(void){return allowed_uid;}
#define getuid test_uid
#include "CONTROL"
#undef getuid
#include <assert.h>
pthread_rwlock_t principal_conn_lock=PTHREAD_RWLOCK_INITIALIZER;rtsp_conn_info *principal_conn;
static int calls,last_command,rejected;
ssize_t ap2_event_send_simple_modern_media_remote_command(rtsp_conn_info *c,unsigned command){(void)c;calls++;last_command=(int)command;return rejected?-1:1;}
static void test(const char *request,const char *expected){int pair[2];assert(!socketpair(AF_UNIX,SOCK_STREAM,0,pair));assert(write(pair[0],request,strlen(request))==(ssize_t)strlen(request));handle(pair[1]);close(pair[1]);char result[128]={0};ssize_t count=read(pair[0],result,sizeof(result)-1);assert(count>=0);if(strcmp(result,expected)){fprintf(stderr,"request=%s expected=%s got=%s",request,expected,result);abort();}close(pair[0]);}
int main(void){allowed_uid=getuid();
test("STATUS\\n","ERR NO_SESSION\\n");
rtsp_conn_info conn={.airplay_type=ap_2,.connection_number=7,.event_channel_fd=1,.ap2_rate=1,.event_sender_mutex=PTHREAD_MUTEX_INITIALIZER,.flush_mutex=PTHREAD_MUTEX_INITIALIZER};principal_conn=&conn;
test("STATUS\\n","OK AP2 7 playing\\n");conn.ap2_rate=0;test("STATUS\\n","OK AP2 7 paused\\n");
test("PLAY 7\\n","OK ACK 7\\n");assert(last_command==0);test("PAUSE 7\\n","OK ACK 7\\n");assert(last_command==1);test("TOGGLE 7\\n","OK ACK 7\\n");assert(last_command==2&&calls==3);
test("NEXT 7\\n","OK ACK 7\\n");assert(last_command==4);test("PREVIOUS 7\\n","OK ACK 7\\n");assert(last_command==5&&calls==5);calls=3;
test("PLAY 8\\n","ERR STALE\\n");test("PLAY -1\\n","ERR REQUEST\\n");test("PLAY 999999999999999999999999999999999999999999\\n","ERR REQUEST\\n");test("STOP 7\\n","ERR REQUEST\\n");test("PLAY 7;anything\\n","ERR REQUEST\\n");test("STATUS extra\\n","ERR REQUEST\\n");assert(calls==3);
rejected=1;test("PAUSE 7\\n","ERR REJECTED\\n");assert(calls==4);
conn.event_channel_fd=-1;test("STATUS\\n","ERR UNAVAILABLE\\n");conn.event_channel_fd=1;
pthread_mutex_lock(&conn.event_sender_mutex);test("PLAY 7\\n","ERR BUSY\\n");pthread_mutex_unlock(&conn.event_sender_mutex);
pthread_mutex_lock(&conn.flush_mutex);test("STATUS\\n","ERR BUSY\\n");pthread_mutex_unlock(&conn.flush_mutex);
pthread_rwlock_wrlock(&principal_conn_lock);test("PLAY 7\\n","ERR BUSY\\n");pthread_rwlock_unlock(&principal_conn_lock);
conn.airplay_type=ap_1;test("PLAY 7\\n","ERR UNSUPPORTED\\n");assert(calls==4);
allowed_uid++;test("PLAY 7\\n","");assert(calls==4);
puts("PASS: native source-control authentication, allowlist, session and lock checks; no real AirPlay source used");return 0;}
'''.replace('CONTROL',str(root/'native/wpar-control.c'))
    (temp/'test.c').write_text(harness)
    subprocess.run(['gcc','-Wall','-Wextra','-Werror','-pthread','-I',str(temp),str(temp/'test.c'),'-o',str(temp/'test')],check=True)
    subprocess.run([str(temp/'test')],check=True,timeout=10)
