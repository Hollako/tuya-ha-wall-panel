package local.wallpanel.app;
import android.content.*;
/** Android starts hardware after boot/update; the selected Home owns dashboard UI. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        BridgeService.ensureStarted(context);
        SipRuntime.reconcile(context);ExternalAppService.reconcile(context);
        ReceiverRuntime.reconcile(context);
    }
}
