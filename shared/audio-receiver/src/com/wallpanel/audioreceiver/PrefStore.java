package com.wallpanel.audioreceiver;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefStore {
  private static final String PREFS = "wpar_prefs";

  public static final String KEY_RECEIVER_NAME = "receiver_name";
  public static final String KEY_ENABLED = "enabled";
  public static final String KEY_AUTOSTART = "autostart";
  public static final String KEY_VOLUME_TRIM = "volume_trim";
  public static final String KEY_HA_ENABLED = "ha_enabled";
  public static final String KEY_HA_PORT = "ha_port";
  public static final String KEY_HA_TOKEN = "ha_token";
  public static final String KEY_RTSP_PORT = "rtsp_port";
  public static final String KEY_LATENCY_OFFSET_MS = "latency_offset_ms";
  public static final String KEY_LOG_VERBOSITY = "log_verbosity";
  public static final String KEY_SU_COMMAND = "su_command";
  public static final String KEY_INTERFACE_NAME = "network_interface";

  public String getInterfaceName(){return prefs.getString(KEY_INTERFACE_NAME, "");}

  private final SharedPreferences prefs;
  private final Context context;

  public PrefStore(Context ctx) {
    context=ctx.getApplicationContext();
    prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    BootFlags.save(context,isEnabled(),isAutostart());
  }

  public String getReceiverName() {
    return prefs.getString(KEY_RECEIVER_NAME, "Wall Panel");
  }

  public void addListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs.registerOnSharedPreferenceChangeListener(listener);
  }

  public void removeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs.unregisterOnSharedPreferenceChangeListener(listener);
  }

  public boolean isEnabled() {
    return prefs.getBoolean(KEY_ENABLED, false);
  }

  public boolean isAutostart() {
    return prefs.getBoolean(KEY_AUTOSTART, true);
  }

  public int getVolumeTrim() {
    return prefs.getInt(KEY_VOLUME_TRIM, 100);
  }

  public boolean isHaEnabled() {
    return prefs.getBoolean(KEY_HA_ENABLED, "com.wallpanel.audioreceiver".equals(context.getPackageName()));
  }

  public int getHaPort() {
    return prefs.getInt(KEY_HA_PORT, 8099);
  }

  public String getHaToken() {
    return prefs.getString(KEY_HA_TOKEN, "");
  }

  public int getRtspPort() {
    return prefs.getInt(KEY_RTSP_PORT, 7000);
  }

  public int getLatencyOffsetMs() {
    return Math.max(-500,Math.min(500,prefs.getInt(KEY_LATENCY_OFFSET_MS, 0)));
  }

  public int getLogVerbosity() {
    return prefs.getInt(KEY_LOG_VERBOSITY, 0);
  }

  public String getSuCommand() {
    return prefs.getString(KEY_SU_COMMAND, "su -c");
  }

  public void put(String key, String value) {
    prefs.edit().putString(key, value).apply();
  }

  public void put(String key, boolean value) {
    prefs.edit().putBoolean(key, value).apply();
    if(KEY_ENABLED.equals(key)||KEY_AUTOSTART.equals(key))BootFlags.save(context,isEnabled(),isAutostart());
  }

  public void put(String key, int value) {
    prefs.edit().putInt(key, value).apply();
  }
}
