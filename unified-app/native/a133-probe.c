/* A133 candidate inventory only. No device writes, ownership or GPIO assumptions.
 * Runtime activation is deliberately absent until the A133 adapter is verified. */
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
static int knob_candidate(void){
    char b[64]={0};int fd=open("/sys/class/graphics/fb1/name",O_RDONLY);if(fd<0)return 0;
    int n=read(fd,b,sizeof(b)-1);close(fd);return n>0&&strstr(b,"gc9a01")!=NULL;
}
int main(int argc,char **argv){
    if(argc!=2)return 2;
    if(!strcmp(argv[1],"--describe")){
        puts("{\"v\":1,\"profile\":\"a133\",\"profile_verified\":false,\"runtime_ready\":false,\"network\":false,\"buttons\":0,\"relays\":4,\"knob_framebuffer\":1,\"led\":false}");return 0;
    }
    if(!strcmp(argv[1],"--probe")){
        int relays=access("/proc/vendor/relay_first",F_OK)==0&&access("/proc/vendor/relay_second",F_OK)==0&&access("/proc/vendor/relay_third",F_OK)==0&&access("/proc/vendor/relay_four",F_OK)==0;
        printf("{\"v\":1,\"profile\":\"a133\",\"layout_matched\":false,\"candidate_knob_fb1\":%s,\"candidate_four_relays\":%s,\"runtime_ready\":false,\"hardware_accessed\":false,\"outputs_written\":false}\n",knob_candidate()?"true":"false",relays?"true":"false");return 0;
    }
    fputs("A133 direct hardware adapter requires verification; no outputs changed.\n",stderr);return 2;
}
