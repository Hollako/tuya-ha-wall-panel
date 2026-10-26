/* Versioned hardware-only settings. No broker, credentials, URL or shell fields. */
#ifndef WALLPANEL_CORE_CONFIG_H
#define WALLPANEL_CORE_CONFIG_H
#include <ctype.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
struct core_config {char device_id[49];int buttons,relays;int bindings[4][2];int knob_input,knob_display,audio,amplifier,display;};
static int core_config_read(const char *path,struct core_config *out){
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;
    struct stat st;
    if(fstat(fd,&st)||!S_ISREG(st.st_mode)||(st.st_mode&0077)||st.st_uid!=geteuid()||st.st_size>4096){close(fd);return -1;}
    FILE *f=fdopen(fd,"r");if(!f){close(fd);return -1;}
    struct core_config c={0};unsigned seen=0;char line[256];int bad=0;
    while(fgets(line,sizeof(line),f)){
        size_t n=strlen(line);
        if(!n||line[n-1]!='\n'){bad=1;break;}line[--n]=0;if(n&&line[n-1]=='\r')line[--n]=0;
        if(!n||line[0]=='#')continue;
        char *value=strchr(line,'=');if(!value){bad=1;break;}*value++=0;
        unsigned bit=0;
        if(!strcmp(line,"version")){bit=1;if(strcmp(value,"1"))bad=1;}
        else if(!strcmp(line,"profile")){bit=2;if(strcmp(value,"g10"))bad=1;}
        else if(!strcmp(line,"device_id")){
            bit=4;size_t length=strlen(value);if(!length||length>=sizeof(c.device_id))bad=1;
            for(size_t i=0;i<length;i++)if(!((value[i]>='a'&&value[i]<='z')||(value[i]>='0'&&value[i]<='9')||value[i]=='_'))bad=1;
            if(!bad)strcpy(c.device_id,value);
        }else if(!strcmp(line,"buttons")||!strcmp(line,"relays")){
            bit=!strcmp(line,"buttons")?8:16;
            if(strcmp(value,"0")&&strcmp(value,"1"))bad=1;
            else if(bit==8)c.buttons=value[0]-'0';else c.relays=value[0]-'0';
        }else if(!strcmp(line,"knob_input")||!strcmp(line,"knob_display")){
            bit=!strcmp(line,"knob_input")?8192:16384;
            if(strcmp(value,"0")&&strcmp(value,"1"))bad=1;
            else if(bit==8192)c.knob_input=value[0]-'0';else c.knob_display=value[0]-'0';
        }else if(!strcmp(line,"audio")||!strcmp(line,"amplifier")){
            bit=!strcmp(line,"audio")?32768:65536;
            if(strcmp(value,"0")&&strcmp(value,"1"))bad=1;
            else if(bit==32768)c.audio=value[0]-'0';else c.amplifier=value[0]-'0';
        }else if(!strcmp(line,"display")){
            bit=131072;if(strcmp(value,"0")&&strcmp(value,"1"))bad=1;else c.display=value[0]-'0';
        }else{
            for(int b=0;b<4;b++)for(int gesture=0;gesture<2;gesture++){
                char key[48];snprintf(key,sizeof(key),"button_%d_%s",b+1,gesture?"double":"single");
                if(!strcmp(line,key)){
                    bit=1u<<(5+b*2+gesture);
                    if(strlen(value)!=1||value[0]<'0'||value[0]>'2')bad=1;
                    else c.bindings[b][gesture]=value[0]-'0';
                }
            }
        }
        if(!bit||(seen&bit)||bad){bad=1;break;}seen|=bit;
    }
    if(ferror(f))bad=1;fclose(f);
    if(bad||(seen&31)!=31)return -1;*out=c;return 0;
}
#endif
