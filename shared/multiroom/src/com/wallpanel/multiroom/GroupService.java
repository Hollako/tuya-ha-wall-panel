package com.wallpanel.multiroom;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.Process;
import com.wallpanel.audioreceiver.AudioOutputGate;

/** Independent, opt-in group processes. No AirPlay process or clock changes. */
public final class GroupService extends Service {
 static final int STREAM_PORT=1704,CONTROL_PORT=1705,INPUT_PORT=1718;
 volatile boolean running,paused,connected,sourceFinished;private volatile boolean closing,callHeld;private boolean preparedOutput;private String role,leader,id;private int bufferMs;volatile int roomDelay;
 private volatile Process server,client;private volatile GroupDecoder decoder;private volatile BluetoothGroupSource bluetooth;private AudioOutputGate.Lease airplayLease;
 private PowerManager.WakeLock wake;private android.net.wifi.WifiManager.WifiLock wifi;private final Handler main=new Handler();private final ExecutorService work=Executors.newSingleThreadExecutor();
 private boolean volumeAfterCall,sourceWaiting;
 private GroupReconnect reconnect;
 void recovery(final boolean value){main.post(()->{if(bluetooth!=null)bluetooth.recovery(value);});}
 void applyVolume(double value){main.post(()->{if(closing)return;if(GroupRuntime.intercom()){volumeAfterCall=true;return;}try{RoomVolume.apply();}catch(IOException error){GroupRuntime.status("Cannot apply room volume");}});}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onCreate(){super.onCreate();GroupRuntime.service=this;
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("wallpanel_groups","Group playback",NotificationManager.IMPORTANCE_LOW));
  startForeground(57,new Notification.Builder(this,"wallpanel_groups").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Panel audio group").setContentText("Group playback active").setOngoing(true).build());
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(running)return START_NOT_STICKY;
  try{
   if(GroupRuntime.intercom())throw new IllegalStateException("End the intercom call first");
   SharedPreferences preferences=getSharedPreferences("wallpanel_groups",MODE_PRIVATE);role=GroupPolicy.role(preferences.getString("role","receiver"));leader=role.equals("leader")?"127.0.0.1":GroupPolicy.host(preferences.getString("leader",""));
   if(!role.equals("leader"))reconnect=new GroupReconnect(preferences.getString("group_leader",""),leader);
   bufferMs=GroupPolicy.buffer(preferences.getInt("buffer_ms",2000));roomDelay=GroupPolicy.roomDelay(preferences.getInt("room_delay_ms",0));
   GroupRuntime.roomTiming(this,roomDelay);
   id=preferences.getString("id","");if(id.isEmpty()){id="panel-"+UUID.randomUUID().toString();preferences.edit().putString("id",id).apply();}
   boolean appsSource=role.equals("leader")&&preferences.getString("source_kind","file").equals("apps");
   boolean bluetoothSource=role.equals("leader")&&preferences.getString("source_kind","file").equals("bluetooth");
   boolean capturedSource=bluetoothSource||appsSource;
   if(appsSource&&!BluetoothGroupSource.appsAvailable())throw new IllegalStateException("Prepare the app audio group bridge first");
   if(bluetoothSource&&!BluetoothGroupSource.available())throw new IllegalStateException("Prepare the Bluetooth group bridge first");
   String url=role.equals("leader")&&!capturedSource?GroupPolicy.source(preferences.getString("source","")):"";
   if(!GroupRuntime.prepare())throw new IllegalStateException("Start the panel audio hardware first");
   preparedOutput=true;
   GroupRuntime.initializeVolume(this,GroupRuntime.volume(this));
   airplayLease=AudioOutputGate.INSTANCE.hold();running=true;applyVolume(GroupRuntime.outputVolume(this));
   wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:group");wake.acquire();
   wifi=((android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"wallpanel:group");wifi.setReferenceCounted(false);wifi.acquire();
   GroupRuntime.status(role.equals("leader")?"Opening the group source…":"Connecting to leader "+leader+"…");
   if(role.equals("leader")){
    GroupDecoder.Sink sink=new GroupDecoder.Sink(){
     public Socket open(int rate,int channels)throws Exception{return startLeader(rate,channels);}
     public void complete(){sourceFinished=true;GroupRuntime.status("Source finished. Stop and start the group to replay or choose another source.");}
     public void error(Exception error){failed(capturedSource?"Media capture failed; check the capture bridge and source app":"Cannot play group source ("+error.getClass().getSimpleName()+")");}
    };if(capturedSource){bluetooth=new BluetoothGroupSource(sink,appsSource);bluetooth.recovery(getSharedPreferences("wpar_prefs",MODE_PRIVATE).getBoolean("bt_handoff_recovery",true));work.execute(bluetooth);}else{decoder=new GroupDecoder(this,url,sink);work.execute(decoder);}
   }else work.execute(()->{try{startClient();}catch(Exception error){main.post(()->retryReceiver("Cannot start group receiver"));}});
   main.post(tick);
  }catch(Exception error){GroupRuntime.status(error.getMessage());stopSelf();}
  return START_NOT_STICKY;
 }
 private Process launch(String binary,String... args)throws IOException{
  File executable=new File(getApplicationInfo().nativeLibraryDir,binary);if(!executable.isFile())throw new IOException("Grouping engine missing");
  ArrayList<String> command=new ArrayList<>();command.add(executable.getAbsolutePath());Collections.addAll(command,args);
  ProcessBuilder builder=new ProcessBuilder(command);builder.environment().put("HOME",getFilesDir().getAbsolutePath());builder.environment().put("TMPDIR",getCacheDir().getAbsolutePath());
  builder.environment().put("GROUP_ROOM_DELAY_FILE",new File(getFilesDir(),"group-room-delay").getAbsolutePath());
  builder.environment().put("GROUP_VOLUME_FILE",new File(getFilesDir(),"group-volume").getAbsolutePath());
  android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
  String rate=audio.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),burst=audio.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
  if(rate!=null&&rate.matches("[0-9]{4,6}"))builder.environment().put("SAMPLE_RATE",rate);
  if(burst!=null&&burst.matches("[0-9]{1,6}"))builder.environment().put("FRAMES_PER_BUFFER",burst);
  try{int latency=((Number)android.media.AudioManager.class.getMethod("getOutputLatency",Integer.TYPE).invoke(audio,android.media.AudioManager.STREAM_MUSIC)).intValue();if(latency>=0&&latency<=500)builder.environment().put("OUTPUT_LATENCY_MS",Integer.toString(latency));}catch(Exception ignored){}
  builder.redirectErrorStream(true);return builder.start();
 }
 private void observe(Process process,String name){new Thread(()->{try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))){String line;while((line=reader.readLine())!=null&&!closing){android.util.Log.i("WallPanelGroup",name+": "+(line.length()>512?line.substring(0,512):line));if(name.equals("receiver")&&process==client){if(line.contains("Reconnecting"))connected=false;if(line.contains("ServerSettings - buffer:")){connected=true;GroupRuntime.status("Connected to "+(role.equals("leader")?"this panel's group":leader)+". Playback timing is managed by the group engine.");}}}}catch(IOException ignored){}},"group-"+name+"-log").start();}
 private Socket startLeader(int rate,int channels)throws Exception{
  if(closing)throw new IOException("Stopped");File data=new File(getFilesDir(),"group-server");data.mkdirs();
  String source="tcp://127.0.0.1:"+INPUT_PORT+"?name=Panel&mode=server&sampleformat="+GroupPolicy.format(rate,channels)+"&codec=pcm";
  // A missing default /etc/snapserver.conf makes upstream skip CLI config overrides too.
  synchronized(this){if(closing)throw new IOException("Stopped");server=launch("libwpar_groupserver.so","--config","/dev/null","--server.datadir",data.getAbsolutePath(),"--server.mdns_enabled","false","--http.enabled","false","--tcp-control.bind_to_address","127.0.0.1","--tcp-control.port",Integer.toString(CONTROL_PORT),"--tcp-streaming.bind_to_address","0.0.0.0","--tcp-streaming.port",Integer.toString(STREAM_PORT),"--stream.codec","pcm","--stream.buffer",Integer.toString(bufferMs),"--stream.source",source);}
  observe(server,"server");Socket input=null;
  for(int attempt=0;attempt<50&&!closing;attempt++){try{input=new Socket();input.connect(new InetSocketAddress("127.0.0.1",INPUT_PORT),200);break;}catch(IOException error){if(input!=null)try{input.close();}catch(IOException ignored){}input=null;if(!server.isAlive())throw new IOException("Group server exited");Thread.sleep(100);}}
  if(input==null||closing){if(input!=null)input.close();throw new IOException("Group source unavailable");}
  try{startClient();return input;}catch(Exception error){input.close();throw error;}
 }
 private synchronized void startClient()throws IOException{
  if(closing||callHeld||paused&&!role.equals("leader")||client!=null&&client.isAlive())return;
  client=launch("libwpar_groupclient.so","--player","oboe","--latency",GroupPolicy.clientLatency(roomDelay),"--hostID",id,"tcp://"+leader+":"+STREAM_PORT);observe(client,"receiver");
 }
 private synchronized void stopClient(){connected=false;Process process=client;client=null;if(process!=null)process.destroy();}
 private void retryReceiver(String message){if(closing||reconnect==null)return;stopClient();reconnect.failed(SystemClock.elapsedRealtime());GroupRuntime.status(message+"; waiting to reconnect to "+leader);}
 void followLeader(String peerId,String address){main.post(()->{
  if(closing||reconnect==null)return;
  try{if(!reconnect.confirmed(peerId,address))return;}catch(IllegalArgumentException invalid){return;}
  leader=reconnect.host();getSharedPreferences("wallpanel_groups",MODE_PRIVATE).edit().putString("leader",leader).apply();
  stopClient();GroupRuntime.status("Leader address updated; reconnecting to "+leader);
 });}
 void networkChanged(){main.post(()->{if(closing||reconnect==null)return;stopClient();reconnect.connected();GroupRuntime.status("Network changed; reconnecting to "+leader);});}
 private final Runnable tick=new Runnable(){public void run(){
  if(closing)return;boolean call=GroupRuntime.intercom();
  if(call&&!callHeld){callHeld=true;if(bluetooth!=null)bluetooth.holdRecovery(true);stopClient();GroupRuntime.status("This room is silenced for intercom; the group continues.");}
  else if(!call&&callHeld){callHeld=false;if(bluetooth!=null)bluetooth.holdRecovery(false);try{startClient();if(volumeAfterCall){volumeAfterCall=false;applyVolume(GroupRuntime.outputVolume(GroupService.this));}}catch(Exception error){if(reconnect!=null)retryReceiver("Cannot resume this room after intercom");else failed("Cannot resume this room after intercom");}}
  if(bluetooth!=null&&bluetooth.bluetooth()&&!paused&&!callHeld){boolean waiting=MediaActivity.bluetoothStalled();if(waiting!=sourceWaiting){sourceWaiting=waiting;GroupRuntime.status(waiting?"Waiting for Bluetooth audio; checking the source stream":"Group source playing");}}
  if(server!=null&&!server.isAlive()){failed("Group leader stopped unexpectedly");return;}
  if(reconnect!=null){
   if(client!=null&&!client.isAlive())retryReceiver("Group receiver disconnected");
   else if(connected)reconnect.connected();
   if(!callHeld&&!paused&&client==null&&reconnect.due(SystemClock.elapsedRealtime()))try{startClient();}catch(IOException error){retryReceiver("Cannot reconnect group receiver");}
  }else if(!callHeld&&client!=null&&!client.isAlive()){failed("Group receiver stopped unexpectedly");return;}
  main.postDelayed(this,200);
 }};
 void transport(String command){main.post(()->{
  if(closing)return;if(command.equals("stop")){stopSelf();return;}
  boolean next=command.equals("pause")||command.equals("play_pause")&&!paused;
  if(!command.equals("play")&&!command.equals("pause")&&!command.equals("play_pause"))return;
  paused=next;if(decoder!=null)decoder.paused=next;if(bluetooth!=null)bluetooth.pause(next);
  if(!role.equals("leader")){if(next)stopClient();else try{startClient();}catch(Exception error){retryReceiver("Cannot resume group receiver");}}
  GroupRuntime.status(next?(role.equals("leader")?"Group source paused":"This room paused"):(role.equals("leader")?"Group source playing":"Connecting to group…"));
 });}
 private void failed(String message){if(closing)return;GroupRuntime.status(message);main.post(this::stopSelf);}
 String captureStatus(){return bluetooth==null?"inactive":bluetooth.status();}
 @Override public void onDestroy(){closing=true;running=false;main.removeCallbacksAndMessages(null);GroupDecoder source=decoder;if(source!=null)source.close();if(bluetooth!=null)bluetooth.close();work.shutdownNow();synchronized(this){stopClient();if(server!=null)server.destroy();}if(airplayLease!=null)airplayLease.close();if(wake!=null&&wake.isHeld())wake.release();if(wifi!=null&&wifi.isHeld())wifi.release();if(GroupRuntime.service==this)GroupRuntime.service=null;if(preparedOutput)GroupRuntime.finished();stopForeground(true);super.onDestroy();}
}
