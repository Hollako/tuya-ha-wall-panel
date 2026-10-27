package local.wallpanel.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Private alarm relaunch survives process exit without force-stopping the package. */
public final class AppRestartReceiver extends BroadcastReceiver {
    private static final String ACTION="local.wallpanel.app.RESTART_ALARM";
    private static boolean pending;
    static void request(final Context source,final Runnable scheduled){
        final Context context=source.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){
            if(pending)return;
            try{
                AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                if(alarm==null)throw new IllegalStateException("Alarm service unavailable");
                PendingIntent launch=PendingIntent.getBroadcast(context,73,new Intent(context,AppRestartReceiver.class).setAction(ACTION),PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+2500,launch);
                pending=true;
                scheduled.run();
                // No STOP_HARDWARE action: preserve the user's saved enabled setting.
                context.stopService(new Intent(context,BridgeService.class));
                context.stopService(new Intent(context,SipService.class));
                context.stopService(new Intent(context,ExternalAppService.class));
                context.stopService(new Intent(context,com.wallpanel.audioreceiver.ReceiverService.class));
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){android.os.Process.killProcess(android.os.Process.myPid());}},1000);
            }catch(Exception failed){pending=false;new ConfigStore(context).runtimeStatus("App restart unavailable; try again");}
        }});
    }
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        pending=false;BridgeService.ensureStarted(context);SipRuntime.reconcile(context);
        context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
