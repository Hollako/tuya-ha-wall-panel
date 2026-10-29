#pragma once
#include <cstdint>
#include <algorithm>
namespace wallpanel_group {
// All frame counters belong to this output stream; all times use CLOCK_MONOTONIC.
inline int64_t delayUs(int64_t written,int64_t read,int rate,int64_t frame,int64_t timestampNs,int64_t nowNs,int halMs,bool* measured=nullptr){
 if(measured)*measured=false;
 if(rate<=0)return 50000;
 if(frame>=0&&frame<=written&&timestampNs>0&&nowNs>=timestampNs&&nowNs-timestampNs<=2000000000LL){
  int64_t delay=(timestampNs-nowNs)/1000+(written-frame)*1000000/rate;
  if(delay>=-20000&&delay<=500000){if(measured)*measured=true;return std::max<int64_t>(0,delay);}
 }
 int64_t queued=std::max<int64_t>(0,written-read);
 return std::min<int64_t>(500000,queued*1000000/rate+std::max(0,std::min(500,halMs))*1000LL);
}
}
