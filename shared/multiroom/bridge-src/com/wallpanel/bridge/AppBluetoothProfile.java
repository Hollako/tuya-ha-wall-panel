package com.wallpanel.bridge;
import android.bluetooth.*;
import android.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Same-owner private endpoint; Android's profile binding belongs to the APK process. */
public final class AppBluetoothProfile implements BluetoothProfile {
 private final int owner;private final boolean controller;private final BluetoothAdapter adapter;
 private static volatile long at;private static int cachedOwner;private static JSONObject cached=new JSONObject();private static final Object statusLock=new Object();
 AppBluetoothProfile(int uid,boolean control,BluetoothAdapter a){owner=uid;controller=control;adapter=a;}
 private JSONObject request(String command){LocalSocket socket=new LocalSocket();try{
  socket.connect(new LocalSocketAddress("wallpanel.btprofiles."+owner,LocalSocketAddress.Namespace.ABSTRACT));if(socket.getPeerCredentials().getUid()!=owner)throw new SecurityException("Untrusted Bluetooth owner");socket.setSoTimeout(1200);
  socket.getOutputStream().write((command+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputStream input=socket.getInputStream();
  for(int n=0;n<8192;n++){int b=input.read();if(b=='\n')return new JSONObject(bytes.toString("UTF-8"));if(b<0)break;bytes.write(b);}
 }catch(Exception ignored){}finally{try{socket.close();}catch(Exception ignored){}}return new JSONObject();}
 private JSONObject status(){synchronized(statusLock){long now=android.os.SystemClock.elapsedRealtime();if(cachedOwner!=owner||now-at>=100){cached=request("STATUS");cachedOwner=owner;at=now;}return cached;}}
 boolean ready(){return status().optBoolean(controller?"controller":"sink");}
 boolean canReconnect(){return status().optBoolean("canReconnect");}
 double roomGain(){return status().optDouble("roomGain",Double.NaN);}
 boolean remotePaused(){return status().optBoolean("remotePaused");}
 public List<BluetoothDevice> getConnectedDevices(){List<BluetoothDevice> result=new ArrayList<BluetoothDevice>();JSONArray peers=status().optJSONArray("peers");if(peers!=null)for(int i=0;i<peers.length();i++)try{result.add(adapter.getRemoteDevice(peers.getJSONObject(i).getString("address")));}catch(Exception ignored){}return result;}
 public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states){for(int state:states)if(state==STATE_CONNECTED)return getConnectedDevices();return Collections.emptyList();}
 public int getConnectionState(BluetoothDevice d){return request((controller?"CTRL_STATE ":"SINK_STATE ")+d.getAddress()).optInt("state",-1);}
 public boolean isAudioPlaying(BluetoothDevice d){return status().optBoolean("playing");}
 public boolean connect(BluetoothDevice d){at=0;return request("CONNECT "+d.getAddress()).optBoolean("ok");}
 public boolean disconnect(BluetoothDevice d){at=0;return request("DISCONNECT "+d.getAddress()).optBoolean("ok");}
 public void sendPassThroughCmd(BluetoothDevice d,int key,int pressed)throws IOException{if(pressed!=0)return;String verb=key==0x44?"PLAY":key==0x46?"PAUSE":key==0x4b?"NEXT":key==0x4c?"PREVIOUS":"";if(verb.length()==0||!request(verb+" "+d.getAddress()).optBoolean("ok"))throw new IOException("Profile transport unavailable");}
}
