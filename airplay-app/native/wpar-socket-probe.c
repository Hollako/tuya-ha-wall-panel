#include <sys/time.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
int main(int argc,char **argv){if(argc!=2)return 2;int s=socket(AF_UNIX,SOCK_STREAM,0);struct timeval t={3,0};setsockopt(s,SOL_SOCKET,SO_RCVTIMEO,&t,sizeof(t));struct sockaddr_un a={.sun_family=AF_UNIX};strcpy(a.sun_path,"/data/local/wpar-airplay/ptp.sock");if(connect(s,(void*)&a,sizeof(a))){perror("connect");return 3;}write(s,argv[1],strlen(argv[1]));write(s,"\n",1);char b[128];int n=read(s,b,sizeof(b)-1);if(n<=0){puts("REJECTED");return 4;}b[n]=0;printf("%s",b);close(s);return 0;}
