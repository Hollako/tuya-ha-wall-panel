#pragma once
#include <fstream>
namespace wallpanel_group {
struct TimeoutBudget {
 unsigned failures=0;
 bool retry(){return ++failures<3;}
 void reset(){failures=0;}
};
inline int roomLatency(const char* path,int current){
 if(!path)return current;
 std::ifstream input(path);int value;
 if(!(input>>value)||value < -500||value > 500)return current;
 input>>std::ws;if(!input.eof())return current;
 return -value;
}
}
