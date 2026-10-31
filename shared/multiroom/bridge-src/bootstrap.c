/* Fixed Bluetooth bridge bootstrap; no shell, user paths, capture flags or audio code. */
#define _GNU_SOURCE
#include <unistd.h>
#include <grp.h>
#include <fcntl.h>
#include <sys/file.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#define BASE "/data/local/wallpanel-btgroup"
int main(int argc,char **argv){
 int probe=argc==3&&!strcmp(argv[1],"--probe");if(argc!=(probe?3:2)||geteuid()!=0)return 2;char *end;long owner=strtol(argv[probe?2:1],&end,10);if(*end||owner<10000||owner>19999)return 2;
 umask(077);if(!probe){int lock=open(BASE "/helper.lock",O_CREAT|O_RDWR|O_NOFOLLOW,0600);if(lock<0||flock(lock,LOCK_EX|LOCK_NB))return 3;
 int file=open(BASE "/helper.pid",O_CREAT|O_TRUNC|O_WRONLY|O_NOFOLLOW,0600);if(file<0)return 4;dprintf(file,"%d\n",getpid());close(file);}
 gid_t groups[]={1000,1005,3003};if(setgroups(3,groups)||setgid(1000)||setuid(1000))return 5;
 setenv("CLASSPATH",BASE "/bridge.jar",1);setenv("PATH","/system/bin",1);char name[80];snprintf(name,sizeof(name),"--nice-name=wallpanel-btgroup-%ld",owner);
 char *args[]={"/system/bin/app_process","/system/bin",name,"com.wallpanel.bridge.BluetoothBridge",probe?"--probe":argv[1],probe?argv[2]:NULL,NULL};execv(args[0],args);return 6;
}
