/* Shared timer format and feedback; no Android sleep or dashboard reload. */
#ifndef WALLPANEL_DISPLAY_POLICY_H
#define WALLPANEL_DISPLAY_POLICY_H
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#ifndef DP_STATE
#define DP_STATE "/data/local/panalume"
#endif
#ifndef DP_FEEDBACK
#define DP_FEEDBACK "/dev/wallpanel-display-state"
#endif
struct dp_policy {int dim_after,off_after,dim_brightness;};
static int dp_valid(struct dp_policy p){return p.dim_after>=0&&p.dim_after<=86400&&p.off_after>=0&&p.off_after<=86400&&p.dim_brightness>=1&&p.dim_brightness<=255&&(!p.dim_after||!p.off_after||p.dim_after<p.off_after);}
static int dp_write(const char *path,const char *text){
    char tmp[256];if(snprintf(tmp,sizeof(tmp),"%s.new",path)>=(int)sizeof(tmp))return -1;
    int fd=open(tmp,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);if(fd<0)return -1;struct stat st;
    if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=geteuid()||(st.st_mode&0077)){close(fd);return -1;}
    size_t n=strlen(text);int ok=write(fd,text,n)==(ssize_t)n&&!fsync(fd);if(close(fd))ok=0;
    if(!ok||rename(tmp,path)){unlink(tmp);return -1;}return 0;
}
static int dp_integer(const char *path,int lo,int hi){
    FILE *f=fopen(path,"r");if(!f)return -1;int value;char extra;int valid=fscanf(f,"%d %c",&value,&extra)==1&&value>=lo&&value<=hi;fclose(f);return valid?value:-1;
}
static int dp_load(struct dp_policy *out){
    struct dp_policy p={60,300,26};FILE *f=fopen(DP_STATE"/display-policy","r");
    if(f){char extra;int ok=fscanf(f,"v1 %d %d %d %c",&p.dim_after,&p.off_after,&p.dim_brightness,&extra)==3&&dp_valid(p);fclose(f);if(!ok)return -1;}
    *out=p;return 0;
}
static int dp_update(struct dp_policy *out,const char *field,int value){
    struct dp_policy p=*out;
    if(!strcmp(field,"dim_after"))p.dim_after=value;
    else if(!strcmp(field,"off_after"))p.off_after=value;
    else if(!strcmp(field,"dim_brightness"))p.dim_brightness=value;else return -1;
    if(!dp_valid(p))return -1;char b[96];snprintf(b,sizeof(b),"v1 %d %d %d\n",p.dim_after,p.off_after,p.dim_brightness);
    if(dp_write(DP_STATE"/display-policy",b))return -1;*out=p;return 0;
}
static int dp_phase(struct dp_policy p,long long idle){return p.off_after&&idle>=p.off_after*1000LL?2:p.dim_after&&idle>=p.dim_after*1000LL?1:0;}
static int dp_level(struct dp_policy p,int normal,int phase){return phase==2?0:phase==1&&p.dim_brightness<normal?p.dim_brightness:normal;}
static int dp_feedback(char *json,size_t size){
    FILE *f=fopen(DP_FEEDBACK,"r");if(!f)return -1;int pid,normal,actual,phase,app;struct dp_policy p;char extra;long long activity;
    int ok=fscanf(f,"v1 %d %d %d %d %d %d %d %d %lld %c",&pid,&normal,&actual,&phase,&p.dim_after,&p.off_after,&p.dim_brightness,&app,&activity,&extra)==9;
    fclose(f);if(!ok||pid<2||kill(pid,0)||normal<1||normal>255||actual<0||actual>255||phase<0||phase>2||!dp_valid(p)||(app!=0&&app!=1))return -1;
    snprintf(json,size,"{\"protocol_version\":1,\"available\":true,\"owner\":\"%s\",\"display_on\":%s,\"brightness\":%d,\"actual_brightness\":%d,\"phase\":\"%s\",\"dim_after\":%d,\"off_after\":%d,\"dim_brightness\":%d}",app?"app":"legacy",actual>0?"true":"false",normal,actual,phase==2?"off":phase==1?"dim":"awake",p.dim_after,p.off_after,p.dim_brightness);return 0;
}
#endif
