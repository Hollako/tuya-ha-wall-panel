package com.wallpanel.multiroom;
import android.content.*;
import android.media.AudioManager;
import android.os.SystemClock;
import java.io.*;

/** Shared authority for UI, HA, standalone playback and groups. No source-specific saved levels. */
public final class RoomVolume {
 private static Context app;private static long applied;private static int expected=-1;private static boolean pending;
 private static AudioManager audio(){return (AudioManager)app.getSystemService(Context.AUDIO_SERVICE);}
 private static SharedPreferences prefs(){return app.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE);}
 public static synchronized void initialize(Context context){
  if(app!=null)return;app=context.getApplicationContext();AudioManager audio=audio();SharedPreferences p=prefs();
  boolean remembered=p.getBoolean("room_volume_v2",false);
  if(!remembered){int index=audibleIndex(audio);int percent=(int)Math.round(100.0*index/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));if(!p.edit().putInt("volume_milli",percent*10).putBoolean("muted",index>0&&audio.isStreamMute(AudioManager.STREAM_MUSIC)).putBoolean("room_volume_v2",true).commit())throw new IllegalStateException("Room volume migration could not be saved");}
  expected=audibleIndex(audio);try{if(remembered)apply();else writeGain();}catch(IOException ignored){}
 }
 private static int audibleIndex(AudioManager audio){try{return ((Integer)AudioManager.class.getMethod("getLastAudibleStreamVolume",Integer.TYPE).invoke(audio,AudioManager.STREAM_MUSIC)).intValue();}catch(Exception unavailable){return audio.getStreamVolume(AudioManager.STREAM_MUSIC);}}
 public static synchronized double value(Context context){initialize(context);return prefs().getInt("volume_milli",0)/1000.0;}
 public static synchronized boolean muted(Context context){initialize(context);return prefs().getBoolean("muted",false);}
 public static synchronized float gain(){if(app==null)return 1;return muted(app)?0:(float)RoomLevel.gain((int)Math.round(value(app)*100),audio().getStreamMaxVolume(AudioManager.STREAM_MUSIC));}
 public static synchronized void set(Context context,double value)throws IOException{initialize(context);int percent=RoomLevel.percent(value);if(!prefs().edit().putInt("volume_milli",percent*10).putBoolean("muted",false).commit())throw new IOException("Room volume could not be saved");apply();}
 public static synchronized void mute(Context context,boolean value)throws IOException{initialize(context);if(!prefs().edit().putBoolean("muted",value).commit())throw new IOException("Room mute could not be saved");apply();}
 public static synchronized void apply()throws IOException{
  if(app==null)return;writeGain();if(GroupRuntime.intercom()){pending=true;return;}pending=false;
  AudioManager audio=audio();expected=RoomLevel.index((int)Math.round(value(app)*100),audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));applied=SystemClock.elapsedRealtime();
  audio.setStreamVolume(AudioManager.STREAM_MUSIC,expected,0);if(muted(app)||expected>0)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,muted(app)?AudioManager.ADJUST_MUTE:AudioManager.ADJUST_UNMUTE,0);
  new android.os.Handler(android.os.Looper.getMainLooper()).post(()->com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.refresh());
 }
 public static synchronized void observe(Context context){
  initialize(context);if(GroupRuntime.intercom())return;if(pending){try{apply();}catch(IOException ignored){}return;}if(SystemClock.elapsedRealtime()-applied<500)return;AudioManager audio=audio();int index=audibleIndex(audio);boolean mute=index>0&&audio.isStreamMute(AudioManager.STREAM_MUSIC);
  SharedPreferences.Editor change=prefs().edit();boolean changed=false;
  if(index!=expected){change.putInt("volume_milli",10*(int)Math.round(100.0*index/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))));expected=index;changed=true;}
  if(index>0&&mute!=prefs().getBoolean("muted",false)){change.putBoolean("muted",mute);changed=true;}
  if(changed){change.commit();try{writeGain();}catch(IOException ignored){}new android.os.Handler(android.os.Looper.getMainLooper()).post(()->com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.refresh());}
 }
 private static void writeGain()throws IOException{
  android.util.AtomicFile file=new android.util.AtomicFile(new File(app.getFilesDir(),"group-volume"));FileOutputStream out=null;
  try{out=file.startWrite();out.write(Integer.toString(Math.round(gain()*1000)).getBytes("US-ASCII"));file.finishWrite(out);}catch(IOException error){if(out!=null)file.failWrite(out);throw error;}
 }
 private RoomVolume(){}
}
