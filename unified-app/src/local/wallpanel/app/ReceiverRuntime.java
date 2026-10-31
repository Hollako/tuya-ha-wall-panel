package local.wallpanel.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.wallpanel.audioreceiver.*;

/** Product adapter only. The receiver engine is the same source in both APKs. */
final class ReceiverRuntime {
    private static Context app;
    private static PrefStore preferences;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static long pending,current;
    private static final Runnable deferred=new Runnable(){public void run(){route();}};
    static synchronized void initialize(Context context){
        if(app!=null)return;app=context.getApplicationContext();
        preferences=new PrefStore(app);
        ReceiverNetwork.resolver(new ReceiverNetwork.Resolver(){public String interfaceName(){
            android.net.Network network=NetworkRouter.get(app).selected(false);
            android.net.ConnectivityManager cm=(android.net.ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.LinkProperties link=network==null?null:cm.getLinkProperties(network);
            return link==null?null:link.getInterfaceName();
        }});
        AudioOutputGate.INSTANCE.sessions(new AudioOutputGate.Sessions(){
            public void started(final long generation){main.post(new Runnable(){public void run(){current=generation;pending=generation;route();}});}
            public void ended(final long generation){main.post(new Runnable(){public void run(){if(current==generation){current=0;pending=0;BridgeService.receiverEnded();}}});}
        });
    }
    private static void route(){
        main.removeCallbacks(deferred);
        if(pending==0||IntercomSession.active||!AudioOutputGate.INSTANCE.active(pending))return;
        if(!enabled(app))return;
        if(BridgeService.receiverStarted()||genericOutput()){AudioOutputGate.INSTANCE.selectSession(pending);pending=0;}
        else main.postDelayed(deferred,1000);
    }
    private static boolean genericOutput(){
        try{String profile=new ConfigStore(app).load().profile;return !"g10".equals(profile)&&!"a133".equals(profile);}catch(Exception ignored){return false;}
    }
    static void afterCall(){main.post(new Runnable(){public void run(){route();}});}
    static PrefStore settings(Context context){initialize(context);return preferences;}
    static boolean enabled(Context context){return settings(context).isEnabled();}
    static void reconcile(Context context){
        initialize(context);PrefStore prefs=preferences;
        if(Build.VERSION.SDK_INT<27)return;
        Intent start=new Intent(context,ReceiverService.class).setAction(ReceiverService.ACTION_ENSURE_STARTED);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(start);else context.startService(start);
    }
    static void apply(Context context){
        initialize(context);
        context.startForegroundService(new Intent(context,ReceiverService.class).setAction(ReceiverService.ACTION_RECONFIGURE));
    }
    static void restartForWebSettings(Context context){
        initialize(context);app.stopService(new Intent(app,ReceiverService.class));
        main.postDelayed(new Runnable(){public void run(){reconcile(app);}},300);
    }
    static void stoppedLocally(){pending=0;main.removeCallbacks(deferred);AudioOutputGate.INSTANCE.select(false);}
    static String status(){ReceiverState s=ReceiverState.INSTANCE;return s.getState().name()+(s.getErrorMessage()==null?"":" - "+s.getErrorMessage());}
    private ReceiverRuntime(){}
}
