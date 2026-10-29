package com.wallpanel.audioreceiver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.net.LocalSocket;
import java.io.*;
public class NativeStack {
 private final Context context;private final PrefStore prefs;private final ReceiverState state;private final LogBuf logBuf;
 private final Handler mainHandler=new Handler(Looper.getMainLooper());
 private final File configFile,audioFifo,metaFifo;
 private String audioSocketName="wpar-audio-"+android.os.Process.myUid()+"-"+Long.toHexString(System.nanoTime());
 public String getAudioSocketName(){return audioSocketName;}
 public String getControlSocketName(){return audioSocketName+"-control";}
 public void newAudioSocketSession(){audioSocketName="wpar-audio-"+android.os.Process.myUid()+"-"+Long.toHexString(System.nanoTime());}
 private Process process;private LocalSocket lease;private volatile boolean stopped=true;private volatile int generation=0;
 public NativeStack(Context c,PrefStore p,ReceiverState s,LogBuf l){context=c;prefs=p;state=s;logBuf=l;
  File dir=c.getFilesDir();configFile=new File(dir,"etc/shairport-sync.conf");audioFifo=new File(dir,"fifo/audio.fifo");metaFifo=new File(dir,"fifo/meta.fifo");}
 public File getAudioFifo(){return audioFifo;}public File getMetaFifo(){return metaFifo;}
 public void prepare()throws IOException {
  configFile.getParentFile().mkdirs();audioFifo.getParentFile().mkdirs();
  for(File f:new File[]{metaFifo})try {android.system.Os.mkfifo(f.toString(),0600);}catch(android.system.ErrnoException e){if(e.errno!=android.system.OsConstants.EEXIST)throw new IOException(e);try {if(!android.system.OsConstants.S_ISFIFO(android.system.Os.lstat(f.toString()).st_mode))throw new IOException("Expected FIFO");}catch(android.system.ErrnoException x){throw new IOException(x);}}
  if(!new File(context.getApplicationInfo().nativeLibraryDir,"libwpar_shairport.so").isFile())throw new IOException("Missing Shairport executable");
  writeConfig();
 }
  private void writeConfig() throws IOException {
    String name = prefs.getReceiverName().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    int rtspPort = prefs.getRtspPort();
    int latencyMs = prefs.getLatencyOffsetMs();
    int verbosity = prefs.getLogVerbosity();

    StringBuilder sb = new StringBuilder();
    sb.append("general = {\n");
    sb.append("  name = \"").append(name).append("\";\n");
    sb.append("  output_backend = \"android\";\n");
    sb.append("  mdns_backend = \"tinysvcmdns\";\n");
    sb.append("  interpolation = \"vernier\";\n");
    sb.append("  port = ").append(rtspPort).append(";\n");
    sb.append("  service_type = \"auto\";\n");
    String iface=ReceiverNetwork.interfaceName(context,prefs);
    if(iface==null||!iface.matches("[A-Za-z0-9_.-]{1,32}"))throw new IOException("Waiting for receiver interface");
    sb.append("  interface = \"").append(iface).append("\";\n");
    if (latencyMs != 0) {
      sb.append("  audio_backend_latency_offset_in_seconds = ").append(latencyMs / 1000.0).append(";\n");
    }
    sb.append("};\n");
    // iOS may sleep during a long pause. Retain a bounded control connection
    // rather than declaring it dead after the upstream 60-second default.
    sb.append("sessioncontrol = { session_timeout = 1800; };\n");
    sb.append("android = {\n");
    sb.append("  output_rate = 48000;\n");
    sb.append("  output_format = \"S16_LE\";\n");
    sb.append("  output_channels = 2;\n");
    sb.append("};\n");
    sb.append("metadata = {\n");
    sb.append("  enabled = \"yes\";\n");
    sb.append("  include_cover_art = \"yes\";\n");
    sb.append("  pipe_name = \"").append(metaFifo.getAbsolutePath()).append("\";\n");
    sb.append("  pipe_timeout = 2000;\n");
    sb.append("  cover_art_cache_directory = ").append((char)34).append(new File(context.getCacheDir(), "coverart").getAbsolutePath()).append((char)34).append(";\n");
    sb.append("};\n");
    sb.append("diagnostics = {\n");
    sb.append("  log_verbosity = ").append(verbosity).append(";\n");
    sb.append("  statistics = \"yes\";\n");
    sb.append("};\n");

    try (FileWriter fw = new FileWriter(configFile)) {
      fw.write(sb.toString());
    }
  }


 public synchronized void start(){
  stop();stopped=false;final int attempt=++generation;
  mainHandler.post(()->{state.setState(State.STARTING);state.setErrorMessage(null);state.setRootAvailable(false);state.setSuCommand("authenticated PTP helper");});
  new Thread(()->run(attempt),"wpar-native").start();
 }
 private void run(int attempt){
  try {
   LocalSocket socket=null;IOException failure=null;BufferedReader reader=null;
   for(int retry=0;retry<40&&!stopped&&attempt==generation;retry++){
    try{
     LocalSocket candidate=PrivilegeChannel.open("PTP");
     BufferedReader input=new BufferedReader(new InputStreamReader(candidate.getInputStream(),"US-ASCII"));
     String reply=input.readLine();
     if(reply!=null&&reply.startsWith("OK PTP_LEASE ")){socket=candidate;reader=input;candidate.setSoTimeout(0);break;}
     candidate.close();failure=new IOException("PTP helper busy or rejected lease");
    }catch(IOException e){failure=e;}
    Thread.sleep(500);
   }
   if(socket==null)throw new IOException("PTP helper unavailable",failure);
   final BufferedReader control=reader;
   synchronized(this){if(stopped||attempt!=generation){socket.shutdownOutput();socket.close();return;}lease=socket;}
   ProcessBuilder pb=new ProcessBuilder(new File(context.getApplicationInfo().nativeLibraryDir,"libwpar_shairport.so").toString(),"-v","-c",configFile.toString());
   pb.environment().put("WPAR_AUDIO_SOCKET",audioSocketName);
   pb.environment().put("WPAR_CONTROL_SOCKET",getControlSocketName());
   pb.environment().put("WPAR_SHM_DIR","/data/local/wpar-airplay/shm");pb.environment().put("TMPDIR",context.getCacheDir().toString());pb.redirectErrorStream(true);
   Process child;
   synchronized(this){if(stopped||attempt!=generation)return;process=pb.start();child=process;ReceiverRemote.attach(getControlSocketName());}
   new Thread(()->{try {while(control.readLine()!=null){}synchronized(this){if(!stopped&&attempt==generation){child.destroy();}}}catch(IOException ignored){}},"wpar-ptp-lease").start();
   new Thread(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(child.getInputStream()))){String line;while((line=r.readLine())!=null)logBuf.append(line);}catch(IOException ignored){}},"wpar-log").start();
   Thread.sleep(1000);
   synchronized(this){if(stopped||attempt!=generation)return;if(!child.isAlive())throw new IOException("Shairport exited during startup; inspect logs");}
   mainHandler.post(()->{if(!stopped&&attempt==generation){state.setRootAvailable(true);state.setUptimeStartMs(System.currentTimeMillis());state.setState(State.RUNNING_IDLE);}});
   int rc=child.waitFor();throw new IOException("Shairport exited: "+rc);
  }catch(Exception e){synchronized(this){if(stopped||attempt!=generation)return;stop();}logBuf.append("Receiver error: "+e.toString());mainHandler.post(()->{state.setErrorMessage(e.toString());state.setState(State.ERROR);});}
 }
 public synchronized void stop(){ReceiverRemote.detach(getControlSocketName());stopped=true;++generation;if(process!=null){process.destroy();process=null;}if(lease!=null){try{lease.shutdownOutput();}catch(IOException ignored){}try{lease.shutdownInput();}catch(IOException ignored){}try{lease.close();}catch(IOException ignored){}lease=null;}state.setRootAvailable(false);}
}
