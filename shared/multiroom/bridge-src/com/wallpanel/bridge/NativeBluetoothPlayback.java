package com.wallpanel.bridge;
import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.util.*;
/** Bounded background inspection. Never blocks PCM delivery or the status socket. */
final class NativeBluetoothPlayback {
 private final Context context;private final Object pulse=new Object();private volatile long fastUntil,at;private volatile PlaybackTracks.Result latest=new PlaybackTracks.Result(false,false);
 volatile String reason="not_checked";
 NativeBluetoothPlayback(Context c){context=c;}
 void fast(){fastUntil=SystemClock.elapsedRealtime()+16000;synchronized(pulse){pulse.notifyAll();}}
 void inactive(){latest=new PlaybackTracks.Result(true,false);at=SystemClock.elapsedRealtime();}
 PlaybackTracks.Result snapshot(){return SystemClock.elapsedRealtime()-at<7000?latest:new PlaybackTracks.Result(false,false);}
 void refresh(){
  Process process=null;final java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
  try{
   List<Integer> pids=new ArrayList<Integer>();ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
   try{List<ActivityManager.RunningAppProcessInfo> apps=manager.getRunningAppProcesses();if(apps!=null)for(ActivityManager.RunningAppProcessInfo app:apps)if(app.uid==1002)pids.add(Integer.valueOf(app.pid));}catch(Exception ignored){}
   process=new ProcessBuilder("/system/bin/dumpsys","-t","1","media.audio_flinger").redirectErrorStream(true).start();final Process bounded=process;
   Thread watchdog=new Thread(new Runnable(){public void run(){try{Thread.sleep(1400);}catch(InterruptedException ignored){}if(!done.get()){bounded.destroy();try{bounded.getInputStream().close();}catch(Exception ignored){}}}},"bt-track-deadline");watchdog.setDaemon(true);watchdog.start();
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputStream input=process.getInputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>524288)throw new IOException("Native status exceeded limit");bytes.write(buffer,0,n);}
   String dump=bytes.toString("UTF-8");int code=process.waitFor();int[] ids=new int[pids.size()];for(int i=0;i<ids.length;i++)ids[i]=pids.get(i).intValue();latest=code==0?PlaybackTracks.inspect(dump,ids):new PlaybackTracks.Result(false,false);reason=code!=0?"dump_exit_"+code:ids.length==0?"no_bluetooth_pid":latest.known?"ok":dump.contains("Permission Denial")?"dump_denied":"unrecognized_tracks";at=SystemClock.elapsedRealtime();
  }catch(Exception unavailable){reason=unavailable.getClass().getSimpleName();latest=new PlaybackTracks.Result(false,false);at=SystemClock.elapsedRealtime();}finally{done.set(true);if(process!=null){process.destroy();try{process.getInputStream().close();process.getErrorStream().close();process.getOutputStream().close();}catch(IOException ignored){}}}
 }
 long interval(){return SystemClock.elapsedRealtime()<fastUntil?500:3000;}
 void rest(){synchronized(pulse){try{pulse.wait(interval());}catch(InterruptedException ignored){}}}
}
