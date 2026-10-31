"""Exercise the production room-volume authority against a simulated Android mixer."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[2]
sources = {
"android/content/SharedPreferences.java": '''package android.content;import java.util.*;public class SharedPreferences {
private final Map<String,Object> values=new HashMap<>();public int getInt(String k,int d){Object v=values.get(k);return v==null?d:(Integer)v;}public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v==null?d:(Boolean)v;}
public Editor edit(){return new Editor();}public class Editor {private final Map<String,Object> edits=new HashMap<>();public Editor putInt(String k,int v){edits.put(k,v);return this;}public Editor putBoolean(String k,boolean v){edits.put(k,v);return this;}public boolean commit(){values.putAll(edits);return true;}}}''',
"android/content/Context.java": '''package android.content;import java.io.*;public class Context {
public static final int MODE_PRIVATE=0;public static final String AUDIO_SERVICE="audio";public final android.media.AudioManager audio=new android.media.AudioManager();public final SharedPreferences prefs=new SharedPreferences();private final File files;
public Context(File f){files=f;}public Context getApplicationContext(){return this;}public Object getSystemService(String s){return audio;}public SharedPreferences getSharedPreferences(String n,int m){return prefs;}public File getFilesDir(){return files;}}''',
"android/media/AudioManager.java": '''package android.media;public class AudioManager {
public static final int STREAM_MUSIC=3,ADJUST_MUTE=-100,ADJUST_UNMUTE=100;public int index=7,maximum=20,writes;public boolean mute;
public int getStreamVolume(int s){return mute?0:index;}public int getLastAudibleStreamVolume(int s){return index;}public int getStreamMaxVolume(int s){return maximum;}public boolean isStreamMute(int s){return mute;}
public void setStreamVolume(int s,int v,int f){index=v;writes++;if(v==0)mute=true;}public void adjustStreamVolume(int s,int v,int f){mute=v==ADJUST_MUTE;}}''',
"android/os/SystemClock.java": 'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
"android/os/Looper.java": 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
"android/os/Handler.java": 'package android.os;public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
"android/util/AtomicFile.java": '''package android.util;import java.io.*;public class AtomicFile {private final File path;public AtomicFile(File p){path=p;}public FileOutputStream startWrite()throws IOException{return new FileOutputStream(path);}public void finishWrite(FileOutputStream f)throws IOException{f.close();}public void failWrite(FileOutputStream f){try{f.close();}catch(IOException e){}}}''',
"com/wallpanel/multiroom/GroupRuntime.java": 'package com.wallpanel.multiroom;public class GroupRuntime {public static boolean call;public static boolean intercom(){return call;}}',
"com/wallpanel/multiroom/RoomVolumeHarness.java": '''package com.wallpanel.multiroom;
import android.content.*;import android.os.SystemClock;import com.wallpanel.audioreceiver.AudioOutputGate;import java.io.*;import java.nio.file.*;
public class RoomVolumeHarness {
static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}static void close(double a,double b){check(Math.abs(a-b)<.00001);}
static void restart(Context c)throws Exception{java.lang.reflect.Field f=RoomVolume.class.getDeclaredField("app");f.setAccessible(true);f.set(null,null);RoomVolume.initialize(c);}
public static void main(String[] args)throws Exception{
Context c=new Context(new File(args[0]));c.prefs.edit().putInt("volume_milli",100).commit();RoomVolume.initialize(c);
close(RoomVolume.value(c),.35);check(c.audio.writes==0);check(c.prefs.getBoolean("room_volume_v2",false));close(RoomVolume.gain(),1);
RoomVolume.apply();close(RoomVolume.value(c),.35);check(c.audio.index==7); // Starting a group must not restore its old 10% level.
for(int i=1;i<=100;i++){RoomVolume.set(c,i/100.0);check(c.audio.index==RoomLevel.index(i,20));close(RoomVolume.value(c),i/100.0);close(RoomVolume.gain(),RoomLevel.gain(i,20));check(!RoomVolume.muted(c));}
RoomVolume.set(c,0);check(!RoomVolume.muted(c));check(c.audio.index==0);RoomVolume.set(c,.02);check(!c.audio.mute);check(c.audio.index==1);close(RoomVolume.gain(),.4);
RoomVolume.mute(c,true);check(c.audio.mute);close(RoomVolume.value(c),.02);close(RoomVolume.gain(),0);RoomVolume.set(c,.43);check(!RoomVolume.muted(c)&&!c.audio.mute);check(c.audio.index==9);
float[] sink={-1};AudioOutputGate gate=AudioOutputGate.INSTANCE;gate.bind(v->sink[0]=v?RoomVolume.gain():0);
AudioOutputGate.Lease call=gate.hold();GroupRuntime.call=true;int writes=c.audio.writes;
RoomVolume.set(c,.12);check(c.audio.writes==writes);close(sink[0],0);close(RoomVolume.value(c),.12);
SystemClock.now+=1000;RoomVolume.observe(c);check(c.audio.writes==writes);GroupRuntime.call=false;call.close();RoomVolume.observe(c);check(c.audio.index==3);close(sink[0],.8);
// Android buttons/phone volume are observed, except for the short echo-suppression window.
c.audio.index=9;RoomVolume.observe(c);close(RoomVolume.value(c),.12);SystemClock.now+=1000;RoomVolume.observe(c);close(RoomVolume.value(c),.45);close(sink[0],1);
c.audio.mute=true;RoomVolume.observe(c);check(RoomVolume.muted(c));close(sink[0],0);c.audio.mute=false;RoomVolume.observe(c);check(!RoomVolume.muted(c));close(sink[0],1);
RoomVolume.set(c,.43);RoomVolume.mute(c,true);c.audio.index=15;c.audio.mute=false;restart(c);close(RoomVolume.value(c),.43);check(c.audio.index==9&&c.audio.mute);close(RoomVolume.gain(),0);
RoomVolume.mute(c,false);close(RoomVolume.gain(),.43*20/9);int gain=Integer.parseInt(new String(Files.readAllBytes(new File(c.getFilesDir(),"group-volume").toPath()),"US-ASCII"));check(Math.abs(gain/1000.0-RoomVolume.gain())<.001);
for(double invalid:new double[]{-.1,1.1,Double.NaN,Double.POSITIVE_INFINITY}){boolean rejected=false;try{RoomVolume.set(c,invalid);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
System.out.println("PASS: "+checks+" room-volume checks: migration, 1% levels, no second group gain, mute/zero, external controls, call deferral and restart persistence");
}}''',
}
with tempfile.TemporaryDirectory(prefix="room-volume-") as folder:
    temp=Path(folder); files=[]
    for name,body in sources.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
    files += [root/"shared/multiroom/src/com/wallpanel/multiroom"/name for name in ("RoomVolume.java","RoomLevel.java")]
    files.append(root/"shared/audio-receiver/src/com/wallpanel/audioreceiver/AudioOutputGate.java")
    java=r"C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe"
    out=temp/"classes";out.mkdir()
    subprocess.run([java,"-jar",str(root/"audio/tooling/ecj.jar"),"-1.8","-nowarn","-d",str(out),*[str(p) for p in files]],check=True)
    subprocess.run([java,"-cp",str(out),"com.wallpanel.multiroom.RoomVolumeHarness",str(temp)],check=True,timeout=15)
