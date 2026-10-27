package local.wallpanel.app;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;

/** Optional external-app supervisor. Never controls the target's internal UI. */
public final class ExternalAppService extends Service {
    private static final String OPEN="local.wallpanel.app.APP_OPEN",RETURN="local.wallpanel.app.APP_RETURN";
    private static boolean administrator;private static ExternalAppService instance;
    static boolean guarding(){ExternalAppService s=instance;return s!=null&&s.ready&&!s.closing;}
    private final Handler main=new Handler();private ConfigStore store;private WindowManager windows;
    private View hotspot;private SystemUiGuard guard;private PowerManager.WakeLock wake;
    private String target="";private boolean ready,closing,callWasActive;private long lastLaunch;
    static boolean configured(Context c){PanelConfig p=new ConfigStore(c).dashboardSettings();return "app".equals(p.displayMode)&&p.selected.contains("dashboard");}
    static void open(Context c){administrator=false;start(c,OPEN);}
    static void reconcile(Context c){if(!administrator&&configured(c))start(c,OPEN);else if(!configured(c))c.stopService(new Intent(c,ExternalAppService.class));}
    static void afterCall(Context c){if(!administrator&&configured(c))start(c,RETURN);}
    static void administratorAuthenticated(Context c){administrator=true;c.stopService(new Intent(c,ExternalAppService.class));}
    private static void start(Context c,String action){Intent i=new Intent(c,ExternalAppService.class).setAction(action);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();instance=this;store=new ConfigStore(this);windows=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(administrator||!configured(this)){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Notification.Builder notice;
        if(Build.VERSION.SDK_INT>=26){manager.createNotificationChannel(new NotificationChannel("wallpanel_appmode","Wall Panel app mode",NotificationManager.IMPORTANCE_LOW));notice=new Notification.Builder(this,"wallpanel_appmode");}else notice=new Notification.Builder(this);
        startForeground(44,notice.setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Tuya HA Wall Panel").setContentText("App mode active").setOngoing(true).build());
        if(!Settings.canDrawOverlays(this)){status("Grant the administrator hotspot overlay permission in setup.");stopSelf();return START_NOT_STICKY;}
        final String desired;
        try{desired=AppModePolicy.packageName(store.dashboardSettings().appPackage);if(getPackageManager().getLaunchIntentForPackage(desired)==null)throw new IllegalArgumentException();}
        catch(Exception e){status("Selected app is unavailable. Open setup to choose another app.");stopSelf();return START_NOT_STICKY;}
        if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:appmode");wake.acquire();}
        if(!desired.equals(target)||guard==null){
            ready=false;if(guard!=null)guard.close();target=desired;
            // A prior dashboard/PIN handoff must release its root lease first.
            SystemUiGuard.administratorAuthenticated();
            guard=new SystemUiGuard(this,target,new SystemUiGuard.Listener(){public void ready(final boolean value){main.post(new Runnable(){public void run(){if(closing||!desired.equals(target))return;ready=value;status(value?"App mode active":"Waiting for app-mode root support. Prepare / repair panel if needed.");if(value){showHotspot();if(hotspot!=null)launch();}}});}});guard.start();
        }else if(ready)launch();
        main.removeCallbacks(tick);main.post(tick);return START_STICKY;
    }
    private void status(String value){getSharedPreferences("wall_panel",MODE_PRIVATE).edit().putString("app_mode_status",value).apply();}
    private final Runnable relaunch=new Runnable(){public void run(){launch();}};
    private void launch(){
        if(closing||!ready||administrator||IntercomSession.active||hotspot==null)return;
        long wait=1500-(SystemClock.elapsedRealtime()-lastLaunch);if(wait>0){main.removeCallbacks(relaunch);main.postDelayed(relaunch,wait);return;}
        Intent intent=getPackageManager().getLaunchIntentForPackage(target);if(intent==null){status("Selected app is unavailable. Open setup.");return;}
        try{lastLaunch=SystemClock.elapsedRealtime();startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}
        catch(Exception e){status("Selected app could not open. Open setup.");}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(closing)return;if(administrator||!configured(ExternalAppService.this)){stopSelf();return;}
        boolean call=IntercomSession.active;
        if(call)removeHotspot();else if(ready){showHotspot();if(callWasActive)launch();}
        callWasActive=call;main.postDelayed(this,300);
    }};
    private void showHotspot(){
        if(hotspot!=null||IntercomSession.active||!ready||closing)return;
        View view=new View(this);view.setContentDescription("Hold to open administrator settings");
        view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){startActivity(new Intent(ExternalAppService.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;}});
        int size=(int)(28*getResources().getDisplayMetrics().density+.5f);
        WindowManager.LayoutParams layout=new WindowManager.LayoutParams(size,size,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;
        try{windows.addView(view,layout);hotspot=view;}catch(Exception e){status("Administrator hotspot unavailable. Check overlay permission.");stopSelf();}
    }
    private void removeHotspot(){View view=hotspot;hotspot=null;if(view!=null)try{windows.removeView(view);}catch(Exception ignored){}}
    @Override public void onDestroy(){closing=true;if(instance==this)instance=null;main.removeCallbacksAndMessages(null);removeHotspot();if(guard!=null)guard.close();guard=null;if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);super.onDestroy();}
}
