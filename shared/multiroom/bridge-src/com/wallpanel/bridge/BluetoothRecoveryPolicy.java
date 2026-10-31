package com.wallpanel.bridge;
/** One bounded handoff recovery, never a silence-triggered reconnect loop. No Android dependencies. */
public final class BluetoothRecoveryPolicy {
 private final long start;private long inactive=-1;private boolean attempted,cancelled;
 public BluetoothRecoveryPolicy(long now){start=now;}
 public synchronized void cancel(){cancelled=true;}
 public synchronized boolean observe(long now,boolean current,boolean singleSamePeer,boolean wantsPlay,boolean held,boolean remotePaused,boolean known,boolean active,boolean recentSignal){
  if(cancelled||attempted||now-start>16000||!current||!singleSamePeer||!wantsPlay||held||remotePaused){inactive=-1;return false;}
  if(!known||active||recentSignal){inactive=-1;return false;}
  if(inactive<0)inactive=now;
  if(now-start<2000||now-inactive<700)return false;
  attempted=true;return true;
 }
 public synchronized boolean attempted(){return attempted;}
 public static final class Budget {
  private final java.util.Map<String,Long> last=new java.util.HashMap<String,Long>();
  public synchronized boolean claim(String peer,String phase,long now){String key=peer+"/"+phase;Long previous=last.get(key);if(previous!=null&&now-previous.longValue()<30000)return false;if(last.size()>16)last.clear();last.put(key,Long.valueOf(now));return true;}
 }
}
