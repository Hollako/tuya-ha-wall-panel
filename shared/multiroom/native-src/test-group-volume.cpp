#include "group-volume.h"
#include <cassert>
#include <cmath>
#include <cstdio>
int main(){
 using namespace wallpanel_group;
 int16_t samples[1920];float current=1;localVolume.store(.05f);
 for(auto& sample:samples)sample=10000;
 applyVolume(samples,960,2,48000,current);assert(std::abs(current-.05f)<.00001f);assert(samples[1918]>=499&&samples[1918]<=500);assert(samples[1918]==samples[1919]);
 localVolume.store(0);applyVolume(samples,960,2,48000,current);assert(current==0&&samples[1919]==0);
 const char* path="/tmp/wpar-group-volume-test.setting";
 {std::ofstream file(path);file<<10;}assert(std::abs(readVolume(path,1)-.01f)<.00001f);
 {std::ofstream file(path);file<<1001;}assert(readVolume(path,.5f)==.5f);
 {std::ofstream file(path);file<<"5junk";}assert(readVolume(path,.5f)==.5f);std::remove(path);
}
