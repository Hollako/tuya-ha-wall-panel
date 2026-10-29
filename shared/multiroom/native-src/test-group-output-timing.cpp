#include "group-output-timing.h"
#include <cassert>
int main(){
 bool measured=false;
 using wallpanel_group::delayUs;
 assert(delayUs(48000,0,48000,45600,1000000000,1010000000,43,&measured)==40000&&measured);
 assert(delayUs(48000,0,48000,43200,1000000000,1010000000,43,&measured)==90000&&measured);
 // Unsupported/stale/impossible presentation timestamps must use queue + HAL, not a fixed constant.
 assert(delayUs(48000,45600,48000,-1,0,1010000000,43,&measured)==93000&&!measured);
 assert(delayUs(48000,45600,48000,43200,1,4000000000,43,&measured)==93000&&!measured);
 assert(delayUs(48000,45600,48000,50000,1000000000,1010000000,43,&measured)==93000&&!measured);
 assert(delayUs(48000,45600,48000,48000,1000000000,1010000000,43,&measured)==0&&measured);
 assert(delayUs(4800000,0,48000,-1,0,1010000000,43,&measured)==500000&&!measured);
 assert(delayUs(0,0,0,-1,0,0,0)==50000);
}
