#define _GNU_SOURCE
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
static int backing(const char *name,char p[512]) {
 const char *dir=getenv("WPAR_SHM_DIR");
 if(!dir||strcmp(dir,"/data/local/wpar-airplay/shm")){errno=EACCES;return -1;}
 if(*name=='/')name++;
 size_t n=strlen(name);if(!n||n>120){errno=EINVAL;return -1;}
 for(size_t i=0;i<n;i++)if(!isalnum((unsigned char)name[i])&&name[i]!='-'&&name[i]!='_'){errno=EINVAL;return -1;}
 snprintf(p,512,"%s/%s",dir,name);return 0;
}
int __wrap_shm_open(const char *name,int flags,mode_t mode){
 (void)mode;char p[512];if(backing(name,p))return -1;
 int fd=open(p,flags|O_NOFOLLOW|O_CLOEXEC,0600);if(fd<0)return -1;
 struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=geteuid()){close(fd);errno=EACCES;return -1;}return fd;
}
int __wrap_shm_unlink(const char *name){char p[512];if(backing(name,p))return -1;return unlink(p);}
