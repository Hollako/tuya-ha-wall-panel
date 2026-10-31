package com.wallpanel.audioreceiver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent) {
    String action=intent.getAction();
    Log.i("AudioReceiverBoot","Startup event: "+action);
    if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
      if(BootFlags.shouldStart(context))context.startForegroundService(new Intent(context,BootStartService.class));
    } else if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
      PrefStore prefs=new PrefStore(context);
      boolean boot=Intent.ACTION_BOOT_COMPLETED.equals(action);
      context.startForegroundService(new Intent(context,ReceiverService.class).setAction(ReceiverService.ACTION_ENSURE_STARTED));
    }
  }
}
