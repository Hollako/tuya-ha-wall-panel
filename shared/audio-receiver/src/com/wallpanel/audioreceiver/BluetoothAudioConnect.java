package com.wallpanel.audioreceiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.os.Handler;
import java.util.*;

/** Explicit audio-profile connection to a user-selected, already bonded phone.
 * No pairing removal, profile priority changes, automatic playback or retries. */
public final class BluetoothAudioConnect {
  public interface Result { void message(String text); }
  public static void choose(Activity activity, final Result result) {
    final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
    if(adapter==null||!adapter.isEnabled()){result.message("Turn Bluetooth on first");return;}
    try {
      final ArrayList<BluetoothDevice> peers=new ArrayList<>(adapter.getBondedDevices());
      Collections.sort(peers,(a,b)->label(a).compareToIgnoreCase(label(b)));
      if(peers.isEmpty()){result.message("Pair your phone in Bluetooth settings first");return;}
      String[] labels=new String[peers.size()];for(int i=0;i<labels.length;i++)labels[i]=label(peers.get(i));
      new AlertDialog.Builder(activity).setTitle("Connect phone for music").setItems(labels,(dialog,index)->new Attempt(activity,adapter,peers.get(index),result).start()).setNegativeButton("Cancel",(dialog,which)->result.message("Connection cancelled")).setOnCancelListener(dialog->result.message("Connection cancelled")).show();
    } catch(SecurityException denied){result.message("Bluetooth permission is unavailable");}
  }
  private static String label(BluetoothDevice d){String name=d.getName();return (name==null?"Paired device":name)+" ("+d.getAddress()+")";}
  private static final class Attempt implements BluetoothProfile.ServiceListener {
    final Activity activity;final BluetoothAdapter adapter;final BluetoothDevice peer;final Result result;final Handler main=new Handler();
    BluetoothProfile profile;boolean closed;
    Attempt(Activity a,BluetoothAdapter b,BluetoothDevice d,Result r){activity=a;adapter=b;peer=d;result=r;}
    final Runnable timeout=()->finish("Bluetooth audio service did not respond");
    void start(){
      try {adapter.cancelDiscovery();result.message("Requesting Bluetooth music connection…");main.postDelayed(timeout,8000);
        if(!adapter.getProfileProxy(activity.getApplicationContext(),this,11))finish("Bluetooth receiver service is unavailable");
      }catch(Exception failed){finish("Bluetooth audio connection is unavailable on this firmware");}
    }
    public void onServiceConnected(int id,BluetoothProfile proxy){
      if(closed){adapter.closeProfileProxy(id,proxy);return;}profile=proxy;
      try {if(proxy.getConnectionState(peer)==BluetoothProfile.STATE_CONNECTED){finish("Phone is already connected for music");return;}
        if(!BluetoothProfiles.musicConnectionAllowed(proxy,peer)){finish("Another device is connected for music. Disconnect it first.");return;}
        boolean accepted=BluetoothProfiles.connectMusic(proxy,peer);
        finish(accepted?"Music connection requested. Check your phone, then press Play.":"Bluetooth rejected the music connection request");
      }catch(Exception failed){finish("Firmware rejected direct audio connection; try Bluetooth settings");}
    }
    public void onServiceDisconnected(int id){if(!closed)finish("Bluetooth audio service disconnected");}
    void finish(final String message){if(closed)return;closed=true;main.removeCallbacks(timeout);if(profile!=null){adapter.closeProfileProxy(11,profile);profile=null;}main.post(()->{if(!activity.isFinishing()&&!activity.isDestroyed())result.message(message);});}
  }
  private BluetoothAudioConnect(){}
}
