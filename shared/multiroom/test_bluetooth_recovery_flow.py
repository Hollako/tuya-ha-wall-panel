"""Exercise the production BluetoothLink against simulated Android profiles, never a real phone."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
sources={
'com/wallpanel/bridge/AppBluetoothProfile.java':'''package com.wallpanel.bridge;import android.bluetooth.*;import java.util.*;public class AppBluetoothProfile implements BluetoothProfile {AppBluetoothProfile(int uid,boolean c,BluetoothAdapter a){}boolean ready(){return true;}boolean canReconnect(){return false;}boolean remotePaused(){return false;}public List<BluetoothDevice> getConnectedDevices(){return Collections.emptyList();}public int getConnectionState(BluetoothDevice d){return 0;}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock {public static volatile long offset;public static long elapsedRealtime(){return System.nanoTime()/1000000+offset;}}''',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
'android/content/Context.java':'''package android.content;public class Context {public static final String AUDIO_SERVICE="audio",BLUETOOTH_SERVICE="bt",ACTIVITY_SERVICE="activity";public final android.bluetooth.BluetoothManager bt=new android.bluetooth.BluetoothManager();public final android.media.AudioManager audio=new android.media.AudioManager();public Object getSystemService(String s){return s.equals(BLUETOOTH_SERVICE)?bt:s.equals(AUDIO_SERVICE)?audio:new android.app.ActivityManager();}public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,String p,android.os.Handler h){return null;}}''',
'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/content/Intent.java':'package android.content;public class Intent {public Object getParcelableExtra(String key){return null;}}',
'android/content/IntentFilter.java':'package android.content;public class IntentFilter {public IntentFilter(String action){}}',
'android/media/session/PlaybackState.java':'package android.media.session;public class PlaybackState {public static final int STATE_PLAYING=3,STATE_PAUSED=2,STATE_STOPPED=1,STATE_BUFFERING=6;public int getState(){return 0;}}',
'android/media/AudioManager.java':'package android.media;public class AudioManager {public static final int MODE_NORMAL=0;public int mode;public int getMode(){return mode;}public boolean isMusicActive(){return true;}}',
'android/app/ActivityManager.java':'package android.app;import java.util.*;public class ActivityManager {public static class RunningAppProcessInfo {public int uid,pid;}public List<RunningAppProcessInfo> getRunningAppProcesses(){return Collections.emptyList();}}',
'android/bluetooth/BluetoothDevice.java':'''package android.bluetooth;public class BluetoothDevice {public static final String EXTRA_DEVICE="device";public static final int BOND_BONDED=12;public String address;public int bond=12;public BluetoothDevice(String s){address=s;}public String getAddress(){return address;}public String getName(){return "Test phone";}public int getBondState(){return bond;}}''',
'android/bluetooth/BluetoothProfile.java':'''package android.bluetooth;import java.util.*;public interface BluetoothProfile {int STATE_CONNECTED=2,STATE_DISCONNECTED=0;List<BluetoothDevice> getConnectedDevices();int getConnectionState(BluetoothDevice d);interface ServiceListener {void onServiceConnected(int i,BluetoothProfile p);void onServiceDisconnected(int i);}}''',
'android/bluetooth/BluetoothAdapter.java':'''package android.bluetooth;public class BluetoothAdapter {public boolean enabled=true;public boolean isEnabled(){return enabled;}public boolean getProfileProxy(android.content.Context c,BluetoothProfile.ServiceListener s,int id){return false;}}''',
'android/bluetooth/BluetoothManager.java':'package android.bluetooth;public class BluetoothManager {public final BluetoothAdapter adapter=new BluetoothAdapter();public BluetoothAdapter getAdapter(){return adapter;}}',
'org/json/JSONException.java':'package org.json;public class JSONException extends Exception {}',
'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject {public static final Object NULL=new Object();private final Map<String,Object> m=new HashMap<>();public JSONObject put(String k,Object v)throws JSONException{m.put(k,v);return this;}public String optString(String k){Object v=m.get(k);return v==null?"":v.toString();}public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}public int optInt(String k){return ((Number)m.get(k)).intValue();}}''',
'com/wallpanel/bridge/BluetoothBridge.java':'package com.wallpanel.bridge;public class BluetoothBridge {public static boolean paused,playing=true;static boolean remotePaused(){return paused;}static boolean remotePlaying(){return playing;}}',
'com/wallpanel/bridge/RecoveryFlowTest.java':'''package com.wallpanel.bridge;
import android.content.*;import android.bluetooth.*;import android.os.SystemClock;import java.util.*;
public class RecoveryFlowTest {
static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
static void set(Object o,String name,Object v)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
static Object get(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
public static class Profile implements BluetoothProfile {
 public BluetoothDevice connected=new BluetoothDevice("original");public final BluetoothDevice original=connected;public int disconnects,connects,keys,disconnectDelay;public long disconnectUntil;public boolean rejectConnect,rejectDisconnect;public Runnable afterDisconnect;
 public synchronized List<BluetoothDevice> getConnectedDevices(){return connected==null?Collections.<BluetoothDevice>emptyList():Arrays.asList(connected);}
 public synchronized int getConnectionState(BluetoothDevice d){return SystemClock.elapsedRealtime()<disconnectUntil?3:connected!=null&&connected.getAddress().equals(d.getAddress())?2:0;}
 public boolean isAudioPlaying(BluetoothDevice d){return false;}
 public synchronized boolean disconnect(BluetoothDevice d){disconnects++;if(rejectDisconnect)return false;connected=null;disconnectUntil=SystemClock.elapsedRealtime()+disconnectDelay;if(afterDisconnect!=null)afterDisconnect.run();return true;}
 public synchronized boolean connect(BluetoothDevice d){connects++;if(rejectConnect||SystemClock.elapsedRealtime()<disconnectUntil)return false;ok(d==original);connected=d;return true;}
 public synchronized void sendPassThroughCmd(BluetoothDevice d,int key,int state){ok(d==original);ok(key==0x44);ok(state==0||state==1);keys++;}
}
static final class Fixture {
 final Context c=new Context();final BluetoothLink link=new BluetoothLink(c);final Profile profile=new Profile();final BluetoothLink.Lease lease;
 Fixture()throws Exception{this(true);}Fixture(boolean playing)throws Exception{BluetoothBridge.paused=false;BluetoothBridge.playing=playing;set(link,"sink",profile);set(link,"controller",profile);lease=link.begin(false);nativeState(false);}
 void nativeState(boolean active)throws Exception{Object n=get(link,"nativePlayback");set(n,"latest",new PlaybackTracks.Result(true,active));set(n,"at",Long.valueOf(SystemClock.elapsedRealtime()));}
 void trigger()throws Exception{SystemClock.offset+=2200;nativeState(false);lease.check();SystemClock.offset+=800;lease.check();}
 String state()throws Exception{return link.snapshot().optString("recovery");}
 void settled()throws Exception{long until=System.nanoTime()+3000000000L;while(((java.util.concurrent.atomic.AtomicBoolean)get(link,"cycling")).get()&&System.nanoTime()<until)Thread.sleep(5);ok(!((java.util.concurrent.atomic.AtomicBoolean)get(link,"cycling")).get());}
}
public static void main(String[] args)throws Exception{
Fixture f=new Fixture();f.trigger();f.settled();ok(f.profile.disconnects==1&&f.profile.connects==1&&f.profile.keys==2);ok(f.state().equals("waiting_for_audio"));f.lease.signal();ok(f.state().equals("restored"));SystemClock.offset+=2000;f.nativeState(false);f.lease.check();ok(f.profile.disconnects==1);
// Quiet valid output is never cycled.
f=new Fixture();SystemClock.offset+=2200;f.nativeState(true);f.lease.check();SystemClock.offset+=800;f.nativeState(true);f.lease.check();ok(f.profile.disconnects==0);
f=new Fixture();f.link.userTransport("PAUSE");f.trigger();ok(f.profile.disconnects==0);
f=new Fixture();f.lease.hold(true);f.trigger();ok(f.profile.disconnects==0);
f=new Fixture();f.c.audio.mode=3;f.trigger();ok(f.profile.disconnects==0);
f=new Fixture();f.lease.automatic(false);f.trigger();ok(f.profile.disconnects==0);
f=new Fixture();BluetoothBridge.paused=true;f.trigger();ok(f.profile.disconnects==0);BluetoothBridge.paused=false;
f=new Fixture(false);f.trigger();ok(f.profile.disconnects==0); // Connected but idle is not a broken playing stream.
f=new Fixture();f.nativeState(true);f.link.userTransport("PLAY");ok(f.link.playResult("PLAY",false));f.lease.check();ok(f.profile.keys==2&&f.profile.disconnects==0); // Deferred transport succeeds after the controller becomes ready.
f=new Fixture();f.link.userTransport("PLAY");ok(f.link.playResult("PLAY",false));f.link.userTransport("PAUSE");f.nativeState(true);f.lease.check();ok(f.profile.keys==0&&f.profile.disconnects==0);
// Cancellation after our disconnect must restore the connection quietly, without PLAY.
final Fixture pause=new Fixture();pause.profile.afterDisconnect=new Runnable(){public void run(){pause.link.userTransport("PAUSE");}};pause.trigger();pause.settled();ok(pause.profile.disconnects==1&&pause.profile.connects==1&&pause.profile.keys==0);
final Fixture call=new Fixture();call.profile.afterDisconnect=new Runnable(){public void run(){call.lease.hold(true);call.c.audio.mode=3;}};call.trigger();call.settled();ok(call.profile.connects==1&&call.profile.keys==0);
final Fixture stop=new Fixture();stop.profile.afterDisconnect=new Runnable(){public void run(){stop.link.end(stop.lease,false);}};stop.trigger();stop.settled();ok(stop.profile.connects==1&&stop.profile.keys==0);
// A user's newly selected phone or disabled radio must never be overridden.
final Fixture other=new Fixture();other.profile.afterDisconnect=new Runnable(){public void run(){other.profile.connected=new BluetoothDevice("other");}};other.trigger();other.settled();ok(other.profile.connects==0&&other.profile.keys==0);ok(other.profile.connected.getAddress().equals("other"));
final Fixture off=new Fixture();off.profile.afterDisconnect=new Runnable(){public void run(){off.c.bt.adapter.enabled=false;}};off.trigger();off.settled();ok(off.profile.connects==0&&!off.c.bt.adapter.enabled);
f=new Fixture();ok(f.lease.preserveInitialPlayback("PLAY"));ok(!f.lease.preserveInitialPlayback("PLAY"));
f=new Fixture(false);ok(!f.lease.preserveInitialPlayback("PLAY"));
f=new Fixture();f.lease.preserveInitialPlayback("PAUSE");ok(!f.lease.preserveInitialPlayback("PLAY"));
f=new Fixture();set(f.link,"sink",new AppBluetoothProfile(10065,false,f.c.bt.adapter));java.lang.reflect.Method repair=BluetoothLink.class.getDeclaredMethod("recover",BluetoothLink.Lease.class);repair.setAccessible(true);repair.invoke(f.link,f.lease);ok(f.state().equals("reconnect_unavailable"));ok(f.profile.disconnects==0);
f=new Fixture();f.profile.disconnectDelay=350;f.trigger();f.settled();ok(f.profile.connects==1&&f.profile.keys==2&&f.state().equals("waiting_for_audio"));
f=new Fixture();f.profile.rejectConnect=true;f.trigger();f.settled();ok(f.profile.connects==1&&f.profile.keys==0&&f.state().equals("manual_required"));
f=new Fixture();f.profile.rejectDisconnect=true;f.trigger();f.settled();ok(f.profile.disconnects==1&&f.profile.connects==0&&f.profile.keys==0);
// Stopping an unpaused group recovers a dead return-to-local route, with the same guards.
f=new Fixture();f.link.end(f.lease,true);SystemClock.offset+=2300;f.nativeState(false);Thread.sleep(1250);f.settled();ok(f.profile.disconnects==1&&f.profile.connects==1&&f.profile.keys==2);
f=new Fixture(false);f.lease.observedPlay=true;f.link.end(f.lease,true);SystemClock.offset+=2300;f.nativeState(false);Thread.sleep(1250);f.settled();ok(f.profile.disconnects==1&&f.profile.connects==1&&f.profile.keys==2);
System.out.println("PASS: "+checks+" production reconnect-flow checks: same phone, single cycle, silence, pause/call/cancel/opt-out guards, radio/peer changes, failures and return-to-local");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='bt-recovery-flow-') as folder:
 temp=Path(folder);files=[]
 for name,body in sources.items():
  path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
 production=root/'shared/multiroom/bridge-src/com/wallpanel/bridge'
 files.extend(production/name for name in ('BluetoothLink.java','NativeBluetoothPlayback.java','PlaybackTracks.java','BluetoothRecoveryPolicy.java'))
 java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,*map(str,files)],check=True)
 subprocess.run([java,'-cp',folder,'com.wallpanel.bridge.RecoveryFlowTest'],check=True,timeout=20)
