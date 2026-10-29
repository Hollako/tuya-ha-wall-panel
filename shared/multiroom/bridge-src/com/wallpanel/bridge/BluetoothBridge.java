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

/** Root-bootstrap, system-UID worker. One owner, Bluetooth UID/media only, no microphone. */
public final class BluetoothBridge {
 private static final int BLUETOOTH_UID=1002,RATE=48000;
 private static Context context;private static int owner;private static final AtomicBoolean busy=new AtomicBoolean();
 private static Class<?> ruleClass,mixClass,policyClass;private static HandlerThread callbacks;
 public static void main(String[] args)throws Exception{
  boolean probe=args.length==2&&args[0].equals("--probe");if(args.length!=(probe?2:1))throw new IllegalArgumentException("Owner UID required");
  if(Process.myUid()==0&&probe){Os.setgid(1000);Os.setuid(1000);}else if(Process.myUid()!=1000)throw new SecurityException("Trusted system-UID bootstrap required");
  owner=Integer.parseInt(args[probe?1:0]);if(owner<10000||owner>19999)throw new SecurityException("Invalid owner");
  if(Looper.myLooper()==null)Looper.prepareMainLooper();
  Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
  context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
  boolean known=false;for(String name:new String[]{"local.wallpanel.app","com.wallpanel.audioreceiver"})try{if(context.getPackageManager().getApplicationInfo(name,0).uid==owner)known=true;}catch(Exception ignored){}
  if(!known||context.getPackageManager().getApplicationInfo("com.android.bluetooth",0).uid!=BLUETOOTH_UID)throw new SecurityException("Unverified owner or Bluetooth UID");
  // The bootstrap dropped root/groups before exec: all ART/Binder threads are UID 1000.
  ruleClass=Class.forName("android.media.audiopolicy.AudioMixingRule");mixClass=Class.forName("android.media.audiopolicy.AudioMix");policyClass=Class.forName("android.media.audiopolicy.AudioPolicy");
  int routing=context.checkSelfPermission("android.permission.MODIFY_AUDIO_ROUTING");int record=context.checkSelfPermission("android.permission.RECORD_AUDIO");
  if(probe){spec();AudioManager.class.getMethod("registerAudioPolicy",policyClass);AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass);policyClass.getMethod("createAudioRecordSink",mixClass);System.out.println("{\"uid\":"+Process.myUid()+",\"owner\":"+owner+",\"bluetooth_uid\":1002,\"routing_permission\":"+routing+",\"record_permission\":"+record+",\"policy_api\":true,\"capture_started\":false}");return;}
  if(routing!=0||record!=0)throw new SecurityException("System audio permissions unavailable");
  callbacks=new HandlerThread("bt-group-policy");callbacks.start();
  LocalServerSocket server=new LocalServerSocket("wallpanel.btgroup."+owner);
  while(true){final LocalSocket socket=server.accept();if(socket.getPeerCredentials().getUid()!=owner){socket.close();continue;}new Thread(new Runnable(){public void run(){serve(socket);}},"bt-group-client").start();}
 }
 private static void line(LocalSocket socket,String text)throws IOException{socket.getOutputStream().write((text+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();}
 private static String command(InputStream in)throws IOException{StringBuilder value=new StringBuilder();for(int i=0;i<24;i++){int b=in.read();if(b<0)return null;if(b=='\n')return value.toString();if(b<32||b>126)throw new IOException("Invalid command");value.append((char)b);}throw new IOException("Command too long");}
 private static void serve(LocalSocket socket){boolean lease=false;AtomicBoolean streaming=new AtomicBoolean();try{
  if(socket.getPeerCredentials().getUid()!=owner)return;socket.setSoTimeout(3000);String command=command(socket.getInputStream());
  if("STATUS".equals(command)){line(socket,"OK BT_GROUP 1 UID "+owner+" SOURCE 1002");return;}
  if(!"CAPTURE".equals(command)||!busy.compareAndSet(false,true)){line(socket,"ERROR UNAVAILABLE");return;}
  lease=true;socket.setSoTimeout(0);capture(socket,streaming);
 }catch(Throwable failed){if(!streaming.get())try{line(socket,"ERROR "+cause(failed).getClass().getSimpleName());}catch(Exception ignored){}System.err.println("Bluetooth bridge: "+cause(failed).getClass().getSimpleName());}finally{try{socket.close();}catch(Exception ignored){}if(lease)busy.set(false);}}
 private static Throwable cause(Throwable value){return value instanceof InvocationTargetException?((InvocationTargetException)value).getTargetException():value;}
 private static void transport(String command){try{
  if(!command.equals("PLAY")&&!command.equals("PAUSE"))return;
  MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);List<MediaController> sessions=manager.getActiveSessions(null);
  for(MediaController session:sessions)if(session.getPackageName().equals("com.android.bluetooth")){if(command.equals("PLAY"))session.getTransportControls().play();else session.getTransportControls().pause();return;}
 }catch(Exception ignored){}}
 private static Object[] spec()throws Exception{
   Class<?> rb=Class.forName("android.media.audiopolicy.AudioMixingRule$Builder");Object rules=rb.newInstance();
   rb.getMethod("addRule",AudioAttributes.class,int.class).invoke(rules,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),ruleClass.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null));
   // Android 8 vendor builders dereference an earlier UID criterion when adding usage.
   // Add usage first; both criteria remain required, including on newer Android.
   rb.getMethod("addMixRule",int.class,Object.class).invoke(rules,ruleClass.getField("RULE_MATCH_UID").getInt(null),Integer.valueOf(BLUETOOTH_UID));
   Object rule=rb.getMethod("build").invoke(rules);Class<?> mb=Class.forName("android.media.audiopolicy.AudioMix$Builder");Object mixBuilder=mb.getConstructor(ruleClass).newInstance(rule);
   mb.getMethod("setFormat",AudioFormat.class).invoke(mixBuilder,new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build());
   // LOOP_BACK alone redirects the original Bluetooth track; no direct, early copy.
   mb.getMethod("setRouteFlags",int.class).invoke(mixBuilder,mixClass.getField("ROUTE_FLAG_LOOP_BACK").getInt(null));Object mix=mb.getMethod("build").invoke(mixBuilder);
   Class<?> pb=Class.forName("android.media.audiopolicy.AudioPolicy$Builder");Object policyBuilder=pb.getConstructor(Context.class).newInstance(context);pb.getMethod("addMix",mixClass).invoke(policyBuilder,mix);pb.getMethod("setLooper",Looper.class).invoke(policyBuilder,callbacks==null?Looper.getMainLooper():callbacks.getLooper());Object policy=pb.getMethod("build").invoke(policyBuilder);return new Object[]{policy,mix};
 }
 private static void capture(final LocalSocket socket,AtomicBoolean streaming)throws Exception{
  Object policy=null;AudioRecord input=null;AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
  try{
   Object[] spec=spec();policy=spec[0];Object mix=spec[1];
   if(((Integer)AudioManager.class.getMethod("registerAudioPolicy",policyClass).invoke(manager,policy)).intValue()!=0)throw new IOException("Policy rejected");
   input=(AudioRecord)policyClass.getMethod("createAudioRecordSink",mixClass).invoke(policy,mix);if(input==null||input.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Capture unavailable");
   input.startRecording();if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Capture start failed");
   final AudioRecord record=input;final AtomicBoolean stopped=new AtomicBoolean();
   Thread end=new Thread(new Runnable(){public void run(){try{String command;while((command=command(socket.getInputStream()))!=null){if(!command.equals("PLAY")&&!command.equals("PAUSE"))break;transport(command);}}catch(Exception ignored){}finally{stopped.set(true);try{record.stop();}catch(Exception ignored){}try{socket.close();}catch(Exception ignored){}}}},"bt-group-lease");end.setDaemon(true);end.start();
   line(socket,"OK BT_CAPTURE 48000 2 S16LE");streaming.set(true);byte[] pcm=new byte[3840];OutputStream out=socket.getOutputStream();
   while(!stopped.get()){int n=input.read(pcm,0,pcm.length);if(n<0)throw new IOException("Capture stopped");if(n==0){Thread.sleep(2);continue;}if(n%4!=0)throw new IOException("Incomplete frame");out.write(pcm,0,n);}
  }finally{
   if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();}
   if(policy!=null)try{AudioManager.class.getMethod("unregisterAudioPolicyAsync",policyClass).invoke(manager,policy);}catch(Exception ignored){}
  }
 }
}
