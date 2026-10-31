package com.wallpanel.audioreceiver;
import android.bluetooth.*;
import android.content.*;
import android.media.session.PlaybackState;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bluetooth bindings must belong to a real Android application process. */
public final class BluetoothProfiles {
 private static BluetoothProfiles instance;
 private final Context context;private final BluetoothAdapter adapter;
 private volatile BluetoothProfile sink,controller;private volatile String remoteAddress="";private volatile Boolean remotePlaying;
 private volatile LocalServerSocket server;
 private final IncomingMusicPolicy incoming=new IncomingMusicPolicy();private Handler incomingWorker;private volatile String incomingStatus="idle";private volatile int incomingAttempts;
 private static final String MUSIC_CONNECTION="android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
 private final MusicPhonePolicy musicPhone=new MusicPhonePolicy();private Handler profileWorker;
 public static synchronized void start(Context c){if(instance==null){instance=new BluetoothProfiles(c.getApplicationContext());instance.start();}}
 private BluetoothProfiles(Context c){context=c;BluetoothManager manager=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();}
 private void start(){
  HandlerThread worker=new HandlerThread("bt-phone-profiles");worker.start();profileWorker=new Handler(worker.getLooper());if(Build.VERSION.SDK_INT<=28)incomingWorker=profileWorker;
  IntentFilter events=new IntentFilter("android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT");
  events.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);events.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);events.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);events.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
  events.addAction(MUSIC_CONNECTION);
  context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){
   if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())){if(i.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR)!=BluetoothAdapter.STATE_ON){incoming.radioOff();musicPhone.clear();}return;}
   BluetoothDevice peer=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
   if(peer!=null&&MUSIC_CONNECTION.equals(i.getAction())){musicState(peer,i.getIntExtra(BluetoothProfile.EXTRA_STATE,-1));return;}
   if(peer!=null&&(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction())||BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(i.getAction()))){incoming.disconnected(peer.getAddress());return;}
   if(peer!=null&&BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())){incomingLink(peer);return;}
   PlaybackState state=i.getParcelableExtra("android.bluetooth.avrcp-controller.profile.extra.PLAYBACK");
   if(peer==null||state==null||!musicPhone.allows(peer.getAddress()))return;int value=state.getState();if(value==PlaybackState.STATE_PLAYING||value==PlaybackState.STATE_BUFFERING){remoteAddress=peer.getAddress();remotePlaying=true;}
   else if(value==PlaybackState.STATE_PAUSED||value==PlaybackState.STATE_STOPPED){remoteAddress=peer.getAddress();remotePlaying=false;}
  }},events,"android.permission.BLUETOOTH",new Handler(Looper.getMainLooper()));
  final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){public void onServiceConnected(int id,BluetoothProfile p){if(id==11){sink=p;try{for(BluetoothDevice peer:p.getConnectedDevices())musicState(peer,BluetoothProfile.STATE_CONNECTED);}catch(Exception ignored){}}else if(id==12)controller=p;}public void onServiceDisconnected(int id){if(id==11){sink=null;musicPhone.clear();}else if(id==12)controller=null;}};
  if(adapter!=null)for(int id:new int[]{11,12})try{adapter.getProfileProxy(context,listener,id);}catch(Exception ignored){}
  Thread accept=new Thread(()->{try{server=new LocalServerSocket("wallpanel.btprofiles."+android.os.Process.myUid());while(true){final LocalSocket socket=server.accept();Thread client=new Thread(()->serve(socket),"bt-profile-client");client.setDaemon(true);client.start();}}catch(IOException e){android.util.Log.e("WallPanelBluetooth","Profile endpoint stopped",e);}},"bt-profile-server");accept.setDaemon(true);accept.start();
 }
 private void musicState(final BluetoothDevice phone,int state){
  if(musicPhone.observe(phone.getAddress(),state))return;
  incoming.disconnected(phone.getAddress());
  profileWorker.post(()->{BluetoothProfile profile=sink;if(profile==null||adapter==null||!adapter.isEnabled()||musicPhone.allows(phone.getAddress()))return;
   try{int actual=profile.getConnectionState(phone);if(actual!=BluetoothProfile.STATE_CONNECTED&&actual!=BluetoothProfile.STATE_CONNECTING)return;
    boolean accepted=Boolean.TRUE.equals(profile.getClass().getMethod("disconnect",BluetoothDevice.class).invoke(profile,phone));android.util.Log.i("WallPanelBluetooth","Second music device refused; current connection retained: "+accepted);
   }catch(Exception failure){android.util.Log.w("WallPanelBluetooth","Cannot refuse second music device: "+failure.getClass().getSimpleName());}
  });
 }
 public static boolean musicConnectionAllowed(BluetoothProfile profile,BluetoothDevice phone){
  for(BluetoothDevice peer:profile.getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_DISCONNECTING}))if(!peer.getAddress().equals(phone.getAddress()))return false;
  BluetoothProfiles current=instance;return current==null||current.musicPhone.allows(phone.getAddress());
 }
 public static boolean connectMusic(BluetoothProfile profile,BluetoothDevice phone)throws Exception{
  if(!musicConnectionAllowed(profile,phone))return false;BluetoothProfiles current=instance;
  if(current!=null&&!current.musicPhone.reserve(phone.getAddress()))return false;
  boolean accepted=false;try{int state=profile.getConnectionState(phone);accepted=state==BluetoothProfile.STATE_CONNECTED||state==BluetoothProfile.STATE_CONNECTING||Boolean.TRUE.equals(profile.getClass().getMethod("connect",BluetoothDevice.class).invoke(profile,phone));return accepted;}
  finally{if(!accepted&&current!=null)current.musicPhone.requestFailed(phone.getAddress());}
 }
 private void incomingLink(final BluetoothDevice phone){
  if(incomingWorker==null||phone.getBondState()!=BluetoothDevice.BOND_BONDED)return;
  final IncomingMusicPolicy.Link link=incoming.connected(phone.getAddress(),SystemClock.elapsedRealtime());if(link==null)return;
  incomingWorker.postDelayed(new Runnable(){public void run(){
   long now=SystemClock.elapsedRealtime();if(!incoming.current(link,now))return;BluetoothProfile profile=sink;
   if(profile==null){incomingStatus="waiting_for_profile";incomingWorker.postDelayed(this,200);return;}
   try{
    int state=profile.getConnectionState(phone);boolean other=false;
    for(BluetoothDevice peer:profile.getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_DISCONNECTING}))if(!peer.getAddress().equals(phone.getAddress()))other=true;
    if(!incoming.claim(link,now,phone.getBondState()==BluetoothDevice.BOND_BONDED,adapter!=null&&adapter.isEnabled(),state,other))return;
    incomingAttempts++;boolean accepted=connectMusic(profile,phone);incomingStatus=accepted?"music_connection_requested":"music_request_rejected";
    android.util.Log.i("WallPanelBluetooth","Incoming paired-phone music request: "+(accepted?"accepted":"rejected"));
   }catch(Exception failed){incomingStatus="music_request_unavailable";android.util.Log.w("WallPanelBluetooth","Incoming music request failed: "+failed.getClass().getSimpleName());}
  }},750);
 }
 private List<BluetoothDevice> peers(){try{return adapter!=null&&adapter.isEnabled()&&sink!=null?sink.getConnectedDevices():Collections.emptyList();}catch(Exception e){return Collections.emptyList();}}
 private JSONObject status()throws Exception{
  com.wallpanel.multiroom.RoomVolume.initialize(context);
  JSONArray devices=new JSONArray();for(BluetoothDevice d:peers())devices.put(new JSONObject().put("address",d.getAddress()).put("name",d.getName()).put("state",sink.getConnectionState(d)));
  boolean paused=false,playing=false;for(BluetoothDevice d:peers())if(d.getAddress().equals(remoteAddress)){paused=Boolean.FALSE.equals(remotePlaying);playing=Boolean.TRUE.equals(remotePlaying);}
  return new JSONObject().put("sink",sink!=null).put("controller",controller!=null).put("peers",devices).put("remotePaused",paused).put("playing",playing).put("incomingAssist",incomingStatus).put("incomingAssistAttempts",incomingAttempts).put("roomGain",com.wallpanel.multiroom.RoomVolume.gain());
 }
 private void serve(LocalSocket socket){try{
  // The privileged worker is the sole caller. Neither network clients nor other apps can use this endpoint.
  if(socket.getPeerCredentials().getUid()!=1000)return;socket.setSoTimeout(2000);
  InputStream input=socket.getInputStream();StringBuilder request=new StringBuilder();boolean complete=false;
  for(int n=0;n<80;n++){int b=input.read();if(b=='\n'){complete=true;break;}if(b<32||b>126)return;request.append((char)b);}if(!complete)return;
  String[] fields=request.toString().split(" ");JSONObject response;
  if(fields.length==1&&fields[0].equals("STATUS"))response=status();
  else if(fields.length==2&&fields[1].matches("[0-9A-Fa-f:]{17}")){
   String verb=fields[0];BluetoothDevice device=adapter.getRemoteDevice(fields[1]);List<BluetoothDevice> peers=peers();
   boolean same=peers.size()==1&&peers.get(0).getAddress().equals(device.getAddress());boolean accepted=false;
   if(device.getBondState()==BluetoothDevice.BOND_BONDED&&adapter.isEnabled()){
    if(verb.equals("SINK_STATE")||verb.equals("CTRL_STATE")){
     BluetoothProfile profile=verb.equals("SINK_STATE")?sink:controller;
     response=new JSONObject().put("state",profile==null?-1:profile.getConnectionState(device));
     socket.getOutputStream().write((response.toString()+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();return;
    }
    if(verb.equals("CONNECT")&&sink!=null&&(peers.isEmpty()||same))accepted=connectMusic(sink,device);
    else if(verb.equals("DISCONNECT")&&sink!=null&&same)accepted=Boolean.TRUE.equals(sink.getClass().getMethod("disconnect",BluetoothDevice.class).invoke(sink,device));
    else if(same&&controller!=null&&controller.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED){
     int key=verb.equals("PLAY")?0x44:verb.equals("PAUSE")?0x46:verb.equals("NEXT")?0x4b:verb.equals("PREVIOUS")?0x4c:-1;
     if(key>=0){java.lang.reflect.Method send=controller.getClass().getMethod("sendPassThroughCmd",BluetoothDevice.class,int.class,int.class);boolean down=false;try{send.invoke(controller,device,key,0);down=true;}finally{if(down)send.invoke(controller,device,key,1);}accepted=true;if(verb.equals("PLAY")){remoteAddress=device.getAddress();remotePlaying=null;}}
    }
   }
   response=new JSONObject().put("ok",accepted);
  }else response=new JSONObject().put("ok",false);
  socket.getOutputStream().write((response.toString()+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();
 }catch(Exception failed){Throwable cause=failed instanceof java.lang.reflect.InvocationTargetException?((java.lang.reflect.InvocationTargetException)failed).getTargetException():failed;android.util.Log.w("WallPanelBluetooth","Profile request failed: "+cause.getClass().getSimpleName());try{socket.getOutputStream().write("{\"ok\":false}\n".getBytes("UTF-8"));}catch(Exception ignored){}}finally{try{socket.close();}catch(Exception ignored){}}}
}
