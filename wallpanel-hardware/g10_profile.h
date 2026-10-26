/* Exact observed GPIO/DT layout; do not reuse for arbitrary Tuya panels. */
#ifndef WALLPANEL_G10_PROFILE_H
#define WALLPANEL_G10_PROFILE_H
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
static uint32_t g10_be32(const unsigned char*p){return (uint32_t)p[0]<<24|(uint32_t)p[1]<<16|(uint32_t)p[2]<<8|p[3];}
static int g10_prop(const char*path,unsigned char*out,int n){int f=open(path,O_RDONLY|O_CLOEXEC);if(f<0)return -1;int got=read(f,out,n);close(f);return got==n?0:-1;}
static int g10_profile_valid(void){
    const char *bank="/sys/firmware/devicetree/base/pinctrl/gpio3@ff270000";
    const char *keys[]={"irq-a-gpio","irq-b-gpio","irq-c-gpio","irq-k1-gpio","irq-k2-gpio","irq-k3-gpio","irq-k4-gpio"};
    const uint32_t pins[]={16,18,10,5,0,7,2};
    unsigned char id[4],reg[16],value[12];char path[256];
    snprintf(path,sizeof(path),"%s/phandle",bank);if(g10_prop(path,id,4))return 0;
    snprintf(path,sizeof(path),"%s/reg",bank);
    if(g10_prop(path,reg,16)||g10_be32(reg)||g10_be32(reg+4)!=0xff270000||g10_be32(reg+8)||g10_be32(reg+12)!=0x100)return 0;
    for(int i=0;i<7;i++){
        snprintf(path,sizeof(path),"/sys/firmware/devicetree/base/knod_aispeech/%s",keys[i]);
        if(g10_prop(path,value,12)||g10_be32(value)!=g10_be32(id)||g10_be32(value+4)!=pins[i]||g10_be32(value+8)!=1)return 0;
    }
    return 1;
}
#endif
