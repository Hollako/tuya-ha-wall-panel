package com.wallpanel.audioreceiver;
import android.app.*;
import android.content.*;
import android.os.*;
import android.net.*;
import java.net.Inet4Address;
import android.util.Log;
/** Direct-boot bridge; audio and credential storage are untouched until unlock. */
public class BootStartService extends Service {
  private BroadcastReceiver unlock;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable check=new Runnable(){@Override public void run(){
    if(!BootFlags.shouldStart(BootStartService.this)){stopSelf();return;}
    if(getSystemService(UserManager.class).isUserUnlocked()) {
      PrefStore prefs=new PrefStore(BootStartService.this);
      if(prefs.isEnabled()&&prefs.isAutostart()) {
        if(!localNetworkReady()) { handler.postDelayed(this,2000); return; }
        Log.i("AudioReceiverBoot","Android unlocked and local network ready; ensuring receiver is started");
        startForegroundService(new Intent(BootStartService.this,ReceiverService.class).setAction(ReceiverService.ACTION_ENSURE_STARTED));
      }
      stopSelf();
    } else handler.postDelayed(this,1000);
  }};
  private boolean localNetworkReady(){
    ConnectivityManager cm=getSystemService(ConnectivityManager.class);
    Network active=cm.getActiveNetwork();
    LinkProperties link=active==null?null:cm.getLinkProperties(active);
    if(link==null||link.getInterfaceName()==null)return false;
    for(LinkAddress address:link.getLinkAddresses())
      if(address.getAddress() instanceof Inet4Address&&!address.getAddress().isLoopbackAddress()&&!address.getAddress().isLinkLocalAddress())return true;
    return false;
  }
  @Override public void onCreate(){
    super.onCreate();
    NotificationManager manager=getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel("wpar_boot","Audio Receiver startup",NotificationManager.IMPORTANCE_LOW));
    Notification notification=new Notification.Builder(this,"wpar_boot").setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.app_name)).setContentText("Waiting for Android to unlock").setOngoing(true).build();
    startForeground(2,notification);
    unlock=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){handler.removeCallbacks(check);handler.post(check);}};
    registerReceiver(unlock,new IntentFilter(Intent.ACTION_USER_UNLOCKED));
  }
  @Override public int onStartCommand(Intent i,int flags,int id){handler.removeCallbacks(check);handler.post(check);return START_NOT_STICKY;}
  @Override public void onDestroy(){handler.removeCallbacks(check);if(unlock!=null)unregisterReceiver(unlock);stopForeground(true);super.onDestroy();}
  @Override public IBinder onBind(Intent i){return null;}
}
