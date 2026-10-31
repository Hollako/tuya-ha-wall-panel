package com.wallpanel.bridge;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.system.Os;
import java.io.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Root-bootstrap, system-UID worker. One owner, explicit Bluetooth or app-media capture, no microphone. */
public final class BluetoothBridge {
 private static final int BLUETOOTH_UID=1002,RATE=48000;
 private static Context context;private static int owner;private static final AtomicBoolean busy=new AtomicBoolean();
 private static Class<?> ruleClass,mixClass,policyClass;private static HandlerThread callbacks;private static BluetoothLink bluetooth;private static StableBluetoothPipe stable;
 public static void main(String[] args)throws Exception{
  boolean probe=args.length==2&&args[0].equals("--probe");if(args.length!=(probe?2:1))throw new IllegalArgumentException("Owner UID required");
  if(Process.myUid()!=1000)throw new SecurityException("Trusted system-UID bootstrap required");
  owner=Integer.parseInt(args[probe?1:0]);if(owner<10000||owner>19999)throw new SecurityException("Invalid owner");
  if(Looper.myLooper()==null)Looper.prepareMainLooper();
  Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
  context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
  boolean known=false;for(String name:new String[]{"local.wallpanel.app","com.wallpanel.audioreceiver"})try{if(context.getPackageManager().getApplicationInfo(name,0).uid==owner)known=true;}catch(Exception ignored){}
  if(!known||context.getPackageManager().getApplicationInfo("com.android.bluetooth",0).uid!=BLUETOOTH_UID)throw new SecurityException("Unverified owner or Bluetooth UID");
  // The bootstrap dropped root/groups before exec: all ART/Binder threads are UID 1000.
  ruleClass=Class.forName("android.media.audiopolicy.AudioMixingRule");mixClass=Class.forName("android.media.audiopolicy.AudioMix");policyClass=Class.forName("android.media.audiopolicy.AudioPolicy");
  int routing=context.checkSelfPermission("android.permission.MODIFY_AUDIO_ROUTING");int record=context.checkSelfPermission("android.permission.RECORD_AUDIO");
  if(probe){spec(false);spec(true);AudioManager.class.getMethod("registerAudioPolicy",policyClass);AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass);policyClass.getMethod("createAudioRecordSink",mixClass);NativeBluetoothPlayback nativeProbe=new NativeBluetoothPlayback(context);nativeProbe.refresh();System.out.println("{\"uid\":"+Process.myUid()+",\"owner\":"+owner+",\"bluetooth_uid\":1002,\"routing_permission\":"+routing+",\"record_permission\":"+record+",\"policy_api\":true,\"app_media_policy\":true,\"capture_started\":false,\"native_probe\":\""+nativeProbe.reason+"\"}");return;}
  if(routing!=0||record!=0)throw new SecurityException("System audio permissions unavailable");
  callbacks=new HandlerThread("bt-group-policy");callbacks.start();
  bluetooth=new BluetoothLink(context,owner);bluetooth.start();
  // Read only the owner's existing fractional room gain on a separate thread.
  // Never block PCM delivery or attenuate the full-level group capture stream.
  final AppBluetoothProfile volumeOwner=new AppBluetoothProfile(owner,false,null);
  Thread volumeMonitor=new Thread(new Runnable(){public void run(){while(true){BluetoothLocalOutput.gain(volumeOwner.roomGain());try{Thread.sleep(200);}catch(InterruptedException ended){return;}}}},"bt-local-volume");volumeMonitor.setDaemon(true);volumeMonitor.start();
  stable=new StableBluetoothPipe(new StableBluetoothPipe.Factory(){public StableBluetoothPipe.Input open()throws Exception{return permanentInput();}},new BluetoothLocalOutput((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)));stable.start();
  final LocalServerSocket server=new LocalServerSocket("wallpanel.btgroup."+owner);
  new Thread(new Runnable(){public void run(){while(true)try{final LocalSocket socket=server.accept();if(socket.getPeerCredentials().getUid()!=owner){socket.close();continue;}new Thread(new Runnable(){public void run(){serve(socket);}},"bt-group-client").start();}catch(IOException error){System.err.println("Bluetooth bridge listener stopped");System.exit(1);}}},"bt-group-accept").start();
  // Profile callbacks are dispatched here. Previously the blocking accept loop
  // left this looper unserviced, so a profile proxy could never connect.
  Looper.loop();
 }
 private static void line(LocalSocket socket,String text)throws IOException{socket.getOutputStream().write((text+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();}
 private static String command(InputStream in)throws IOException{StringBuilder value=new StringBuilder();for(int i=0;i<24;i++){int b=in.read();if(b<0)return null;if(b=='\n')return value.toString();if(b<32||b>126)throw new IOException("Invalid command");value.append((char)b);}throw new IOException("Command too long");}
 private static void serve(LocalSocket socket){boolean lease=false;AtomicBoolean streaming=new AtomicBoolean();try{
  if(socket.getPeerCredentials().getUid()!=owner)return;socket.setSoTimeout(3000);String command=command(socket.getInputStream());
  if("STATUS".equals(command)){line(socket,"OK BT_GROUP 1 UID "+owner+" SOURCE 1002");return;}
  if("STATUS_APPS".equals(command)){line(socket,"OK APP_GROUP 1 UID "+owner+" SOURCE MEDIA");return;}
  if("STATUS_HANDOFF".equals(command)){line(socket,"OK BT_HANDOFF 1");return;}
  if("MEDIA_STATUS".equals(command)){line(socket,mediaStatus().toString());return;}
  for(String kind:new String[]{"BT","APPS"})for(String verb:new String[]{"PLAY","PAUSE","NEXT","PREVIOUS"})if(command.equals("MEDIA_"+verb+"_"+kind)){if(kind.equals("BT"))bluetooth.userTransport(verb);boolean delivered=transport(verb,kind.equals("APPS"));boolean queued=kind.equals("BT")&&bluetooth.playResult(verb,delivered);line(socket,delivered||queued?"OK MEDIA":"ERROR MEDIA_UNAVAILABLE");return;}
  final boolean apps="CAPTURE_APPS".equals(command);
  if(!"CAPTURE".equals(command)&&!apps){line(socket,"ERROR COMMAND");return;}
  if(!busy.compareAndSet(false,true)){line(socket,"ERROR BUSY");return;}
  lease=true;socket.setSoTimeout(0);if(apps)capture(socket,streaming,true);else captureStable(socket,streaming);
 }catch(Throwable failed){if(!streaming.get())try{line(socket,"ERROR "+cause(failed).getClass().getSimpleName());}catch(Exception ignored){}System.err.println("Bluetooth bridge: "+cause(failed).getClass().getSimpleName());}finally{try{socket.close();}catch(Exception ignored){}if(lease)busy.set(false);}}
 private static Throwable cause(Throwable value){return value instanceof InvocationTargetException?((InvocationTargetException)value).getTargetException():value;}
 private static org.json.JSONObject mediaStatus()throws Exception{
  org.json.JSONArray result=new org.json.JSONArray();MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
  List<MediaController> sessions;try{sessions=manager.getActiveSessions(null);}catch(Exception unavailable){sessions=java.util.Collections.emptyList();}
  for(MediaController session:sessions){
   String pkg=session.getPackageName();boolean bt=pkg.equals("com.android.bluetooth"),allowed=bt;
   if(!bt)try{allowed=AppCapturePolicy.allowed(context.getPackageManager().getApplicationInfo(pkg,0).uid,owner);}catch(Exception ignored){}
   if(!allowed)continue;android.media.session.PlaybackState state=session.getPlaybackState();if(state==null)continue;
   int mode=state.getState();if(mode!=android.media.session.PlaybackState.STATE_PLAYING&&mode!=android.media.session.PlaybackState.STATE_PAUSED&&mode!=android.media.session.PlaybackState.STATE_BUFFERING)continue;
   String title="";android.media.MediaMetadata metadata=session.getMetadata();if(metadata!=null){String text=metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);if(text!=null)title=text.substring(0,Math.min(text.length(),80));}
   result.put(new org.json.JSONObject().put("kind",bt?"bluetooth":"apps").put("package",pkg).put("state",mode==android.media.session.PlaybackState.STATE_PAUSED?"paused":"playing").put("title",title));if(result.length()>=8)break;
  }
  return new org.json.JSONObject().put("sessions",result).put("bluetooth",bluetooth.snapshot()).put("bluetoothRoute",new org.json.JSONObject().put("persistent",true).put("prepared",stable.prepared()).put("opens",stable.opens()).put("frames",stable.frames()).put("localGain",BluetoothLocalOutput.gain()));
 }
 static boolean remotePaused(){try{MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);for(MediaController session:manager.getActiveSessions(null))if(session.getPackageName().equals("com.android.bluetooth")){android.media.session.PlaybackState state=session.getPlaybackState();if(state!=null)return state.getState()==android.media.session.PlaybackState.STATE_PAUSED||state.getState()==android.media.session.PlaybackState.STATE_STOPPED;}}catch(Exception ignored){}return false;}
 static boolean remotePlaying(){try{MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);for(MediaController session:manager.getActiveSessions(null))if(session.getPackageName().equals("com.android.bluetooth")){android.media.session.PlaybackState state=session.getPlaybackState();if(state!=null)return state.getState()==android.media.session.PlaybackState.STATE_PLAYING||state.getState()==android.media.session.PlaybackState.STATE_BUFFERING;}}catch(Exception ignored){}return false;}
 private static boolean transport(String command,boolean apps){try{
  if(!command.equals("PLAY")&&!command.equals("PAUSE")&&!command.equals("NEXT")&&!command.equals("PREVIOUS"))return false;
  MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);List<MediaController> sessions=manager.getActiveSessions(null);
  for(MediaController session:sessions){boolean eligible=session.getPackageName().equals("com.android.bluetooth");if(apps)try{eligible=AppCapturePolicy.allowed(context.getPackageManager().getApplicationInfo(session.getPackageName(),0).uid,owner);}catch(Exception ignored){eligible=false;}
    if(eligible){android.media.session.PlaybackState state=session.getPlaybackState();if(command.equals("PAUSE")&&(state==null||state.getState()!=android.media.session.PlaybackState.STATE_PLAYING))continue;if(command.equals("PLAY"))session.getTransportControls().play();else if(command.equals("PAUSE"))session.getTransportControls().pause();else if(command.equals("NEXT"))session.getTransportControls().skipToNext();else session.getTransportControls().skipToPrevious();return true;}}
 }catch(Exception ignored){}return !apps&&bluetooth.transport(command);}
 private static Object[] spec(boolean apps)throws Exception{
   Class<?> rb=Class.forName("android.media.audiopolicy.AudioMixingRule$Builder");Object rules=rb.newInstance();
   rb.getMethod("addRule",AudioAttributes.class,int.class).invoke(rules,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),ruleClass.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null));
   // Android 8 vendor builders dereference an earlier UID criterion when adding usage.
   // Add usage first; both criteria remain required, including on newer Android.
   if(apps){
     // Usage first, then a small system/feedback exclusion list; firmware accepts at most 20 criteria.
     rb.getMethod("addRule",AudioAttributes.class,int.class).invoke(rules,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build(),ruleClass.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null));
     Object users=context.getSystemService(Context.USER_SERVICE);
     if(((java.util.List<?>)users.getClass().getMethod("getUsers").invoke(users)).size()!=1)throw new SecurityException("App capture requires a single-user panel");
     java.util.List<android.content.pm.ApplicationInfo> installed=context.getPackageManager().getInstalledApplications(0);int[] uids=new int[installed.size()];for(int i=0;i<uids.length;i++)uids[i]=installed.get(i).uid;
     for(int uid:AppCapturePolicy.exclusions(owner,uids))rb.getMethod("excludeMixRule",int.class,Object.class).invoke(rules,ruleClass.getField("RULE_MATCH_UID").getInt(null),Integer.valueOf(uid));
   }else rb.getMethod("addMixRule",int.class,Object.class).invoke(rules,ruleClass.getField("RULE_MATCH_UID").getInt(null),Integer.valueOf(BLUETOOTH_UID));
   Object rule=rb.getMethod("build").invoke(rules);Class<?> mb=Class.forName("android.media.audiopolicy.AudioMix$Builder");Object mixBuilder=mb.getConstructor(ruleClass).newInstance(rule);
   mb.getMethod("setFormat",AudioFormat.class).invoke(mixBuilder,new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build());
   // LOOP_BACK alone redirects the original Bluetooth track; no direct, early copy.
   mb.getMethod("setRouteFlags",int.class).invoke(mixBuilder,mixClass.getField("ROUTE_FLAG_LOOP_BACK").getInt(null));Object mix=mb.getMethod("build").invoke(mixBuilder);
   Class<?> pb=Class.forName("android.media.audiopolicy.AudioPolicy$Builder");Object policyBuilder=pb.getConstructor(Context.class).newInstance(context);pb.getMethod("addMix",mixClass).invoke(policyBuilder,mix);pb.getMethod("setLooper",Looper.class).invoke(policyBuilder,callbacks==null?Looper.getMainLooper():callbacks.getLooper());Object policy=pb.getMethod("build").invoke(policyBuilder);return new Object[]{policy,mix};
 }
 private static StableBluetoothPipe.Input permanentInput()throws Exception{
  final AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);Object[] route=spec(false);final Object policy=route[0];AudioRecord opened=null;
  if(((Integer)AudioManager.class.getMethod("registerAudioPolicy",policyClass).invoke(manager,policy)).intValue()!=0)throw new IOException("Bluetooth policy rejected");
  try{opened=(AudioRecord)policyClass.getMethod("createAudioRecordSink",mixClass).invoke(policy,route[1]);if(opened==null||opened.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Bluetooth record unavailable");opened.startRecording();if(opened.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Bluetooth record not started");}
  catch(Exception failure){if(opened!=null)opened.release();AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass).invoke(manager,policy);throw failure;}
  final AudioRecord record=opened;
  return new StableBluetoothPipe.Input(){public int read(byte[] buffer){return record.read(buffer,0,buffer.length);}public void close(){try{record.stop();}catch(Exception ignored){}record.release();try{AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass).invoke(manager,policy);}catch(Exception ignored){}}};
 }
 private static void captureStable(final LocalSocket socket,AtomicBoolean streaming)throws Exception{
  final boolean alreadyPlaying=remotePlaying()||bluetooth.snapshot().optBoolean("rendering");final StableBluetoothPipe.Subscriber subscriber=stable.attach();final AtomicBoolean first=new AtomicBoolean(true);
  try{
   Thread controls=new Thread(new Runnable(){public void run(){try{String command;while((command=command(socket.getInputStream()))!=null){if(command.equals("CLOSE"))break;if(command.equals("PLAY")){if(first.getAndSet(false)&&alreadyPlaying)continue;transport(command,false);}else if(command.equals("PAUSE")){first.set(false);transport(command,false);}else if(!command.equals("HOLD")&&!command.equals("RELEASE")&&!command.equals("RECOVERY_ON")&&!command.equals("RECOVERY_OFF"))break;}}catch(Exception ignored){}finally{subscriber.close();try{socket.close();}catch(Exception ignored){}}}},"bt-stable-client");controls.setDaemon(true);controls.start();
   line(socket,"OK BT_CAPTURE 48000 2 S16LE");streaming.set(true);byte[] pcm;while((pcm=subscriber.read())!=null)socket.getOutputStream().write(pcm);
  }finally{subscriber.close();}
 }
 private static void capture(final LocalSocket socket,AtomicBoolean streaming,final boolean apps)throws Exception{
  Object policy=null;AudioRecord input=null;AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);final BluetoothLink.Lease lease=bluetooth.begin(apps);final AtomicBoolean requestedClose=new AtomicBoolean();
  try{
   Object[] spec=spec(apps);policy=spec[0];Object mix=spec[1];
   if(((Integer)AudioManager.class.getMethod("registerAudioPolicy",policyClass).invoke(manager,policy)).intValue()!=0)throw new IOException("Policy rejected");
   input=(AudioRecord)policyClass.getMethod("createAudioRecordSink",mixClass).invoke(policy,mix);if(input==null||input.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Capture unavailable");
   input.startRecording();if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Capture start failed");
   final AudioRecord record=input;final AtomicBoolean stopped=new AtomicBoolean();
   Thread end=new Thread(new Runnable(){public void run(){try{String command;while((command=command(socket.getInputStream()))!=null){if(command.equals("CLOSE")){requestedClose.set(true);break;}if(command.equals("RECOVERY_ON")||command.equals("RECOVERY_OFF")){if(lease!=null)lease.automatic(command.equals("RECOVERY_ON"));continue;}if(command.equals("HOLD")||command.equals("RELEASE")){if(lease!=null)lease.hold(command.equals("HOLD"));continue;}if(!command.equals("PLAY")&&!command.equals("PAUSE"))break;if(lease!=null&&lease.preserveInitialPlayback(command))continue;if(!apps)bluetooth.userTransport(command);boolean delivered=transport(command,apps);if(!apps)bluetooth.playResult(command,delivered);}}catch(Exception ignored){}finally{stopped.set(true);try{record.stop();}catch(Exception ignored){}try{socket.close();}catch(Exception ignored){}}}},"bt-group-lease");end.setDaemon(true);end.start();
   line(socket,apps?"OK APP_CAPTURE 48000 2 S16LE":"OK BT_CAPTURE 48000 2 S16LE");streaming.set(true);byte[] pcm=new byte[3840];OutputStream out=socket.getOutputStream();
   while(!stopped.get()){int n=input.read(pcm,0,pcm.length);if(n<0)throw new IOException("Capture stopped");if(n==0){Thread.sleep(2);continue;}if(n%4!=0)throw new IOException("Incomplete frame");if(lease!=null){for(int i=0;i+1<n;i+=2)if(Math.abs((short)((pcm[i]&255)|(pcm[i+1]<<8)))>16){lease.signal();break;}lease.check();}out.write(pcm,0,n);}
  }finally{
   if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();}
   if(policy!=null)try{AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass).invoke(manager,policy);}catch(Exception ignored){}
   bluetooth.end(lease,requestedClose.get());
  }
 }
}
