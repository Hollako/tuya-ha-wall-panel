/* App-UID-only AirPlay 2 transport commands. Never part of the audio clock path. */
#define _GNU_SOURCE
#include "rtsp.h"
#include "remote/remote.h"
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <unistd.h>

extern ssize_t ap2_event_send_simple_modern_media_remote_command(rtsp_conn_info *,unsigned int);
static int control_server=-1;
static int peer_allowed(int fd){struct ucred peer;socklen_t size=sizeof(peer);return !getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&size)&&peer.uid==getuid();}
static void reply(int fd,const char *message){send(fd,message,strlen(message),MSG_NOSIGNAL);}
static void handle(int fd){
  if(!peer_allowed(fd))return;
  struct timeval timeout={2,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
  char request[64];size_t at=0;
  while(at<sizeof(request)-1){ssize_t n=recv(fd,request+at,1,0);if(n<=0)return;if(request[at++]=='\n')break;}
  if(!at||request[at-1]!='\n'){reply(fd,"ERR REQUEST\n");return;}request[at-1]=0;
  int status=!strcmp(request,"STATUS"),command=-1;unsigned long token=0;char *end=NULL,*space=strchr(request,' ');
  if(!status){
    if(!space){reply(fd,"ERR REQUEST\n");return;}*space++=0;
    if(!strcmp(request,"PLAY"))command=rcsc_play;
    else if(!strcmp(request,"PAUSE"))command=rcsc_pause;
    else if(!strcmp(request,"TOGGLE"))command=rcsc_play_pause;
    else if(!strcmp(request,"NEXT"))command=rcsc_next_item;
    else if(!strcmp(request,"PREVIOUS"))command=rcsc_previous_item;
    if(command<0||!*space||strspn(space,"0123456789")!=strlen(space)){reply(fd,"ERR REQUEST\n");return;}
    errno=0;token=strtoul(space,&end,10);if(errno||*end||token>2147483647UL){reply(fd,"ERR REQUEST\n");return;}
  }
  if(pthread_rwlock_tryrdlock(&principal_conn_lock)){reply(fd,"ERR BUSY\n");return;}
  rtsp_conn_info *conn=principal_conn;
  const char *error=NULL;char response[96];
  if(!conn)error="ERR NO_SESSION\n";
  else if(conn->airplay_type!=ap_2)error="ERR UNSUPPORTED\n";
  else if(!status&&token!=(unsigned long)conn->connection_number)error="ERR STALE\n";
  else if(pthread_mutex_trylock(&conn->event_sender_mutex))error="ERR BUSY\n";
  else{
    int ready=conn->event_channel_fd>0;pthread_mutex_unlock(&conn->event_sender_mutex);
    if(!ready)error="ERR UNAVAILABLE\n";
    else if(status){
      if(pthread_mutex_trylock(&conn->flush_mutex))error="ERR BUSY\n";
      else{int playing=conn->ap2_rate==1;pthread_mutex_unlock(&conn->flush_mutex);snprintf(response,sizeof(response),"OK AP2 %d %s\n",conn->connection_number,playing?"playing":"paused");}
    }else{
      // Upstream sends an encrypted event-channel POST and validates its RTSP
      // response. Socket/mutex deadlines are installed by patch-control.py.
      ssize_t result=ap2_event_send_simple_modern_media_remote_command(conn,(unsigned int)command);
      if(result<=0)error="ERR REJECTED\n";
      else snprintf(response,sizeof(response),"OK ACK %d\n",conn->connection_number);
    }
  }
  pthread_rwlock_unlock(&principal_conn_lock);
  reply(fd,error?error:response);
}
static void *serve(void *unused){(void)unused;for(;;){int fd=accept(control_server,NULL,NULL);if(fd<0){if(errno==EINTR)continue;break;}handle(fd);close(fd);}return NULL;}
int wpar_control_start(void){
  const char *name=getenv("WPAR_CONTROL_SOCKET");if(!name||!*name)return 0;
  struct sockaddr_un address={.sun_family=AF_UNIX};size_t length=strlen(name);
  if(length>sizeof(address.sun_path)-2)return -1;
  memcpy(address.sun_path+1,name,length);
  int server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(server<0){perror("Private control socket");return -1;}
  if(bind(server,(void*)&address,sizeof(sa_family_t)+1+length)||listen(server,4)){perror("Private control bind/listen");close(server);return -1;}
  control_server=server;pthread_t thread;
  int rc=pthread_create(&thread,NULL,serve,NULL);if(rc){fprintf(stderr,"Private control thread: %s\n",strerror(rc));close(server);control_server=-1;return -1;}
  pthread_detach(thread);return 0;
}
