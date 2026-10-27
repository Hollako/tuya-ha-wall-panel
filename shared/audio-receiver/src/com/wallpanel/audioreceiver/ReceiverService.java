package com.wallpanel.audioreceiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ReceiverService extends Service implements ReceiverState.Listener {
  private static final String TAG = "ReceiverService";
  private static final String CHANNEL_ID = "wpar_status";
  private static final int NOTIFICATION_ID = 1;

  public static final String ACTION_ENSURE_STARTED = "com.wallpanel.audioreceiver.ENSURE_STARTED";
  public static final String ACTION_START = "com.wallpanel.audioreceiver.START";
  public static final String ACTION_STOP = "com.wallpanel.audioreceiver.STOP";
  public static final String ACTION_TOGGLE = "com.wallpanel.audioreceiver.TOGGLE";

  private PrefStore prefs;
  private ReceiverState state;
  private LogBuf logBuf;
  private NativeStack nativeStack;
  private PcmPump pcmPump;
  private MetadataReader metadataReader;
  private HttpApi httpApi;
  private BluetoothSinkModule bluetoothSink;

  private Thread pcmPumpThread;
  private Thread metadataReaderThread;
  private Thread httpApiThread;

  private WifiManager wifiManager;
  private WifiManager.WifiLock wifiLock;
  private PowerManager powerManager;

  private ConnectivityManager.NetworkCallback networkCallback;
  private Handler mainHandler;
  private boolean destroying;
  private int retryMs=2000;
  private String startedNetwork="";
  private final Runnable retry=new Runnable(){public void run(){if(!destroying&&prefs.isEnabled())startReceiver();}};
  private final Runnable watchNetwork=new Runnable(){public void run(){
    if(destroying)return;
    if(prefs.isEnabled()&&receiverStarted&&!startedNetwork.equals(networkKey()))startReceiver();
    mainHandler.postDelayed(this,2000);
  }};
  private String networkKey(){
    if(!localNetworkReady())return "";
    return ReceiverNetwork.interfaceName(this,prefs)+"/"+state.getLocalIp();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Service created");

    prefs = new PrefStore(this);
    state = ReceiverState.INSTANCE;
    state.addListener(this);

    File logFile = new File(getFilesDir(), "app.log");
    logBuf = new LogBuf(logFile);

    nativeStack = new NativeStack(this, prefs, state, logBuf);

    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

    bluetoothSink = new BluetoothSinkModule(this, "com.wallpanel.audioreceiver".equals(getPackageName()));
    bluetoothSink.startObserving();
    httpApi = new HttpApi(this, wifiManager, powerManager, prefs, state, logBuf, bluetoothSink);

    pcmPump = new PcmPump(nativeStack.getAudioSocketName(), audioManager, httpApi.getWakeLock(), state, prefs);
    metadataReader = new MetadataReader(nativeStack.getMetaFifo(), state);

    createNotificationChannel();
    startForeground(NOTIFICATION_ID, buildNotification("Starting..."));

    mainHandler = new Handler(Looper.getMainLooper());
    if(prefs.isHaEnabled()){httpApiThread=new Thread(httpApi,"wpar-http");httpApiThread.start();}
    registerNetworkCallback();
    mainHandler.postDelayed(watchNetwork,2000);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if(intent==null&&prefs.isEnabled()){startReceiver();return START_STICKY;}
    if (intent != null) {
      String action = intent.getAction();
      if (ACTION_ENSURE_STARTED.equals(action)) {
        if(prefs.isEnabled()&&!receiverStarted)startReceiver();
      } else if (ACTION_START.equals(action)) {
        prefs.put(PrefStore.KEY_ENABLED,true);
        startReceiver();
      } else if (ACTION_STOP.equals(action)) {
        prefs.put(PrefStore.KEY_ENABLED,false);
        mainHandler.removeCallbacks(retry);
        stopReceiver();
      } else if (ACTION_TOGGLE.equals(action)) {
        if (prefs.isEnabled()) {
          prefs.put(PrefStore.KEY_ENABLED,false);
          mainHandler.removeCallbacks(retry);
          stopReceiver();
        } else {
          prefs.put(PrefStore.KEY_ENABLED, true);
          startReceiver();
        }
      }
    }
    return START_STICKY;
  }

  private boolean receiverStarted=false;
  private void startReceiver() {
    mainHandler.removeCallbacks(retry);
    if(receiverStarted) stopReceiver();
    if((pcmPumpThread!=null&&pcmPumpThread.isAlive())||(metadataReaderThread!=null&&metadataReaderThread.isAlive())){
      state.setErrorMessage("Waiting for previous receiver transport to close");state.setState(State.ERROR);return;
    }
    if(!localNetworkReady()){
      state.setState(State.STARTING);state.setErrorMessage("Waiting for receiver network");
      mainHandler.postDelayed(retry,2000);return;
    }
    startedNetwork=networkKey();
    nativeStack.newAudioSocketSession();
    pcmPump = new PcmPump(nativeStack.getAudioSocketName(), (AudioManager)getSystemService(Context.AUDIO_SERVICE), httpApi.getWakeLock(), state, prefs);
    metadataReader = new MetadataReader(nativeStack.getMetaFifo(), state);
    state.setVolumeTrim(prefs.getVolumeTrim());
    receiverStarted=true;
    Log.i(TAG, "Starting receiver");
    state.setState(State.STARTING);

    // Check root
    state.setRootAvailable(false);

    httpApi.acquireMulticastLock();
    if(wifiLock==null){wifiLock=wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"wpar:wifi");wifiLock.setReferenceCounted(false);}
    if(!wifiLock.isHeld())wifiLock.acquire();

    try {
      nativeStack.prepare();
      nativeStack.start();

      pcmPumpThread = new Thread(pcmPump);
      pcmPumpThread.start();

      metadataReaderThread = new Thread(metadataReader);
      metadataReaderThread.start();



      prefs.put(PrefStore.KEY_ENABLED, true);

    } catch (Exception e) {
      Log.e(TAG, "startReceiver failed", e);
      state.setErrorMessage("Start failed: " + e.getMessage());
      state.setState(State.ERROR);
    }
  }

  private void stopReceiver() {
    Log.i(TAG, "Stopping receiver");
    receiverStarted=false;

    nativeStack.stop();
    pcmPump.stop();
    metadataReader.stop();


    if (pcmPumpThread != null) pcmPumpThread.interrupt();
    if (metadataReaderThread != null) metadataReaderThread.interrupt();

    try{if(pcmPumpThread!=null)pcmPumpThread.join(500);if(metadataReaderThread!=null)metadataReaderThread.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    state.clearStreamingInfo();
    httpApi.releaseMulticastLock();
    if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();

    state.setState(State.DISABLED);
  }

  private boolean localNetworkReady(){
    try{
      String chosen=ReceiverNetwork.interfaceName(this,prefs);if(chosen==null||chosen.isEmpty())return false;
      java.net.NetworkInterface iface=java.net.NetworkInterface.getByName(chosen);
      if(iface==null||!iface.isUp())return false;
      java.util.Enumeration<java.net.InetAddress> addresses=iface.getInetAddresses();
      while(addresses.hasMoreElements()){
        java.net.InetAddress a=addresses.nextElement();
        if(a instanceof java.net.Inet4Address&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress()){state.setLocalIp(a.getHostAddress());return true;}
      }
    }catch(Exception ignored){}
    return false;
  }

  private void registerNetworkCallback() {
    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    networkCallback = new ConnectivityManager.NetworkCallback() {
      @Override
      public void onAvailable(Network network) {
        Log.i(TAG, "Network available");
        updateLocalIp();
      }

      @Override
      public void onLost(Network network) {
        Log.w(TAG, "Network lost");
        updateLocalIp();
      }

      @Override
      public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        updateLocalIp();
      }
    };
    NetworkRequest request = new NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build();
    cm.registerNetworkCallback(request, networkCallback);
  }

  private void updateLocalIp() {
    mainHandler.post(() -> {if(!localNetworkReady())state.setLocalIp(null);});
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
          CHANNEL_ID,
          "Receiver Status",
          NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("AirPlay 2 receiver status");
      NotificationManager nm = getSystemService(NotificationManager.class);
      nm.createNotificationChannel(channel);
    }
  }

  private Notification buildNotification(String text) {
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new Notification.Builder(this, CHANNEL_ID);
    } else {
      builder = new Notification.Builder(this);
    }
    return builder
        .setContentTitle("Audio Receiver")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .build();
  }

  @Override
  public void onStateChanged() {
    // Native errors arrive asynchronously. Retry the whole transport rather than
    // trusting receiverStarted after a dead process. Explicit STOP cancels desire.
    if(mainHandler!=null&&!destroying&&prefs.isEnabled()&&state.getState()==State.ERROR){
      mainHandler.removeCallbacks(retry);mainHandler.postDelayed(retry,retryMs);retryMs=Math.min(30000,retryMs*2);
    }else if(state.getState()==State.RUNNING_IDLE||state.getState()==State.STREAMING)retryMs=2000;
    String text;
    switch (state.getState()) {
      case DISABLED: text = "Disabled"; break;
      case STARTING: text = "Starting..."; break;
      case RUNNING_IDLE: text = "Ready: " + prefs.getReceiverName(); break;
      case STREAMING:
        String title = state.getTitle();
        String artist = state.getArtist();
        text = (title != null ? title : "Streaming") + (artist != null ? " â€” " + artist : "");
        break;
      case STOPPING: text = "Stopping..."; break;
      case ERROR: text = "Error: " + state.getErrorMessage(); break;
      default: text = "Unknown";
    }
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(NOTIFICATION_ID, buildNotification(text));
  }

  @Override
  public void onDestroy() {
    destroying=true;mainHandler.removeCallbacksAndMessages(null);
    super.onDestroy();
    Log.i(TAG, "Service destroyed");
    boolean enabled=prefs.isEnabled();
    stopReceiver();
    prefs.put(PrefStore.KEY_ENABLED,enabled);
    if (networkCallback != null) {
      ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      cm.unregisterNetworkCallback(networkCallback);
    }
    bluetoothSink.close();
    httpApi.stop();
    state.removeListener(this);
    logBuf.close();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
