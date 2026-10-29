"""Production source-control client with fake Android sockets; no panel traffic."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
sources={
'android/os/Process.java':'package android.os;public class Process {public static int myUid(){return 10001;}}',
'android/net/Credentials.java':'package android.net;public class Credentials {public int getUid(){return LocalSocket.peerUid;}}',
'android/net/LocalSocketAddress.java':'package android.net;public class LocalSocketAddress {public enum Namespace {ABSTRACT}public final String name;public LocalSocketAddress(String n,Namespace s){name=n;}}',
'android/net/LocalSocket.java':'''package android.net;
import java.io.*;import java.util.*;import java.util.concurrent.*;
public class LocalSocket {
public static volatile int peerUid=10001,session=7,rate=1,delay;public static volatile boolean reject,replace;
public static final List<String> commands=Collections.synchronizedList(new ArrayList<String>());
public static volatile CountDownLatch started=new CountDownLatch(1);private final ByteArrayOutputStream output=new ByteArrayOutputStream();private boolean connected;
public void setSoTimeout(int n)throws IOException{if(!connected)throw new IOException("socket not created");}public void connect(LocalSocketAddress a){connected=true;}public Credentials getPeerCredentials(){return new Credentials();}
public OutputStream getOutputStream(){return output;}public void close()throws IOException{}
public InputStream getInputStream()throws IOException {
String request=output.toString("US-ASCII").trim(),reply;
if(request.equals("STATUS"))reply="OK AP2 "+session+" "+(rate==1?"playing":"paused");
else {commands.add(request);started.countDown();try{Thread.sleep(delay);}catch(InterruptedException stop){throw new IOException("stopped");}
if(replace){session++;replace=false;}
int token=Integer.parseInt(request.split(" ")[1]);reply=token!=session?"ERR STALE":reject?"ERR REJECTED":"OK ACK "+token;
}return new ByteArrayInputStream((reply+"\\n").getBytes("US-ASCII"));
}}''',
'com/wallpanel/audioreceiver/RemoteHarness.java':'''package com.wallpanel.audioreceiver;
import android.net.LocalSocket;import java.util.concurrent.*;
public class RemoteHarness {
static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks+" "+ReceiverRemote.snapshot());}
static void await(java.util.concurrent.Callable<Boolean> condition)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.call()){if(System.nanoTime()>end)throw new AssertionError("timeout "+ReceiverRemote.snapshot());Thread.sleep(10);}}
public static void main(String[] args)throws Exception {try{
check(RemoteProtocol.verb("play").equals("PLAY"));check(RemoteProtocol.verb("pause").equals("PAUSE"));check(RemoteProtocol.verb("play_pause").equals("TOGGLE"));
check(RemoteProtocol.verb("next").equals("NEXT"));check(RemoteProtocol.verb("previous").equals("PREVIOUS"));
for(String bad:new String[]{"STOP","play\\nPAUSE","","pause 7"}){boolean failed=false;try{RemoteProtocol.verb(bad);}catch(IllegalArgumentException expected){failed=true;}check(failed);}
for(String bad:new String[]{"OK AP2 -1 playing","OK AP2 2147483648 paused","OK AP2 1 stopped","OK AP2 1 playing\\n","ERR BUSY"})check(RemoteProtocol.status(bad)==null);
check(RemoteProtocol.status("OK AP2 2147483647 paused").session==2147483647L);check(!RemoteProtocol.acknowledged("OK ACK 8",7));
check(!ReceiverRemote.request("pause"));check(LocalSocket.commands.isEmpty());
ReceiverRemote.attach("first");await(()->ReceiverRemote.available());check(ReceiverRemote.playback().equals("playing"));
check(ReceiverRemote.request("pause"));await(()->"acknowledged".equals(ReceiverRemote.snapshot().get("result")));
check(LocalSocket.commands.get(0).equals("PAUSE 7"));check(ReceiverRemote.playback().equals("playing")); // ACK must not invent paused feedback.
LocalSocket.rate=0;await(()->ReceiverRemote.playback().equals("paused"));check(ReceiverRemote.request("play_pause"));await(()->"acknowledged".equals(ReceiverRemote.snapshot().get("result")));
check(LocalSocket.commands.get(1).equals("TOGGLE 7"));check(ReceiverRemote.playback().equals("paused"));
check(ReceiverRemote.request("next"));await(()->"acknowledged".equals(ReceiverRemote.snapshot().get("result")));check(LocalSocket.commands.get(2).equals("NEXT 7"));
check(ReceiverRemote.request("previous"));await(()->"acknowledged".equals(ReceiverRemote.snapshot().get("result")));check(LocalSocket.commands.get(3).equals("PREVIOUS 7"));check(ReceiverRemote.playback().equals("paused"));
LocalSocket.reject=true;check(ReceiverRemote.request("play"));await(()->"failed".equals(ReceiverRemote.snapshot().get("result")));check(!ReceiverRemote.lastError().isEmpty());LocalSocket.reject=false;
int before=LocalSocket.commands.size();LocalSocket.replace=true;check(ReceiverRemote.request("pause"));await(()->LocalSocket.session==8);Thread.sleep(850);check(LocalSocket.commands.size()==before+1);check(LocalSocket.commands.get(before).equals("PAUSE 7"));
LocalSocket.delay=1800;LocalSocket.started=new CountDownLatch(1);check(ReceiverRemote.request("play"));check(LocalSocket.started.await(2,TimeUnit.SECONDS));check(ReceiverRemote.request("pause"));
await(()->"failed".equals(ReceiverRemote.snapshot().get("result")));check(LocalSocket.commands.size()==before+2); // Queued pause expires, never replayed.
LocalSocket.delay=0;ReceiverRemote.detach("first");check(!ReceiverRemote.available());
LocalSocket.peerUid=999;ReceiverRemote.attach("wrong-peer");Thread.sleep(100);check(!ReceiverRemote.available());
ReceiverRemote.detach("wrong-peer");LocalSocket.peerUid=10001;ReceiverRemote.attach("second");await(()->ReceiverRemote.available());ReceiverRemote.detach("first");check(ReceiverRemote.available());
System.out.println("PASS: "+checks+" source protocol/client checks: allowlist, session binding, real feedback, rejection, expiry, no replay, peer UID and generation isolation");
}finally{ReceiverRemote.detach("first");ReceiverRemote.detach("wrong-peer");ReceiverRemote.detach("second");}}
}'''
}
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='receiver-remote-') as folder:
    temp=Path(folder);files=[]
    for name,body in sources.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body,encoding='utf-8');files.append(path)
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    production=[root.parent/'shared/audio-receiver/src/com/wallpanel/audioreceiver'/name for name in ('RemoteProtocol.java','ReceiverRemote.java')]
    out=temp/'classes';out.mkdir()
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',str(out),*[str(p) for p in production+files]],check=True)
    subprocess.run([java,'-cp',str(out),'com.wallpanel.audioreceiver.RemoteHarness'],check=True,timeout=20)
