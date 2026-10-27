package com.wallpanel.audioreceiver;
import android.content.Context;
import android.content.SharedPreferences;
/** Only non-secret startup switches are accessible before user unlock. */
final class BootFlags {
  private static SharedPreferences flags(Context context) {
    Context storage=android.os.Build.VERSION.SDK_INT>=24?context.createDeviceProtectedStorageContext():context;
    return storage.getSharedPreferences("wpar_boot_flags",Context.MODE_PRIVATE);
  }
  static void save(Context context,boolean enabled,boolean autostart) {
    if(!flags(context).edit().putBoolean("enabled",enabled).putBoolean("autostart",autostart).commit())
      android.util.Log.w("AudioReceiverBoot","Could not persist startup switches");
  }
  static boolean shouldStart(Context context) {
    SharedPreferences p=flags(context);
    return p.getBoolean("enabled",false)&&p.getBoolean("autostart",false);
  }
}
