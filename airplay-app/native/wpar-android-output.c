/* Android app-private socket backend. PCM and feedback stay at the app UID. */
#define _GNU_SOURCE
#include "audio.h"
#include "common.h"
#include <errno.h>
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define PCM_MAGIC 0x57504131u
#define FEEDBACK_MAGIC 0x57504631u
#define FEEDBACK_SIZE 76
static int android_fd = -1;
static pthread_t feedback_thread;
static int feedback_thread_started;
static pthread_mutex_t android_mutex = PTHREAD_MUTEX_INITIALIZER;
/* Upstream AP2 reset_buffer can flush from its processor thread while the
 * player writes PCM. Protect a whole header+payload and its generation. */
static pthread_mutex_t transport_mutex = PTHREAD_MUTEX_INITIALIZER;
static uint64_t generation, sent_frames;
static unsigned int android_rate = 48000;
static int feedback_valid, feedback_flags, feedback_offset_ms, offset_valid;
static uint64_t feedback_generation, feedback_played, feedback_mono, feedback_accepted;
static uint32_t feedback_underruns, stats_underruns;
static double feedback_rate = 48000;
static char *socket_name;

static uint32_t u32(const unsigned char *p) {
  return ((uint32_t)p[0]<<24)|((uint32_t)p[1]<<16)|((uint32_t)p[2]<<8)|p[3];
}
static uint64_t u64(const unsigned char *p) { return ((uint64_t)u32(p)<<32)|u32(p+4); }
static void put32(unsigned char *p, uint32_t v) { p[0]=v>>24;p[1]=v>>16;p[2]=v>>8;p[3]=v; }
static void put64(unsigned char *p, uint64_t v) { put32(p,v>>32);put32(p+4,v); }
static uint64_t mono_now(void) {
  struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t);
  return (uint64_t)t.tv_sec*1000000000u+t.tv_nsec;
}
static int read_all(int s, unsigned char *p, size_t n) {
  while(n) { ssize_t k=recv(s,p,n,0);if(k<0&&errno==EINTR)continue;if(k<=0)return -1;p+=k;n-=k; }return 0;
}
static int send_all(int s, const void *data, size_t n) {
  const unsigned char *p=data;
  while(n) { ssize_t k=send(s,p,n,MSG_NOSIGNAL);if(k<0&&errno==EINTR)continue;if(k<=0)return -EIO;p+=k;n-=k; }return 0;
}
static int message(int type, uint32_t frames, uint32_t rtp, uint32_t kind, uint64_t playtime) {
  unsigned char p[40];put32(p,PCM_MAGIC);put32(p+4,type);put64(p+8,generation);
  put32(p+16,frames);put32(p+20,android_rate);put32(p+24,rtp);put32(p+28,kind);put64(p+32,playtime);
  return send_all(android_fd,p,sizeof(p));
}
static void *read_feedback(void *unused) {
  (void)unused;unsigned char p[FEEDBACK_SIZE];int s=android_fd;
  while(read_all(s,p,sizeof(p))==0) {
    if(u32(p)!=FEEDBACK_MAGIC)break;
    uint64_t gen=u64(p+4), played=u64(p+12), when=u64(p+20), accepted=u64(p+44);
    int flags=(int)u32(p+56), offset=(int32_t)u32(p+60);
    uint64_t rate_bits=u64(p+68);double rate;memcpy(&rate,&rate_bits,sizeof(rate));
    if(played>accepted || offset< -500 || offset>500 || !isfinite(rate) || rate<47000 || rate>49000)break;
    pthread_mutex_lock(&android_mutex);
    if(gen==generation) {
      feedback_generation=gen;feedback_played=played;feedback_mono=when;feedback_accepted=accepted;
      feedback_flags=flags;feedback_offset_ms=offset;feedback_rate=rate;feedback_underruns=u32(p+52);
      offset_valid=1;feedback_valid=1;
    }
    pthread_mutex_unlock(&android_mutex);
  }
  pthread_mutex_lock(&android_mutex);feedback_valid=0;pthread_mutex_unlock(&android_mutex);
  shutdown(s,SHUT_RDWR); /* Unblock the PCM writer if feedback transport fails. */
  return NULL;
}
static void disconnect_android(void) {
  if(android_fd>=0)shutdown(android_fd,SHUT_RDWR);
  if(feedback_thread_started){pthread_join(feedback_thread,NULL);feedback_thread_started=0;}
  if(android_fd>=0){close(android_fd);android_fd=-1;}
  pthread_mutex_lock(&android_mutex);feedback_valid=0;pthread_mutex_unlock(&android_mutex);
}
static int prepare_locked(void) {
  disconnect_android();
  if(!socket_name || strlen(socket_name)>sizeof(((struct sockaddr_un*)0)->sun_path)-2)return -EINVAL;
  struct sockaddr_un address;memset(&address,0,sizeof(address));address.sun_family=AF_UNIX;
  memcpy(address.sun_path+1,socket_name,strlen(socket_name));
  for(int attempt=0;attempt<20;attempt++) {
    android_fd=socket(AF_UNIX,SOCK_STREAM,0);
    if(android_fd<0)return -errno;
    if(connect(android_fd,(struct sockaddr*)&address,sizeof(sa_family_t)+1+strlen(socket_name))==0)break;
    close(android_fd);android_fd=-1;usleep(50000);
  }
  if(android_fd<0)return -ENODEV;
  struct ucred peer;socklen_t size=sizeof(peer);
  if(getsockopt(android_fd,SOL_SOCKET,SO_PEERCRED,&peer,&size)||peer.uid!=getuid()){disconnect_android();return -EACCES;}
  struct timeval timeout={2,0};setsockopt(android_fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
  pthread_mutex_lock(&android_mutex);generation++;sent_frames=0;feedback_valid=0;pthread_mutex_unlock(&android_mutex);
  if(message(1,0,0,0,0)){disconnect_android();return -EIO;}
  if(pthread_create(&feedback_thread,NULL,read_feedback,NULL)){disconnect_android();return -EIO;}
  feedback_thread_started=1;
  return 0;
}
static int play_locked(void *buffer,int frames,int kind,uint32_t timestamp,uint64_t playtime) {
  if(android_fd<0 || frames<0)return -EINVAL;
  int offset=0;
  while(offset<frames) {
    int count=frames-offset;if(count>8192)count=8192;
    pthread_mutex_lock(&android_mutex);
    uint32_t live_kind=(uint32_t)kind | ((uint32_t)(uint16_t)(int16_t)feedback_offset_ms<<16);
    pthread_mutex_unlock(&android_mutex);
    uint64_t target=playtime ? playtime+(uint64_t)offset*1000000000u/android_rate : 0;
    if(message(2,(uint32_t)count,timestamp,live_kind,target) ||
       send_all(android_fd,(const unsigned char*)buffer+(size_t)offset*4,(size_t)count*4)) {
      return -EIO;
    }
    pthread_mutex_lock(&android_mutex);sent_frames+=count;pthread_mutex_unlock(&android_mutex);
    offset+=count;
  }
  return 0;
}
static void flush_locked(void) {
  if(android_fd<0)return;
  pthread_mutex_lock(&android_mutex);generation++;sent_frames=0;feedback_valid=0;pthread_mutex_unlock(&android_mutex);
  if(message(3,0,0,0,0))shutdown(android_fd,SHUT_RDWR);
}
static int prepare(void) {
  int old;pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,&old);
  pthread_mutex_lock(&transport_mutex);int result=prepare_locked();pthread_mutex_unlock(&transport_mutex);
  pthread_setcancelstate(old,NULL);
  if(result)die("Android audio connection failed (%d); output listener unavailable.",result);
  return result;
}
static int play(void *buffer,int frames,int kind,uint32_t timestamp,uint64_t playtime) {
  int old;pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,&old);
  pthread_mutex_lock(&transport_mutex);int result=play_locked(buffer,frames,kind,timestamp,playtime);pthread_mutex_unlock(&transport_mutex);
  pthread_setcancelstate(old,NULL);
  if(result==-EIO)die("Android audio transport failed; restart the receiver to reconnect.");
  return result;
}
static void flush(void) {
  int old;pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,&old);
  pthread_mutex_lock(&transport_mutex);flush_locked();pthread_mutex_unlock(&transport_mutex);
  pthread_setcancelstate(old,NULL);
}
static void stop(void) {
  int old;pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,&old);
  pthread_mutex_lock(&transport_mutex);disconnect_android();pthread_mutex_unlock(&transport_mutex);
  pthread_setcancelstate(old,NULL);
}
static int read_delay(uint64_t now,long *delay,uint64_t *sent,int *flags,uint32_t *underruns) {
  int result=-EAGAIN;
  pthread_mutex_lock(&android_mutex);
  if(feedback_valid && (feedback_flags&3) && feedback_generation==generation && now>=feedback_mono && now-feedback_mono<=250000000u) {
    double estimate=feedback_played;
    if(feedback_flags&1)estimate+=(now-feedback_mono)*feedback_rate/1e9;
    if(estimate>feedback_accepted)estimate=feedback_accepted;
    uint64_t played=(uint64_t)estimate;
    uint64_t queued=sent_frames>played?sent_frames-played:0;
    if(queued<=LONG_MAX){*delay=(long)queued;*sent=sent_frames;*flags=feedback_flags;*underruns=feedback_underruns;result=0;}
  }
  pthread_mutex_unlock(&android_mutex);return result;
}
static int delay(long *value) { uint64_t sent;int flags;uint32_t underruns;return read_delay(mono_now(),value,&sent,&flags,&underruns); }
static int stats(uint64_t *raw,uint64_t *corrected,uint64_t *queued,uint64_t *sent) {
  *raw=get_absolute_time_in_ns();*corrected=mono_now();long d;int flags;uint32_t underruns;
  int result=read_delay(*corrected,&d,sent,&flags,&underruns);
  if(result){*queued=0;*sent=0;return result;}
  *queued=(uint64_t)d;
  if(!(flags&1) || underruns!=stats_underruns){stats_underruns=underruns;return -EAGAIN;}
  return 0;
}
/* Called by RTP anchor conversion: local offset is an input to the existing native controller. */
double wpar_android_latency_offset(double fallback) {
  double result=fallback;pthread_mutex_lock(&android_mutex);
  if(offset_valid)result=feedback_offset_ms/1000.0;
  pthread_mutex_unlock(&android_mutex);return result;
}
static int init(int argc,char **argv) {
  (void)argv;
  /* Upstream passes argc == -1 when no backend-specific arguments follow --.
   * It ignores init return codes, so do not silently skip all initialization. */
  if(argc>0)die("Android audio backend does not accept command-line arguments.");
  socket_name=getenv("WPAR_AUDIO_SOCKET");
  if(!socket_name)die("Android audio backend requires WPAR_AUDIO_SOCKET.");
  config.audio_backend_buffer_desired_length=0.20;
  parse_audio_options("android",1<<SPS_FORMAT_S16_LE,1<<SPS_RATE_48000,1<<2);
  return 0;
}
static int32_t configuration(unsigned int channels,unsigned int rate,unsigned int format) {
  return search_for_suitable_configuration(channels,rate,format,NULL);
}
static int configure(int32_t encoded,char **map) {
  if(map)*map=NULL;
  if(FORMAT_FROM_ENCODED_FORMAT(encoded)!=SPS_FORMAT_S16_LE || CHANNELS_FROM_ENCODED_FORMAT(encoded)!=2 || RATE_FROM_ENCODED_FORMAT(encoded)!=48000)return -EINVAL;
  android_rate=48000;return 0;
}
static void help(void) {}
audio_output audio_pipe={.name="android",.help=help,.init=init,.deinit=stop,
 .prepare=prepare,.get_configuration=configuration,.configure=configure,.stop=stop,
 .flush=flush,.delay=delay,.stats=stats,.play=play};
