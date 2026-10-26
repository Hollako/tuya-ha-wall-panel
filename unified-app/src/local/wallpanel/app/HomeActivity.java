package local.wallpanel.app;
import android.content.Intent;
import android.os.Bundle;
/** The Home root owns its WebView; Home must not recreate the dashboard. */
public final class HomeActivity extends DashboardActivity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);BridgeService.ensureStarted(this);SipRuntime.reconcile(this);}
    @Override protected boolean isHome(){return true;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);refreshDashboard();}
}
