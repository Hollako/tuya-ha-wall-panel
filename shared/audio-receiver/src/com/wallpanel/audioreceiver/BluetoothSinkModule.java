package com.wallpanel.audioreceiver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import java.util.LinkedHashMap;
import java.util.Map;

/** Observes the firmware receiver. Never starts a source profile or modifies audio routing. */
public final class BluetoothSinkModule implements AudioSourceModule {
  private static final String CONNECTION = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
  private static final String PLAYING = "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED";
  private final Context context;
  private final BluetoothAdapter adapter;
  private final PrefStore prefs;
  private final boolean manageName;
  private String nameSyncError;
  private Boolean sinkConfigured;
  private Boolean connected;
  private Boolean playing;
  private String peerName;
  private String error;
  private boolean registered;

  public BluetoothSinkModule(Context context) {
    this(context, false);
  }

  public BluetoothSinkModule(Context context, boolean manageName) {
    this.context = context.getApplicationContext();
    this.prefs = new PrefStore(context);
    this.manageName = manageName;
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    adapter = manager == null ? null : manager.getAdapter();
    try {
      Resources resources = context.getPackageManager().getResourcesForApplication("com.android.bluetooth");
      int id = resources.getIdentifier("profile_supported_a2dp_sink", "bool", "com.android.bluetooth");
      sinkConfigured = id == 0 ? null : resources.getBoolean(id);
    } catch (Exception e) { error = "Firmware profile configuration unavailable"; }
  }

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      synchronized (BluetoothSinkModule.this) {
        String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          connected = null; playing = null; peerName = null;
          if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON) {
            connected = false; playing = false;
          } else if (manageName) {
            syncReceiverName();
          }
        } else if (CONNECTION.equals(action)) {
          int value = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
          if (value == BluetoothProfile.STATE_CONNECTED) connected = true;
          else if (value == BluetoothProfile.STATE_DISCONNECTED) {
            connected = false; playing = false; peerName = null;
          }
          if (Boolean.TRUE.equals(connected)) updatePeer(intent);
        } else if (PLAYING.equals(action)) {
          int value = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
          // Firmware A2DP sink playback constants: PLAYING=10, NOT_PLAYING=11.
          if (value == 10) { playing = true; connected = true; updatePeer(intent); }
          else if (value == 11) playing = false;
        }
      }
    }
  };

  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener nameListener =
      (preferences, key) -> {
        if (PrefStore.KEY_RECEIVER_NAME.equals(key)) syncReceiverName();
      };

  /** Android owns persistence; sync on app startup, Apply and Bluetooth ON. */
  private synchronized void syncReceiverName() {
    nameSyncError = null;
    if(!SettingsHub.ownsBluetoothName())return;
    if (adapter == null) return;
    try {
      if (!adapter.isEnabled()) return;
      String name = prefs.getReceiverName();
      if (name.trim().isEmpty() || name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 248) {
        nameSyncError = "Receiver name must be 1-248 UTF-8 bytes";
      } else if (!name.equals(adapter.getName()) && !adapter.setName(name)) {
        nameSyncError = "Firmware rejected the Bluetooth name change";
      }
    } catch (SecurityException e) {
      nameSyncError = "Bluetooth name permission denied";
    }
  }

  private void updatePeer(Intent intent) {
    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    if (device != null) try { peerName = device.getName(); } catch (SecurityException ignored) {}
  }
  public String sourceId() { return "bluetooth"; }
  public synchronized void startObserving() {
    if (registered || adapter == null) return;
    BluetoothProfiles.start(context);
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    filter.addAction(CONNECTION); filter.addAction(PLAYING);
    context.registerReceiver(receiver, filter);
    registered = true;
    if (manageName) {
      prefs.addListener(nameListener);
      syncReceiverName();
    }
  }
  public synchronized void close() {
    if (manageName) prefs.removeListener(nameListener);
    if (registered) context.unregisterReceiver(receiver);
    registered = false;
  }
  public synchronized Map<String, Object> snapshot() {
    org.json.JSONObject live=com.wallpanel.multiroom.MediaActivity.bluetoothEvidence();
    if(live.optBoolean("known")){connected=live.optBoolean("connected");playing=live.optBoolean("playing");peerName=live.optString("peerName",null);}
    if(live.optBoolean("renderingKnown")&&live.optBoolean("rendering")){connected=true;playing=true;}
    Map<String, Object> result = new LinkedHashMap<>();
    boolean enabled = false;
    String name = null;
    try { enabled = adapter != null && adapter.isEnabled(); if (enabled) name = adapter.getName(); }
    catch (SecurityException e) { error = "Bluetooth permission unavailable"; }
    result.put("id", sourceId());
    result.put("hardwareAvailable", adapter != null);
    result.put("sinkConfigured", sinkConfigured);
    result.put("enabled", enabled);
    result.put("name", name);
    result.put("desiredName", prefs.getReceiverName());
    result.put("nameMatchesReceiver", name != null && name.equals(prefs.getReceiverName()));
    result.put("nameSyncError", nameSyncError);
    result.put("connected", enabled ? connected : Boolean.FALSE);
    result.put("playing", enabled ? playing : Boolean.FALSE);
    result.put("peerName", peerName);
    result.put("state", adapter == null ? "UNAVAILABLE" : Boolean.FALSE.equals(sinkConfigured) ? "FIRMWARE_DISABLED" : !enabled ? "OFF" : Boolean.TRUE.equals(playing) ? "STREAMING" : Boolean.TRUE.equals(connected) ? "CONNECTED" : "AWAITING_PHONE");
    if(enabled&&live.optString("recovery").equals("reconnecting"))result.put("state","RECONNECTING");
    else if(enabled&&Boolean.TRUE.equals(connected)&&Boolean.TRUE.equals(playing)&&live.optBoolean("renderingKnown")&&!live.optBoolean("rendering"))result.put("state","WAITING_FOR_AUDIO");
    result.put("sessionStateKnown", !enabled || connected != null);
    result.put("volumeControl", "firmware_media_volume");
    result.put("error", error);
    result.put("renderingKnown",live.optBoolean("renderingKnown"));result.put("rendering",live.optBoolean("rendering"));result.put("recovery",live.optString("recovery","idle"));
    result.put("profileBindingReady",live.optBoolean("known"));result.put("controllerReady",live.optBoolean("controllerReady"));result.put("nativeProbe",live.optString("nativeProbe","unavailable"));
    return result;
  }
}
