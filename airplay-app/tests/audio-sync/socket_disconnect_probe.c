/* On-device regression: same-UID CONFIG/feedback sessions, then close the read side. No PCM. */
#define _GNU_SOURCE
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stddef.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <unistd.h>
static void put32(unsigned char*p,uint32_t n){p[0]=n>>24;p[1]=n>>16;p[2]=n>>8;p[3]=n;}
int main(int argc,char**argv){
 if(argc!=2)return 2;
 struct sockaddr_un address={.sun_family=AF_UNIX};
 if(strlen(argv[1])>sizeof(address.sun_path)-2)return 2;
 memcpy(address.sun_path+1,argv[1],strlen(argv[1]));
 printf("probe UID=%u\n",(unsigned)getuid());
 for(int cycle=1;cycle<=5;cycle++){
  int s=socket(AF_UNIX,SOCK_STREAM,0);if(s<0){perror("socket");return 3;}
  struct timeval timeout={2,0};setsockopt(s,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
  if(connect(s,(struct sockaddr*)&address,offsetof(struct sockaddr_un,sun_path)+1+strlen(argv[1]))){perror("connect");return 4;}
  unsigned char config[40]={0},feedback[76];put32(config,0x57504131);put32(config+4,1);put32(config+12,cycle);put32(config+20,48000);
  if(send(s,config,sizeof(config),MSG_NOSIGNAL)!=sizeof(config)){perror("CONFIG");return 5;}
  size_t at=0;while(at<sizeof(feedback)){ssize_t n=recv(s,feedback+at,sizeof(feedback)-at,0);if(n<=0){perror("feedback");return 6;}at+=n;}
  if(memcmp(feedback,"WPF1",4)){fprintf(stderr,"bad feedback");return 7;}
  shutdown(s,SHUT_RD);usleep(150000);close(s);usleep(100000);
  printf("disconnect/reconnect cycle %d passed\n",cycle);
 }
 return 0;
}
