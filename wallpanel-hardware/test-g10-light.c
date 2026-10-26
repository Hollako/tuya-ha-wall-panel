#undef NDEBUG
#include <assert.h>
#include <stdio.h>
#include "g10_mcu.h"
int main(void){
    unsigned char out[7];
    for(int state=-1;state<3;state++){
        memset(out,0x55,7);int valid=state==0||state==1;
        assert((g10_light_power(out,state)==0)==valid);
        if(valid){assert(g10_allowed(out)&&out[2]==0x1d&&out[3]==state&&!out[4]&&!out[5]);}
        else for(int i=0;i<7;i++)assert(out[i]==0x55);
    }
    for(int level=-1;level<=256;level++){
        memset(out,0x55,7);int valid=level>=0&&level<=100;
        assert((g10_light_brightness(out,level)==0)==valid);
        if(valid){assert(g10_allowed(out)&&out[2]==0x1d&&out[3]==2&&out[4]==level&&!out[5]);}
        else for(int i=0;i<7;i++)assert(out[i]==0x55);
    }
    for(int mode=0;mode<256;mode++)for(int level=0;level<256;level++)for(int key=0;key<2;key++){
        unsigned char f[]={0xfc,0,0x1d,(unsigned char)mode,(unsigned char)level,(unsigned char)key,0xfe};
        assert(!!g10_allowed(f)==(!key&&((mode<=1&&!level)||(mode==2&&level<=100))));
    }
    puts("PASS: exact vendor global-light power/brightness frames, bounds, unchanged invalid output, all opcode/value combinations, no per-button address guesses.");return 0;
}
