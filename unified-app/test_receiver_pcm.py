"""Run the production PCM pump with fake Android audio/sockets, including blocked feedback."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
sources={
'android/media/AudioManager.java':'package android.media;public class AudioManager {public static final int STREAM_MUSIC=3;}',
'android/media/AudioFormat.java':'package android.media;public class AudioFormat {public static final int CHANNEL_OUT_STEREO=12,ENCODING_PCM_16BIT=2;}',
'android/media/AudioDeviceInfo.java':'package android.media;public class AudioDeviceInfo {public int getId(){return 1;}}',
'android/media/AudioTimestamp.java':'package android.media;public class AudioTimestamp {public long framePosition,nanoTime;}',
'android/media/AudioTrack.java':'''package android.media;public class AudioTrack {
public static final int STATE_INITIALIZED=1,MODE_STREAM=1,WRITE_NON_BLOCKING=1,SUCCESS=0;
public static volatile AudioTrack latest;public static volatile boolean stall=true;
public volatile float volume=1;public volatile int frames;public volatile boolean released,started;
public AudioTrack(int a,int b,int c,int d,int e,int f){latest=this;}
public static int getMinBufferSize(int a,int b,int c){return 3840;}public int getState(){return 1;}
public void play(){started=true;}public void pause(){}public void flush(){}public void stop(){}
public void release(){released=true;}public int setVolume(float v){if(released)throw new AssertionError("write to retired track");volume=v;return 0;}
public int write(byte[] b,int offset,int length,int mode){if(mode!=WRITE_NON_BLOCKING)throw new AssertionError("blocking output under gate lock");if(stall)return 0;int count=Math.min(length,512);frames+=count/4;return count;}
public int getPlaybackHeadPosition(){return frames;}public int getUnderrunCount(){return 0;}public AudioDeviceInfo getRoutedDevice(){return null;}public boolean getTimestamp(AudioTimestamp value){return false;}}
''',
'android/os/PowerManager.java':'''package android.os;public class PowerManager {public static class WakeLock {private boolean held;public synchronized boolean isHeld(){return held;}public synchronized void acquire(){held=true;}public synchronized void release(){held=false;}}}''',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
'android/os/Process.java':'package android.os;public class Process {public static final int THREAD_PRIORITY_AUDIO=-16;public static int myUid(){return 10050;}public static void setThreadPriority(int p){}}',
'android/util/Log.java':'package android.util;public class Log {public static int w(String t,String s,Throwable e){return 0;}public static int e(String t,String s,Throwable e){return 0;}}',
'android/net/LocalSocketAddress.java':'package android.net;public class LocalSocketAddress {public enum Namespace {ABSTRACT}public LocalSocketAddress(String s,Namespace n){}}',
'android/net/Credentials.java':'package android.net;public class Credentials {public int getUid(){return 10050;}}',
'android/net/LocalServerSocket.java':'''package android.net;import java.io.*;import java.util.concurrent.*;public class LocalServerSocket {
public static volatile LocalServerSocket current;public static final BlockingQueue<LocalSocket> incoming=new LinkedBlockingQueue<LocalSocket>();public volatile boolean closed;
public LocalServerSocket(String name){current=this;}public LocalSocket accept()throws IOException {try{return incoming.take();}catch(InterruptedException e){throw new IOException(e);}}public void close()throws IOException {closed=true;}}
''',
'android/net/LocalSocket.java':'''package android.net;import java.io.*;import java.util.concurrent.*;
public class LocalSocket implements Closeable {
public volatile boolean closed;public final CountDownLatch feedbackBlocked=new CountDownLatch(1);
private final ByteArrayInputStream bytes;public LocalSocket(){this(new byte[0]);}public LocalSocket(byte[] data){bytes=new ByteArrayInputStream(data);}
public void connect(LocalSocketAddress a)throws IOException {if(LocalServerSocket.current==null||LocalServerSocket.current.closed)throw new IOException("closed");LocalServerSocket.incoming.offer(this);}
public Credentials getPeerCredentials(){return new Credentials();}
public InputStream getInputStream(){return new InputStream(){public int read()throws IOException {
int value=bytes.read();if(value>=0)return value;
while(!closed){try{Thread.sleep(2);}catch(InterruptedException e){throw new IOException(e);}}return -1;}
public int read(byte[] b,int offset,int length)throws IOException {if(bytes.available()>0)return bytes.read(b,offset,Math.min(length,bytes.available()));int value=read();if(value<0)return -1;b[offset]=(byte)value;return 1;}};}
public OutputStream getOutputStream(){return new OutputStream(){public void write(int b)throws IOException {feedbackBlocked.countDown();while(!closed){try{Thread.sleep(2);}catch(InterruptedException e){throw new IOException(e);}}throw new IOException("closed");}};}
public void close()throws IOException {closed=true;}}
''',
'com/wallpanel/audioreceiver/PrefStore.java':'package com.wallpanel.audioreceiver;public class PrefStore {public int getLatencyOffsetMs(){return 0;}}',
'com/wallpanel/audioreceiver/PcmPumpHarness.java':'''package com.wallpanel.audioreceiver;
import java.io.*;import java.util.concurrent.*;import android.media.*;import android.net.*;
public class PcmPumpHarness {
static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
static void header(DataOutputStream out,int type,int frames)throws Exception{out.writeInt(0x57504131);out.writeInt(type);out.writeLong(1);out.writeInt(frames);out.writeInt(48000);out.writeInt(0);out.writeInt(1);out.writeLong(0);}
public static void main(String[] args)throws Exception{
AudioOutputGate gate=AudioOutputGate.INSTANCE;gate.select(true);
AudioOutputGate.Lease ring=gate.hold();
ByteArrayOutputStream data=new ByteArrayOutputStream();DataOutputStream frame=new DataOutputStream(data);
header(frame,1,0);header(frame,2,1024);frame.write(new byte[4096]);frame.flush();
LocalSocket peer=new LocalSocket(data.toByteArray());LocalServerSocket.incoming.offer(peer);
android.os.PowerManager.WakeLock wake=new android.os.PowerManager.WakeLock();
PcmPump pump=new PcmPump("fake",new AudioManager(),wake,ReceiverState.INSTANCE,new PrefStore());Thread thread=new Thread(pump);thread.start();
long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while((AudioTrack.latest==null||!AudioTrack.latest.started)&&System.nanoTime()<deadline)Thread.sleep(2);
check(AudioTrack.latest!=null&&AudioTrack.latest.started);check(AudioTrack.latest.volume==0);check(peer.feedbackBlocked.await(2,TimeUnit.SECONDS));
// Feedback is blocked, and AudioTrack write keeps returning zero. Call priority
// must still acquire/release immediately, without either I/O holding its lock.
ExecutorService work=Executors.newSingleThreadExecutor();
Future<AudioOutputGate.Lease> held=work.submit(new Callable<AudioOutputGate.Lease>(){public AudioOutputGate.Lease call(){return AudioOutputGate.INSTANCE.hold();}});
AudioOutputGate.Lease nested=held.get(1,TimeUnit.SECONDS);check(AudioTrack.latest.volume==0);
ring.close();check(AudioTrack.latest.volume==0);nested.close();check(AudioTrack.latest.volume==1);
AudioTrack.stall=false;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while((AudioTrack.latest.frames<1024||!ReceiverState.INSTANCE.isPlaying())&&System.nanoTime()<deadline)Thread.sleep(2);
check(AudioTrack.latest.frames==1024);check(ReceiverState.INSTANCE.isPlaying());
pump.stop();thread.join(2000);check(!thread.isAlive());check(AudioTrack.latest.released);check(!wake.isHeld());check(!gate.streaming());work.shutdownNow();
System.out.println("PASS: "+checks+" production PCM checks: silent track during ringing, nested calls despite blocked feedback/stalled output, partial writes, frame accounting and clean stop");
}}
'''
}
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='receiver-pcm-') as folder:
    temp=Path(folder);files=[]
    for name,body in sources.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
    shared=root.parent/'shared/audio-receiver/src/com/wallpanel/audioreceiver'
    production=[shared/name for name in ('AudioOutputGate.java','PcmPump.java','PresentationClock.java','ReceiverState.java','State.java')]
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';out=temp/'classes';out.mkdir()
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',str(out),*[str(p) for p in production+files]],check=True)
    subprocess.run([java,'-cp',str(out),'com.wallpanel.audioreceiver.PcmPumpHarness'],check=True,timeout=10)
