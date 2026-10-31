package com.wallpanel.audioreceiver;
import java.util.*;
/** One music-profile request per incoming link; never a reconnect loop. */
public final class IncomingMusicPolicy {
 public static final class Link {final String key;final long at;boolean attempted;Link(String k,long t){key=k;at=t;}}
 private final Map<String,Link> links=new HashMap<>();private final Map<String,Long> attempts=new HashMap<>();
 public synchronized Link connected(String key,long now){Link old=links.get(key);if(old!=null)return old;if(links.size()>=8)return null;Link link=new Link(key,now);links.put(key,link);return link;}
 public synchronized void disconnected(String key){links.remove(key);}
 public synchronized void radioOff(){links.clear();}
 public synchronized boolean current(Link link,long now){return link!=null&&links.get(link.key)==link&&!link.attempted&&now>=link.at&&now-link.at<=5000;}
 public synchronized boolean claim(Link link,long now,boolean bonded,boolean enabled,int state,boolean otherBusy){
  if(!current(link,now)||!bonded||!enabled||state!=0||otherBusy)return false;
  Long last=attempts.get(link.key);if(last!=null&&now-last<10000)return false;
  link.attempted=true;attempts.put(link.key,now);if(attempts.size()>32){Iterator<String> keys=attempts.keySet().iterator();while(keys.hasNext()){String key=keys.next();if(!key.equals(link.key)&&!links.containsKey(key))keys.remove();}}return true;
 }
}
