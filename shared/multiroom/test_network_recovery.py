"""Host tests of receiver IP recovery policy and real web-listener socket replacement."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[2]
java = r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
with tempfile.TemporaryDirectory() as folder:
    out = Path(folder)
    receiver = out / 'ReconnectTest.java'
    receiver.write_text('''package com.wallpanel.multiroom;
public class ReconnectTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  GroupReconnect state=new GroupReconnect("panel-leader","172.16.1.40");
  check(state.due(0));state.failed(100);check(!state.due(599)&&state.due(600));
  state.failed(600);check(!state.due(1599)&&state.due(1600));
  check(!state.confirmed("panel-other","172.16.1.99")&&state.host().equals("172.16.1.40"));
  check(!state.confirmed("panel-leader","172.16.1.40")&&!state.due(1599));
  check(state.confirmed("panel-leader","172.16.1.41")&&state.host().equals("172.16.1.41")&&state.due(0));
  check(!state.confirmed("panel-leader","172.16.1.41"));
  try{state.confirmed("panel-leader","172.16.1.2:1704");throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(state.host().equals("172.16.1.41"));
  long now=0;for(int i=0;i<50;i++){state.failed(now);check(!state.due(now+499)&&state.due(now+15000));now+=15000;}
  state.connected();state.failed(1000000);check(!state.due(1000499)&&state.due(1000500));
  GroupReconnect legacy=new GroupReconnect("","172.16.1.40");check(!legacy.confirmed("panel-leader","172.16.1.41"));
  System.out.println("PASS: stable leader ID, changed-IP recovery, no duplicate reconnects, foreign/invalid endpoint rejection, bounded retry and backoff reset.");
 }
}''', encoding='utf-8')
    web = out / 'ListenerTest.java'
    web.write_text('''package com.wallpanel.audioreceiver;
import java.net.*;import java.io.*;import java.util.concurrent.*;
public class ListenerTest {
 static Socket connect(int port)throws Exception{
  for(int i=0;i<100;i++){Socket socket=new Socket();try{socket.connect(new InetSocketAddress("127.0.0.1",port),100);socket.setSoTimeout(2000);return socket;}catch(IOException error){socket.close();Thread.sleep(10);}}
  throw new AssertionError("Listener did not reopen");
 }
 public static void main(String[] args)throws Exception{
  int port;try(ServerSocket probe=new ServerSocket(0)){port=probe.getLocalPort();}
  RebindableServer server=new RebindableServer(port);ExecutorService worker=Executors.newSingleThreadExecutor();
  try{
   Future<Socket> first=worker.submit(()->server.accept());Socket client=connect(port);Socket accepted=first.get(2,TimeUnit.SECONDS);
   Future<Socket> second=worker.submit(()->server.accept());server.rebind();
   accepted.getOutputStream().write(77);if(client.getInputStream().read()!=77)throw new AssertionError("Rebind interrupted existing request");
   Socket next=connect(port);Socket replacement=second.get(2,TimeUnit.SECONDS);replacement.getOutputStream().write(42);
   if(next.getInputStream().read()!=42)throw new AssertionError();
   client.close();accepted.close();next.close();replacement.close();
   Future<Boolean> stopped=worker.submit(()->{try{server.accept();return false;}catch(IOException expected){return true;}});
   server.close();if(!stopped.get(2,TimeUnit.SECONDS))throw new AssertionError("Shutdown did not unblock accept");
   server.rebind();try{server.accept();throw new AssertionError("Stopped listener reopened");}catch(IOException expected){}
   System.out.println("PASS: listener rebind unblocks accept, reopens same port, preserves accepted requests, and cannot reopen after shutdown.");
  }finally{server.close();worker.shutdownNow();}
 }
}''', encoding='utf-8')
    decisions=out/'AudioDecisionsTest.java'
    decisions.write_text('''package com.wallpanel.multiroom;
import java.util.*;
public class AudioDecisionsTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  Map<String,Boolean> sources=new LinkedHashMap<>();check(SourceDecision.choose(sources,null).equals("none"));
  sources.put("bluetooth",true);check(SourceDecision.choose(sources,null).equals("bluetooth"));
  sources.put("apps",false);check(SourceDecision.choose(sources,null).equals("bluetooth"));
  sources.put("apps",true);check(SourceDecision.choose(sources,null).equals("ambiguous"));
  sources.put("bluetooth",false);sources.put("apps",false);check(SourceDecision.choose(sources,null).equals("none"));check(SourceDecision.choose(sources,"apps").equals("apps"));
  sources.remove("apps");check(SourceDecision.choose(sources,null).equals("bluetooth"));
  for(int max:new int[]{15,20,25,100})for(int percent=0;percent<=100;percent++){
   int index=RoomLevel.index(percent,max);double gain=RoomLevel.gain(percent,max);
   check(index>=0&&index<=max&&gain>=0&&gain<=1);
   check(Math.abs(index/(double)max*gain-percent/100.0)<0.000001);
  }
  check(RoomLevel.percent(.126)==13);for(double bad:new double[]{-1,1.01,Double.NaN,Double.POSITIVE_INFINITY})try{RoomLevel.percent(bad);throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{String nonce=RoomIdentity.challenge(),token="test-room-secret",id="panel-aaaaaaaa";String proof=RoomIdentity.proof(token,id,nonce);check(RoomIdentity.verify(token,id,nonce,proof));check(!RoomIdentity.verify("wrong",id,nonce,proof));check(!RoomIdentity.verify(token,"panel-bbbbbbbb",nonce,proof));check(!RoomIdentity.verify(token,id,RoomIdentity.challenge(),proof));check(!RoomIdentity.verify("",id,nonce,proof));}catch(Exception error){throw new AssertionError(error);}
  System.out.println("PASS: source arbitration, paused-source resume, explicit conflict handling and one consistent room-level curve across hardware resolutions.");
 }
}''',encoding='utf-8')
    sources = [root/'shared/multiroom/src/com/wallpanel/multiroom/GroupPolicy.java',root/'shared/multiroom/src/com/wallpanel/multiroom/GroupPlan.java',root/'shared/multiroom/src/com/wallpanel/multiroom/RoomIdentity.java',root/'shared/multiroom/src/com/wallpanel/multiroom/SourceDecision.java',root/'shared/multiroom/src/com/wallpanel/multiroom/RoomLevel.java',decisions,
               root/'shared/multiroom/src/com/wallpanel/multiroom/GroupReconnect.java',
               root/'shared/audio-receiver/src/com/wallpanel/audioreceiver/RebindableServer.java', receiver, web]
    subprocess.run([java, '-jar', str(root/'audio/tooling/ecj.jar'), '-1.8', '-nowarn', '-d', folder, *map(str,sources)], check=True)
    for name in ('com.wallpanel.multiroom.ReconnectTest','com.wallpanel.audioreceiver.ListenerTest','com.wallpanel.multiroom.AudioDecisionsTest'):
        subprocess.run([java, '-cp', folder, name], check=True, timeout=15)
