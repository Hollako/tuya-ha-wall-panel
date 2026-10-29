#include "group-timeout-policy.h"
#include <cassert>
#include <cstdio>
int main(){
 wallpanel_group::TimeoutBudget budget;
 assert(budget.retry());assert(budget.retry());assert(!budget.retry());budget.reset();assert(budget.retry());budget.reset();assert(budget.retry());
 const char* path="/tmp/wpar-group-room-test";
 {std::ofstream out(path);out<<100;}assert(wallpanel_group::roomLatency(path,3)==-100);
 {std::ofstream out(path);out<<-75;}assert(wallpanel_group::roomLatency(path,3)==75);
 {std::ofstream out(path);out<<999;}assert(wallpanel_group::roomLatency(path,3)==3);
 {std::ofstream out(path);out<<"10junk";}assert(wallpanel_group::roomLatency(path,3)==3);
 std::remove(path);assert(wallpanel_group::roomLatency(path,3)==3);
}
