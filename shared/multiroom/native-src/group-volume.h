#pragma once
#include <atomic>
#include <fstream>
#include <algorithm>
#include <cstdint>
namespace wallpanel_group {
inline std::atomic<float> localVolume{0.0f}; // Silent until the app's setting is read.
inline float readVolume(const char* path,float previous){
 if(!path)return previous;
 std::ifstream input(path);int value;if(!(input>>value)||value<0||value>1000)return previous;
 input>>std::ws;return input.eof()?value/1000.0f:previous;
}
inline void applyVolume(void* pcm,int frames,int channels,int rate,float& current){
 const float target=localVolume.load(std::memory_order_relaxed);
 const float step=1.0f/std::max(1,rate/50); // At most 20 ms for a full-scale change.
 auto* samples=static_cast<int16_t*>(pcm);
 for(int frame=0;frame<frames;++frame){
  current=target>current?std::min(target,current+step):std::max(target,current-step);
  for(int channel=0;channel<channels;++channel){int at=frame*channels+channel;samples[at]=static_cast<int16_t>(samples[at]*current);}
 }
}
}
