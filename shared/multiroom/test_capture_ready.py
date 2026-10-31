"""Production capture-start sequence on simulated sockets: no early PLAY or leaked leases."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}',
'android/os/Process.java':'package android.os;public class Process {public static int myUid(){return 10065;}}',
'android/content/Context.java':'package android.content;public class Context {public static final String BLUETOOTH_SERVICE="bt";public Object getSystemService(String n){return new android.bluetooth.BluetoothManager();}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public android.content.res.Resources getResourcesForApplication(String p){return new android.content.res.Resources();}}',
'android/content/res/Resources.java':'package android.content.res;public class Resources {public int getIdentifier(String n,String t,String p){return 1;}public boolean getBoolean(int id){return true;}}',
'android/bluetooth/BluetoothManager.java':'package android.bluetooth;public class BluetoothManager {public BluetoothAdapter getAdapter(){return new BluetoothAdapter();}}',
'android/bluetooth/BluetoothAdapter.java':'package android.bluetooth;public class BluetoothAdapter {public boolean isEnabled(){return true;}}',
'android/net/LocalSocketAddress.java':'package android.net;public class LocalSocketAddress {public enum Namespace {ABSTRACT}public LocalSocketAddress(String n,Namespace s){}}',
'android/net/Credentials.java':'package android.net;public class Credentials {public int getUid(){return 1000;}}',
'android/net/LocalSocket.java':'''package android.net;import java.io.*;import java.util.*;
public class LocalSocket implements Closeable {
public static volatile boolean ready,modern=true;public static int busy;public static volatile LocalSocket current;public static final List<LocalSocket> all=Collections.synchronizedList(new ArrayList<LocalSocket>());
public volatile boolean closed;public final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private InputStream input;
public LocalSocket(){all.add(this);}public void connect(LocalSocketAddress a){}public Credentials getPeerCredentials(){return new Credentials();}public void setSoTimeout(int t){}
public OutputStream getOutputStream(){return new OutputStream(){public void write(int b)throws IOException{if(closed)throw new IOException("closed");synchronized(bytes){bytes.write(b);}}};}
public String commands(){synchronized(bytes){return bytes.toString();}}
public synchronized InputStream getInputStream(){if(input!=null)return input;String command=commands().split("\\n")[0],reply;
if(command.equals("STATUS"))reply="OK BT_GROUP 1 UID 10065 SOURCE 1002\\n";
else if(command.equals("STATUS_HANDOFF"))reply=modern?"OK BT_HANDOFF 1\\n":"ERROR COMMAND\\n";
else if(command.equals("CAPTURE")&&busy>0){busy--;reply="ERROR BUSY\\n";}
else{final byte[] header="OK BT_CAPTURE 48000 2 S16LE\\n".getBytes();current=this;input=new InputStream(){int position;public int read()throws IOException {while(!ready&&!closed)try{Thread.sleep(2);}catch(InterruptedException e){throw new IOException(e);}if(closed)return -1;if(position<header.length)return header[position++];while(!closed)try{Thread.sleep(2);}catch(InterruptedException e){throw new IOException(e);}return -1;}};return input;}
input=new ByteArrayInputStream(reply.getBytes());return input;}
public void close()throws IOException{closed=true;}
}''',
'com/wallpanel/multiroom/GroupDecoder.java':'''package com.wallpanel.multiroom;public class GroupDecoder {public interface Sink {java.net.Socket open(int rate,int channels)throws Exception;void error(Exception e);}}''',
'com/wallpanel/multiroom/CaptureReadyTest.java':'''package com.wallpanel.multiroom;import java.io.*;import java.net.*;import android.net.LocalSocket;
public class CaptureReadyTest {
static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
static class Fixture implements GroupDecoder.Sink {final BluetoothGroupSource source=new BluetoothGroupSource(this);Thread worker;int opens,errors;public Socket open(int r,int c){opens++;return new Socket(){public OutputStream getOutputStream(){return new ByteArrayOutputStream();}};}public void error(Exception e){errors++;}
void start()throws Exception{LocalSocket.ready=false;LocalSocket.current=null;LocalSocket.all.clear();worker=new Thread(source);worker.start();long end=System.nanoTime()+2000000000L;while(LocalSocket.current==null&&System.nanoTime()<end)Thread.sleep(2);ok(LocalSocket.current!=null);}
void waitCommand(String command)throws Exception{long end=System.nanoTime()+2000000000L;while(!LocalSocket.current.commands().contains(command)&&System.nanoTime()<end)Thread.sleep(2);ok(LocalSocket.current.commands().contains(command));}
void finish()throws Exception{source.close();worker.join(2000);ok(!worker.isAlive());ok(errors==0);for(LocalSocket socket:LocalSocket.all)ok(socket.closed);}
}
public static void main(String[] args)throws Exception{
Fixture f=new Fixture();f.start();ok(!LocalSocket.current.commands().contains("PLAY"));LocalSocket.ready=true;f.waitCommand("PLAY\\n");ok(LocalSocket.current.commands().split("PLAY",-1).length==2);f.finish();
f=new Fixture();f.start();f.source.pause(true);LocalSocket.ready=true;f.waitCommand("PAUSE\\n");Thread.sleep(30);ok(!LocalSocket.current.commands().contains("PLAY"));f.finish();
f=new Fixture();f.start();f.source.holdRecovery(true);LocalSocket.ready=true;f.waitCommand("HOLD\\n");Thread.sleep(30);ok(!LocalSocket.current.commands().contains("PLAY"));f.source.holdRecovery(false);f.waitCommand("PLAY\\n");f.finish();
f=new Fixture();f.source.recovery(false);f.start();LocalSocket.ready=true;f.waitCommand("PLAY\\n");ok(LocalSocket.current.commands().indexOf("RECOVERY_OFF")<LocalSocket.current.commands().indexOf("PLAY"));f.finish();
LocalSocket.busy=1;f=new Fixture();f.start();LocalSocket.ready=true;f.waitCommand("PLAY\\n");ok(f.opens==1);f.finish();
f=new Fixture();f.start();f.finish(); // Close while the header is pending: no late PLAY and no retained socket.
LocalSocket.modern=false;f=new Fixture();f.start();f.source.holdRecovery(true);LocalSocket.ready=true;Thread.sleep(30);ok(!LocalSocket.current.commands().contains("HOLD"));ok(!LocalSocket.current.commands().contains("PLAY"));f.source.holdRecovery(false);f.waitCommand("PLAY\\n");f.finish();
System.out.println("PASS: "+checks+" production capture sequencing checks: route-ready PLAY, pause/call holds, opt-out ordering, busy retry, cancellation and legacy helper compatibility");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='capture-ready-') as folder:
 files=[]
 for name,body in sources.items():
  path=Path(folder)/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
 files.append(root/'shared/multiroom/src/com/wallpanel/multiroom/BluetoothGroupSource.java')
 java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,*map(str,files)],check=True)
 subprocess.run([java,'-cp',folder,'com.wallpanel.multiroom.CaptureReadyTest'],check=True,timeout=15)
