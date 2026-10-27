#!/usr/bin/env python3
"""Exercise the real C backend against a deterministic Android-protocol peer on Linux."""
import os,pathlib,queue,socket,struct,subprocess,tempfile,threading,time
root=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='wpar-audio-test-') as td:
 d=pathlib.Path(td)
 (d/'audio.h').write_text((root/'build/native-work/shairport-sync-5.6.1/audio.h').read_text())
 (d/'libconfig.h').write_text('/* not needed by this harness */\n')
 (d/'common.h').write_text('''#include <stdint.h>
#define SPS_FORMAT_S16_LE 1
#define SPS_RATE_48000 0
#define FORMAT_FROM_ENCODED_FORMAT(x) ((x)&15)
#define CHANNELS_FROM_ENCODED_FORMAT(x) (((x)>>4)&15)
#define RATE_FROM_ENCODED_FORMAT(x) ((x)>>8)
struct settings { double audio_backend_buffer_desired_length; };
extern struct settings config;
void parse_audio_options(const char*,uint32_t,uint32_t,uint32_t);
void die(const char*,...);
uint64_t get_absolute_time_in_ns(void);
''')
 (d/'backend.c').write_text(pathlib.Path(os.environ.get('WPAR_TEST_BACKEND',root/'native/wpar-android-output.c')).read_text())
 (d/'harness.c').write_text('''#include "backend.c"
#include <stdio.h>
void die(const char *format,...){fprintf(stderr,"%s",format);exit(2);}
struct settings config;
void parse_audio_options(const char*a,uint32_t b,uint32_t c,uint32_t d){(void)a;(void)b;(void)c;(void)d;}
int32_t search_for_suitable_configuration(unsigned int a,unsigned int b,unsigned int c,int(*f)(unsigned int,unsigned int,unsigned int)){(void)a;(void)b;(void)c;(void)f;return (48000<<8)|(2<<4)|1;}
uint64_t get_absolute_time_in_ns(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC_RAW,&t);return (uint64_t)t.tv_sec*1000000000+t.tv_nsec;}
static void *stress_writer(void *unused){
 (void)unused;unsigned char b[4800*4];memset(b,0x55,sizeof(b));
 for(int i=0;i<150;i++)play(b,4800,1,123,987654321);
 return NULL;
}
int main(void){
 setvbuf(stdout,NULL,_IOLBF,0);
 /* Real Shairport startup passes -1 here, not the synthetic zero used before. */
 if(init(-1,NULL)!=0 || config.audio_backend_buffer_desired_length!=0.20){fprintf(stderr,"backend initialization skipped");return 3;}
 int result=prepare();printf("READY %d\\n",result);if(result)return 0;
 char cmd[80];while(fgets(cmd,sizeof(cmd),stdin)){
  int n;if(sscanf(cmd,"send %d",&n)==1){unsigned char b[32768*4];memset(b,0x55,sizeof(b));printf("S %d\\n",play(b,n,1,123,987654321));}
  else if(!strncmp(cmd,"race",4)){pthread_t writer;pthread_create(&writer,NULL,stress_writer,NULL);for(int i=0;i<150;i++){flush();usleep(50);}pthread_join(writer,NULL);puts("R 0");}
  else if(!strncmp(cmd,"delay",5)){long v=-1;int r=delay(&v);printf("D %d %ld %.3f\\n",r,v,wpar_android_latency_offset(0));}
  else if(!strncmp(cmd,"flush",5)){flush();printf("F\\n");}
  else if(!strncmp(cmd,"stats",5)){uint64_t r,c,q,s;int result=stats(&r,&c,&q,&s);printf("T %d %llu %llu\\n",result,(unsigned long long)q,(unsigned long long)s);}
  else break;
 }disconnect_android();return 0;
}
''')
 subprocess.run(['gcc','-std=c11','-Wall','-Wextra','-Werror','-I',str(d),str(d/'harness.c'),'-pthread','-lm','-o',str(d/'harness')],check=True)
 name='wpar-test-'+str(os.getpid());server=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);server.bind('\0'+name);server.listen();server.settimeout(3)
 env=dict(os.environ,WPAR_AUDIO_SOCKET=name)
 p=subprocess.Popen([str(d/'harness')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,env=env)
 peer,_=server.accept();events=queue.Queue()
 def exact(n):
  b=b''
  while len(b)<n:
   part=peer.recv(n-len(b))
   if not part:raise EOFError()
   b+=part
  return b
 def receive():
  try:
   while True:
    h=struct.unpack('>IIQIIIIQ',exact(40));assert h[0]==0x57504131 and h[1] in (1,2,3) and h[3]<=32768,'interleaved audio header';payload=exact(h[3]*4) if h[1]==2 else b'';events.put((h,payload))
  except (EOFError,OSError):pass
 thread=threading.Thread(target=receive,daemon=True);thread.start()
 def command(s):p.stdin.write(s+'\n');p.stdin.flush();return p.stdout.readline().strip().split()
 def feedback(gen,played,accepted,flags=1,offset=25,age=0):
  now=time.monotonic_ns()-age
  peer.sendall(struct.pack('>IQQQQQQIIIId',0x57504631,gen,played,now,played,now,accepted,0,flags,offset&0xffffffff,0,48000.0));time.sleep(.01)
 assert p.stdout.readline().strip()=='READY 0'
 h,_=events.get(timeout=2);assert h[0]==0x57504131 and h[1]==1;gen=h[2]
 assert command('delay')[1]!='0','no invented startup delay'
 assert command('send 4800')==['S','0'];h,payload=events.get(timeout=2)
 assert h[3]==4800 and h[6]==1 and h[7]==987654321 and payload==b'\x55'*(4800*4)
 feedback(gen,2400,3200)
 r=command('delay');assert r[1]=='0' and 1200<int(r[2])<=2400 and r[3]=='0.025',r
 assert command('send 480')==['S','0'];h,_=events.get(timeout=2);assert h[6]>>16==25,'live offset observed in outgoing PCM'
 assert command('send 20000')==['S','0']
 chunks=[events.get(timeout=2) for _ in range(3)]
 assert [x[0][3] for x in chunks]==[8192,8192,3616]
 assert chunks[1][0][7]==987654321+8192*1_000_000_000//48000
 feedback(gen,2400,3200,age=1_000_000_000)
 assert command('delay')[1]!='0','stale feedback rejected'
 assert command('flush')==['F'];h,_=events.get(timeout=2);assert h[1]==3 and h[2]>gen
 new_gen=h[2];feedback(gen,1000,3200)
 assert command('delay')[1]!='0','old generation rejected'
 feedback(new_gen,0,0,flags=2,offset=-15)
 r=command('delay');assert r[1:] == ['0','0','-0.015'],r
 assert command('stats')[1]!='0','head estimate is not a valid hardware clock statistic'
 feedback(new_gen,0,0,flags=0)
 assert command('delay')[1]!='0','invalid counter cannot discipline playback'
 assert command('race')==['R','0']
 current_gen=new_gen
 for _ in range(300):
  packet,payload=events.get(timeout=3)
  if packet[1]==3:
   assert packet[2]>current_gen;current_gen=packet[2]
  else:
   assert packet[1]==2 and packet[2]==current_gen and packet[3]==4800
   assert payload==b'\x55'*(4800*4),'flush bytes must never enter PCM payload'
 peer.sendall(b'BAD!'+b'\0'*72);time.sleep(.02)
 assert command('delay')[1]!='0','malformed peer invalidates feedback'
 assert command('send 480')==[],'broken transport must stop the receiver, not silently discard audio'
 p.wait(timeout=2);assert p.returncode==2
 peer.close();server.close()
 print('Native backend protocol tests passed')
