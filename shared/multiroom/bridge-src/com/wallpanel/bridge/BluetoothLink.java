package com.wallpanel.bridge;
import android.bluetooth.*;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Fixed-purpose, same-phone recovery. Never toggles the radio, unpairs, changes priority or touches another phone. */
final class BluetoothLink {
 private final Context context;private final BluetoothAdapter adapter;private final NativeBluetoothPlayback nativePlayback;private final int owner;
 private volatile BluetoothProfile sink,controller;private volatile Lease current;private final AtomicLong route=new AtomicLong();
 private final AtomicBoolean cycling=new AtomicBoolean();private final BluetoothRecoveryPolicy.Budget budget=new BluetoothRecoveryPolicy.Budget();
 private volatile String recovery="idle";private volatile int attempts;
 private volatile boolean explicitRemotePause;private volatile Boolean remotePlaying;private volatile String remotePeer="";
 BluetoothLink(Context c){this(c,0);}
 BluetoothLink(Context c,int uid){owner=uid;context=c;BluetoothManager manager=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();nativePlayback=new NativeBluetoothPlayback(c);}
 void start(){
  if(owner>=10000){sink=new AppBluetoothProfile(owner,false,adapter);controller=new AppBluetoothProfile(owner,true,adapter);}else{
  IntentFilter events=new IntentFilter("android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT");
  try{context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
   if(cycling.get())return;BluetoothDevice currentPeer=singlePeer(),eventPeer=(BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
   if(currentPeer==null||eventPeer!=null&&!same(currentPeer,eventPeer))return;
   PlaybackState state=(PlaybackState)intent.getParcelableExtra("android.bluetooth.avrcp-controller.profile.extra.PLAYBACK");if(state==null)return;
   remotePeer=currentPeer.getAddress();int mode=state.getState();if(mode==PlaybackState.STATE_PAUSED||mode==PlaybackState.STATE_STOPPED){explicitRemotePause=true;remotePlaying=Boolean.FALSE;userTransport("PAUSE");}
   else if(mode==PlaybackState.STATE_PLAYING||mode==PlaybackState.STATE_BUFFERING){explicitRemotePause=false;remotePlaying=Boolean.TRUE;Lease lease=current;if(lease!=null){lease.observedPlay=true;if(!lease.wanted)userTransport("PLAY");}}
  }},events,"android.permission.BLUETOOTH",new Handler(Looper.getMainLooper()));}catch(Exception unsupported){System.err.println("Bluetooth event observer unavailable: "+unsupported.getClass().getSimpleName());}
  new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){if(adapter==null)return;for(int id:new int[]{11,12})try{adapter.getProfileProxy(context,listener,id);}catch(Exception unavailable){System.err.println("Bluetooth profile "+id+" unavailable: "+unavailable.getClass().getSimpleName());}}});
  }
  Thread monitor=new Thread(new Runnable(){public void run(){while(true){try{if(current!=null||((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).isMusicActive())nativePlayback.refresh();else nativePlayback.inactive();}catch(Exception ignored){}nativePlayback.rest();}}},"bt-native-playback");monitor.setDaemon(true);monitor.start();
 }
 private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
  public void onServiceConnected(int id,BluetoothProfile proxy){if(id==11)sink=proxy;else if(id==12)controller=proxy;}
  public void onServiceDisconnected(int id){if(id==11)sink=null;else if(id==12)controller=null;}
 };
 private List<BluetoothDevice> peers(){try{BluetoothProfile p=sink;if(adapter!=null&&adapter.isEnabled()&&p!=null)return p.getConnectedDevices();}catch(Exception ignored){}return Collections.emptyList();}
 private BluetoothDevice singlePeer(){List<BluetoothDevice> peers=peers();return peers.size()==1?peers.get(0):null;}
 private boolean same(BluetoothDevice a,BluetoothDevice b){return a!=null&&b!=null&&a.getAddress().equals(b.getAddress());}
 private Boolean firmwarePlaying(BluetoothDevice peer){BluetoothProfile p=sink;if(p==null||peer==null)return null;for(String method:new String[]{"isAudioPlaying","isA2dpPlaying"})try{return (Boolean)p.getClass().getMethod(method,BluetoothDevice.class).invoke(p,peer);}catch(Exception ignored){}return null;}
 JSONObject snapshot()throws JSONException{
  List<BluetoothDevice> devices=peers();BluetoothDevice peer=devices.size()==1?devices.get(0):null;PlaybackTracks.Result rendering=nativePlayback.snapshot();Boolean firmware=firmwarePlaying(peer);
  boolean enabled=adapter!=null&&adapter.isEnabled(),ready=sink instanceof AppBluetoothProfile?((AppBluetoothProfile)sink).ready():sink!=null;JSONObject result=new JSONObject().put("enabled",enabled).put("known",ready||!enabled).put("connected",!devices.isEmpty()).put("peerCount",devices.size()).put("renderingKnown",rendering.known).put("rendering",rendering.active).put("nativeProbe",nativePlayback.reason).put("controllerReady",controller instanceof AppBluetoothProfile?((AppBluetoothProfile)controller).ready():controller!=null).put("recovery",recovery).put("recoveryAttempts",attempts);
  boolean sameRemote=peer!=null&&peer.getAddress().equals(remotePeer),paused=sameRemote&&explicitRemotePause||sink instanceof AppBluetoothProfile&&((AppBluetoothProfile)sink).remotePaused();
  result.put("playing",rendering.active||!paused&&(Boolean.TRUE.equals(firmware)||sameRemote&&Boolean.TRUE.equals(remotePlaying)));result.put("remotePaused",paused);String name=peer==null?null:peer.getName();if(name!=null&&name.length()>80)name=name.substring(0,80);result.put("peerName",name==null?JSONObject.NULL:name);return result;
 }
 boolean transport(String verb){
  BluetoothDevice peer=singlePeer();BluetoothProfile p=controller;if(peer==null||p==null)return false;
  int key=verb.equals("PLAY")?0x44:verb.equals("PAUSE")?0x46:verb.equals("NEXT")?0x4b:verb.equals("PREVIOUS")?0x4c:-1;if(key<0)return false;
  try{if(p.getConnectionState(peer)!=BluetoothProfile.STATE_CONNECTED)return false;
   java.lang.reflect.Method send=p.getClass().getMethod("sendPassThroughCmd",BluetoothDevice.class,Integer.TYPE,Integer.TYPE);boolean down=false;
   try{send.invoke(p,peer,Integer.valueOf(key),Integer.valueOf(0));down=true;}finally{if(down)send.invoke(p,peer,Integer.valueOf(key),Integer.valueOf(1));}return true;
  }catch(Exception ignored){return false;}
 }
 void userTransport(String verb){if(verb.equals("PLAY"))explicitRemotePause=false;Lease lease=current;if(lease==null)return;if(verb.equals("PAUSE")){lease.wanted=false;lease.pendingPlay=false;lease.policy.cancel();}else if(verb.equals("PLAY")){lease.wanted=true;lease.policy=new BluetoothRecoveryPolicy(SystemClock.elapsedRealtime());nativePlayback.fast();}}
 boolean playResult(String verb,boolean delivered){Lease lease=current;if(!verb.equals("PLAY")||lease==null||!lease.wanted)return false;if(delivered){lease.pendingPlay=false;return false;}lease.pendingPlay=true;lease.playDeadline=SystemClock.elapsedRealtime()+10000;return true;}
 Lease begin(boolean apps){long generation=route.incrementAndGet();recovery="idle";if(apps){current=null;return null;}Lease lease=new Lease(generation,singlePeer());current=lease;nativePlayback.fast();return lease;}
 void end(final Lease lease,boolean requestedClose){
  if(lease==null)return;boolean restore=requestedClose&&lease.automatic&&lease.observedPlay&&lease.wanted&&!lease.held&&same(lease.peer,singlePeer())&&!remotePaused();
  lease.policy.cancel();if(current==lease)current=null;final long generation=route.incrementAndGet();nativePlayback.fast();
  if(!restore)return;
  final Lease exit=new Lease(generation,lease.peer);exit.exit=true;exit.observedPlay=lease.observedPlay;
  Thread wait=new Thread(new Runnable(){public void run(){long until=SystemClock.elapsedRealtime()+16000;while(SystemClock.elapsedRealtime()<until&&route.get()==generation){exit.check();if(exit.policy.attempted())break;try{Thread.sleep(500);}catch(InterruptedException e){return;}}}},"bt-return-to-local");wait.setDaemon(true);wait.start();
 }
 final class Lease {
  final long generation;volatile BluetoothDevice peer;volatile boolean wanted=true,held,exit,automatic=true,observedPlay,pendingPlay;private boolean initialPlay=true;volatile long lastSignal,lastCheck,playDeadline;volatile BluetoothRecoveryPolicy policy;
  Lease(long value,BluetoothDevice device){generation=value;peer=device;observedPlay=nativePlayback.snapshot().active||BluetoothBridge.remotePlaying()||Boolean.TRUE.equals(firmwarePlaying(device));policy=new BluetoothRecoveryPolicy(SystemClock.elapsedRealtime());}
  boolean current(){return route.get()==generation&&(exit?BluetoothLink.this.current==null:BluetoothLink.this.current==this);}
  synchronized boolean preserveInitialPlayback(String command){if(command.equals("PAUSE")){initialPlay=false;return false;}if(!command.equals("PLAY")||!initialPlay)return false;initialPlay=false;return observedPlay&&wanted&&!held&&!remotePaused();}
  void signal(){observedPlay=true;lastSignal=SystemClock.elapsedRealtime();if(current()&&!cycling.get()&&!recovery.equals("idle"))recovery="restored";}
  void hold(boolean value){held=value;if(value)policy.cancel();}
  void automatic(boolean value){automatic=value;if(!value)policy.cancel();else{policy=new BluetoothRecoveryPolicy(SystemClock.elapsedRealtime());nativePlayback.fast();}}
  void check(){
   long now=SystemClock.elapsedRealtime();if(now-lastCheck<400)return;lastCheck=now;BluetoothDevice connected=singlePeer();if(peer==null)peer=connected;
   if(pendingPlay&&now>playDeadline)pendingPlay=false;
   if(pendingPlay&&current()&&wanted&&!held&&!callActive()&&same(peer,connected)&&transport("PLAY")){pendingPlay=false;policy=new BluetoothRecoveryPolicy(now);}
   PlaybackTracks.Result rendering=nativePlayback.snapshot();if(rendering.active||BluetoothBridge.remotePlaying()||Boolean.TRUE.equals(firmwarePlaying(connected)))observedPlay=true;boolean call=((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).getMode()!=AudioManager.MODE_NORMAL;
   if(policy.observe(now,current(),same(peer,connected),wanted&&observedPlay,held||call||!automatic,remotePaused(),rendering.known,rendering.active,lastSignal>0&&now-lastSignal<1600))recover(this);
  }
 }
 private boolean usable(BluetoothDevice peer){try{return adapter!=null&&adapter.isEnabled()&&peer.getBondState()==BluetoothDevice.BOND_BONDED&&sink!=null;}catch(Exception ignored){return false;}}
 private boolean noOtherPhone(BluetoothDevice original){List<BluetoothDevice> peers=peers();return peers.isEmpty()||peers.size()==1&&same(original,peers.get(0));}
 private boolean callActive(){try{return ((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).getMode()!=AudioManager.MODE_NORMAL;}catch(Exception ignored){return true;}}
 private boolean remotePaused(){BluetoothDevice peer=singlePeer();return sink instanceof AppBluetoothProfile&&((AppBluetoothProfile)sink).remotePaused()||explicitRemotePause&&peer!=null&&peer.getAddress().equals(remotePeer)||BluetoothBridge.remotePaused();}
 private void publish(Lease lease,String state){if(lease.current())recovery=state;}
 private boolean resumeAllowed(Lease lease){return lease.current()&&lease.automatic&&lease.wanted&&!lease.held&&!callActive();}
 private void recover(final Lease lease){
  final BluetoothDevice peer=lease.peer;final String phase=lease.exit?"local":"group";
  // App-side profile access may disconnect with BLUETOOTH_ADMIN while this vendor
  // requires BLUETOOTH_PRIVILEGED to reconnect. Never break a link we cannot restore.
  if(sink instanceof AppBluetoothProfile&&!((AppBluetoothProfile)sink).canReconnect()){publish(lease,"reconnect_unavailable");return;}
  if(peer==null||!resumeAllowed(lease)||!same(peer,singlePeer())||!budget.claim(peer.getAddress(),phase,SystemClock.elapsedRealtime())||!cycling.compareAndSet(false,true)){recovery="manual_required";return;}
  recovery="reconnecting";attempts++;
  Thread worker=new Thread(new Runnable(){public void run(){boolean disconnected=false,connectAttempted=false;
   try{
    if(!resumeAllowed(lease)||!usable(peer)||!same(peer,singlePeer())){publish(lease,"cancelled");return;}
    if(nativePlayback.snapshot().active||lease.lastSignal>0&&SystemClock.elapsedRealtime()-lease.lastSignal<1600){publish(lease,"restored");return;}BluetoothProfile profile=sink;
    if(!Boolean.TRUE.equals(profile.getClass().getMethod("disconnect",BluetoothDevice.class).invoke(profile,peer)))throw new Exception("Disconnect rejected");disconnected=true;
    long deadline=SystemClock.elapsedRealtime()+3500;
    while(usable(peer)&&profile.getConnectionState(peer)!=BluetoothProfile.STATE_DISCONNECTED&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
    if(!usable(peer)||!noOtherPhone(peer))return;
    if(profile.getConnectionState(peer)!=BluetoothProfile.STATE_DISCONNECTED)throw new Exception("Disconnect deadline");
    // Restore the connection even if a pause/stop arrived after our disconnect;
    // resuming music below still requires the original, uncancelled request.
    connectAttempted=true;if(!Boolean.TRUE.equals(profile.getClass().getMethod("connect",BluetoothDevice.class).invoke(profile,peer)))throw new Exception("Connect rejected");
    deadline=SystemClock.elapsedRealtime()+6500;while(usable(peer)&&noOtherPhone(peer)&&profile.getConnectionState(peer)!=BluetoothProfile.STATE_CONNECTED&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
    if(!usable(peer)||!same(peer,singlePeer()))throw new Exception("Reconnect deadline");
    nativePlayback.fast();if(!resumeAllowed(lease)){publish(lease,"cancelled");return;}
    deadline=SystemClock.elapsedRealtime()+2000;boolean sent=false;
    while(resumeAllowed(lease)&&same(peer,singlePeer())&&SystemClock.elapsedRealtime()<deadline){if(transport("PLAY")){sent=true;break;}Thread.sleep(150);}
    publish(lease,sent?"waiting_for_audio":"manual_required");
   }catch(Exception failure){publish(lease,"manual_required");System.err.println("Bluetooth handoff "+phase+": "+failure.getClass().getSimpleName()+" "+(failure.getMessage()==null?"":failure.getMessage()));}finally{
    if(disconnected&&!connectAttempted&&usable(peer)&&noOtherPhone(peer))try{sink.getClass().getMethod("connect",BluetoothDevice.class).invoke(sink,peer);}catch(Exception ignored){}
    cycling.set(false);
   }
  }},"bt-handoff-recovery");worker.setDaemon(true);worker.start();
 }
}
