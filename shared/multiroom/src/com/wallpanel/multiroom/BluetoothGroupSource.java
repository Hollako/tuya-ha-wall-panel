package com.wallpanel.multiroom;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

/** Volatile PCM pipe: only the bridge's fixed Bluetooth or app-media stream, never a mic or file. */
final class BluetoothGroupSource implements Runnable {
 private static volatile boolean ready;private static volatile long checked;
 private final boolean apps;private final GroupDecoder.Sink sink;private volatile LocalSocket capture;private volatile Socket output;
 volatile boolean closed,paused,held;private volatile boolean automatic=true;private boolean handoff,pendingInitial=true;private volatile String status="Waiting for Bluetooth capture";private volatile long bytes,lastSignal;
 BluetoothGroupSource(GroupDecoder.Sink sink){this(sink,false);}
 BluetoothGroupSource(GroupDecoder.Sink sink,boolean apps){this.sink=sink;this.apps=apps;}
 private static LocalSocket connect(String command)throws IOException{
  LocalSocket socket=new LocalSocket();try{socket.connect(new LocalSocketAddress("wallpanel.btgroup."+android.os.Process.myUid(),LocalSocketAddress.Namespace.ABSTRACT));if(socket.getPeerCredentials().getUid()!=1000)throw new IOException("Untrusted Bluetooth bridge");socket.setSoTimeout(10000);socket.getOutputStream().write((command+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();return socket;}catch(IOException error){socket.close();throw error;}
 }
 static boolean available(){try(LocalSocket s=connect("STATUS")){return readLine(s.getInputStream()).equals("OK BT_GROUP 1 UID "+android.os.Process.myUid()+" SOURCE 1002");}catch(Exception unavailable){return false;}}
 static boolean appsAvailable(){try(LocalSocket s=connect("STATUS_APPS")){return readLine(s.getInputStream()).equals("OK APP_GROUP 1 UID "+android.os.Process.myUid()+" SOURCE MEDIA");}catch(Exception unavailable){return false;}}
 static boolean supported(android.content.Context context){
  if(android.os.SystemClock.elapsedRealtime()-checked>5000){ready=available();checked=android.os.SystemClock.elapsedRealtime();}
  if(!ready)return false;
  try{android.bluetooth.BluetoothManager manager=(android.bluetooth.BluetoothManager)context.getSystemService(android.content.Context.BLUETOOTH_SERVICE);android.bluetooth.BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null||!adapter.isEnabled())return false;
   android.content.res.Resources resources=context.getPackageManager().getResourcesForApplication("com.android.bluetooth");int flag=resources.getIdentifier("profile_supported_a2dp_sink","bool","com.android.bluetooth");return flag!=0&&resources.getBoolean(flag);
  }catch(Exception unavailable){return false;}
 }
 private static String readLine(InputStream in)throws IOException{StringBuilder line=new StringBuilder();for(int i=0;i<160;i++){int value=in.read();if(value<0)throw new EOFException("Bluetooth bridge closed");if(value=='\n')return line.toString();line.append((char)value);}throw new IOException("Invalid bridge header");}
 public void run(){try{
  if(!(apps?appsAvailable():available()))throw new IOException("Audio capture bridge is not prepared");
  if(!apps)try(LocalSocket features=connect("STATUS_HANDOFF")){handoff=readLine(features.getInputStream()).equals("OK BT_HANDOFF 1");}
  // Prepare synchronized output before redirecting the existing Bluetooth track.
  output=sink.open(48000,2);if(closed)return;long deadline=android.os.SystemClock.elapsedRealtime()+4500;
  while(!closed){capture=connect(apps?"CAPTURE_APPS":"CAPTURE");String header=readLine(capture.getInputStream());if(header.equals(apps?"OK APP_CAPTURE 48000 2 S16LE":"OK BT_CAPTURE 48000 2 S16LE"))break;capture.close();capture=null;if((!header.equals("ERROR BUSY")&&!header.equals("ERROR UNAVAILABLE"))||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("Audio capture could not start: "+header);Thread.sleep(150);}
  if(closed)return;initialControls();
  capture.setSoTimeout(0);status=apps?"App media capture active":"Bluetooth media capture active";InputStream input=capture.getInputStream();OutputStream out=output.getOutputStream();byte[] pcm=new byte[3840];
  while(!closed){int n=input.read(pcm);if(n<0)throw new EOFException("Bluetooth capture ended");if(n==0)continue;bytes+=n;for(int i=0;i+1<n;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));if(Math.abs(sample)>16){lastSignal=android.os.SystemClock.elapsedRealtime();break;}}if(paused)Arrays.fill(pcm,0,n,(byte)0);out.write(pcm,0,n);}
 }catch(Exception error){if(!closed){status=apps?"App capture unavailable":"Bluetooth capture unavailable";sink.error(error);}}finally{close();}}
 private synchronized boolean send(String command){LocalSocket socket=capture;if(socket!=null)try{socket.getOutputStream().write((command+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();return true;}catch(IOException ignored){}return false;}
 private synchronized void initialControls(){if(closed)return;if(held&&handoff)send("HOLD");if(!automatic&&handoff)send("RECOVERY_OFF");if(paused){send("PAUSE");pendingInitial=false;}else if(!held&&pendingInitial)pendingInitial=!send("PLAY");}
 synchronized void pause(boolean value){paused=value;boolean sent=send(value?"PAUSE":"PLAY");pendingInitial=!value&&!sent;}
 synchronized void holdRecovery(boolean value){held=value;if(handoff)send(value?"HOLD":"RELEASE");if(!value&&pendingInitial&&!paused)pendingInitial=!send("PLAY");}
 void recovery(boolean value){automatic=value;if(handoff)send(value?"RECOVERY_ON":"RECOVERY_OFF");}
 boolean bluetooth(){return !apps;}
 String status(){return status+"; signal "+(lastSignal>0&&android.os.SystemClock.elapsedRealtime()-lastSignal<1500?"present":"silent")+"; "+bytes+" PCM bytes";}
 void close(){closed=true;send("CLOSE");try{if(capture!=null)capture.close();}catch(IOException ignored){}try{if(output!=null)output.close();}catch(IOException ignored){}}
}
