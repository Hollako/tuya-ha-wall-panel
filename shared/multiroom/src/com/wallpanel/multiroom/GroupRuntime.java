package com.wallpanel.multiroom;
import android.content.*;
import android.os.Build;
public final class GroupRuntime {
 public interface Host {boolean prepare();boolean intercom();void finished();}
 private static Host host;static volatile GroupService service;private static volatile String status="Group playback is stopped";
 public static void host(Host value){host=value;}
 static boolean prepare(){return host==null||host.prepare();}
 public static boolean intercom(){return host!=null&&host.intercom();}
 static void finished(){if(host!=null)host.finished();}
 static void status(String value){status=value;}
 public static String status(){return status;}
 public static boolean active(){GroupService s=service;return s!=null&&s.running;}
 public static boolean paused(){GroupService s=service;return s!=null&&s.paused;}
 public static void bluetoothRecovery(boolean value){GroupService s=service;if(s!=null)s.recovery(value);}
 public static double volume(Context context){return RoomVolume.value(context);}
 public static boolean muted(Context context){return RoomVolume.muted(context);}
 static double outputVolume(Context context){return context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).getBoolean("muted",false)?0:volume(context);}
 public static void mute(Context context,boolean value)throws java.io.IOException{RoomVolume.mute(context,value);}
 static void initializeVolume(Context context,double value)throws java.io.IOException{RoomVolume.initialize(context);RoomVolume.apply();}
 public static void volume(Context context,double value)throws java.io.IOException{RoomVolume.set(context,value);}
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
