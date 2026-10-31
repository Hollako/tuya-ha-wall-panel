package com.wallpanel.multiroom;
import java.util.Map;
/** Automatic playback never guesses between multiple active sources. */
final class SourceDecision {
 static String choose(Map<String,Boolean> sources,String group){
  String active="";for(Map.Entry<String,Boolean> source:sources.entrySet())if(source.getValue()){if(!active.isEmpty())return "ambiguous";active=source.getKey();}
  if(!active.isEmpty())return active;if(group!=null&&sources.containsKey(group))return group;
  return sources.size()==1?sources.keySet().iterator().next():"none";
 }
 private SourceDecision(){}
}
