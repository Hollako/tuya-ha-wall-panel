package com.wallpanel.multiroom;
import android.content.*;
import android.os.Build;
public final class GroupRuntime {
 public interface Host {boolean prepare();boolean intercom();void finished();}
 private static Host host;static volatile GroupService service;private static volatile String status="Group playback is stopped";
 public static void host(Host value){host=value;}
 static boolean prepare(){return host==null||host.prepare();}
 static boolean intercom(){return host!=null&&host.intercom();}
 static void finished(){if(host!=null)host.finished();}
 static void status(String value){status=value;}
 public static String status(){return status;}
 public static boolean active(){GroupService s=service;return s!=null&&s.running;}
 public static boolean paused(){GroupService s=service;return s!=null&&s.paused;}
 public static double volume(Context context){android.media.AudioManager audio=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int maximum=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);return Math.round(context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).getInt("volume_milli",maximum==0?0:(int)Math.round(1000.0*audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)/maximum))/10.0)/100.0;}
 public static boolean muted(Context context){return context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).getBoolean("muted",false)||volume(context)==0||active()&&((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).isStreamMute(android.media.AudioManager.STREAM_MUSIC);}
 static double outputVolume(Context context){return context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).getBoolean("muted",false)?0:volume(context);}
 public static synchronized void mute(Context context,boolean value)throws java.io.IOException{writeVolume(context,value?0:(int)Math.round(volume(context)*1000));context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).edit().putBoolean("muted",value).apply();GroupService s=service;if(s!=null&&s.running)s.applyVolume(value?0:volume(context));}
 static synchronized void initializeVolume(Context context,double value)throws java.io.IOException{int milli=(int)Math.round(value*1000);context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).edit().putInt("volume_milli",milli).apply();writeVolume(context,(int)Math.round(outputVolume(context)*1000));}
 public static synchronized void volume(Context context,double value)throws java.io.IOException{
  if(Double.isNaN(value)||Double.isInfinite(value)||value<0||value>1)throw new IllegalArgumentException("Volume must be 0 to 100%");
  int milli=(int)Math.round(value*100)*10;writeVolume(context,milli);
  context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).edit().putInt("volume_milli",milli).putBoolean("muted",false).apply();GroupService s=service;if(s!=null&&s.running)s.applyVolume(milli/1000.0);
 }
 private static void writeVolume(Context context,int milli)throws java.io.IOException{android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(context.getFilesDir(),"group-volume"));java.io.FileOutputStream out=null;
  try{out=file.startWrite();out.write(Integer.toString(milli).getBytes(java.nio.charset.StandardCharsets.US_ASCII));file.finishWrite(out);}catch(java.io.IOException error){if(out!=null)file.failWrite(out);throw error;}
 }
 public static void transport(String command){GroupService s=service;if(s!=null)s.transport(command);}
 public static synchronized void roomTiming(Context context,int value)throws java.io.IOException{
  value=GroupPolicy.roomDelay(value);android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(context.getFilesDir(),"group-room-delay"));java.io.FileOutputStream out=null;
  try{out=file.startWrite();out.write(Integer.toString(value).getBytes(java.nio.charset.StandardCharsets.US_ASCII));file.finishWrite(out);}catch(java.io.IOException error){if(out!=null)file.failWrite(out);throw error;}
  context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).edit().putInt("room_delay_ms",value).apply();GroupService s=service;if(s!=null)s.roomDelay=value;
 }
 public static void start(Context context){if(Build.VERSION.SDK_INT<27)throw new IllegalStateException("Group playback requires Android 8.1 or newer");Intent i=new Intent(context,GroupService.class);context.startForegroundService(i);}
 public static void stop(Context context){context.stopService(new Intent(context,GroupService.class));}
 private GroupRuntime(){}
}
