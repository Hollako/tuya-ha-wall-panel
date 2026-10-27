package local.wallpanel.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Dashboard viewer only; no hardware access, shell, JS bridge or TLS bypass. */
public class DashboardActivity extends Activity {
    private static java.lang.ref.WeakReference<DashboardActivity> current=new java.lang.ref.WeakReference<DashboardActivity>(null);
    static void reloadFromRemote(final Context context){
        new Handler(android.os.Looper.getMainLooper()).post(new Runnable(){public void run(){
            DashboardActivity active=current.get();
            if(active!=null&&!active.closed&&active.web!=null){
                active.web.stopLoading();active.handler.removeCallbacks(active.retry);
                if(active.router.selected(false)==null){active.loaded=false;active.loadWhenConnected();}
                else if(active.loaded)active.web.reload();
                else active.loadWhenConnected();
            }else context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }});
    }
    private WebView web;
    private TextView message;
    private final Handler handler=new Handler();
    private String url,networkKey="";
    private NetworkRouter router;
    private final NetworkRouter.Listener networkChanges=new NetworkRouter.Listener(){public void changed(){if(!closed&&web!=null){String next=router.key(false);if(!next.equals(networkKey)){networkKey=next;loaded=false;web.stopLoading();loadWhenConnected();}}}};
    private SystemUiGuard systemUi;
    private DashboardFullscreen fullscreen;
    private boolean loaded=false,closed=false,locked=false,setupShown=false,appView=false;
    private final Runnable retry=new Runnable(){public void run(){loadWhenConnected();}};
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=new SharedPreferences.OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(SharedPreferences prefs,String key){if("dashboard".equals(key)||"display_mode".equals(key)||"app_package".equals(key)||"modules".equals(key))handler.post(new Runnable(){public void run(){if(!closed)refreshDashboard();}});}
    };
    @Override protected void onStart(){super.onStart();router=NetworkRouter.get(this);router.add(networkChanges);networkChanges.changed();getSharedPreferences("wall_panel",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changes);}
    @Override protected void onStop(){getSharedPreferences("wall_panel",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changes);if(router!=null)router.remove(networkChanges);super.onStop();}
    protected boolean isHome(){return false;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);fullscreen=new DashboardFullscreen(getWindow());current=new java.lang.ref.WeakReference<DashboardActivity>(this);router=NetworkRouter.get(this);refreshDashboard();}
    @Override protected void onResume(){super.onResume();if(fullscreen!=null)fullscreen.resume();if(!closed){refreshDashboard();if(web!=null&&systemUi==null){systemUi=SystemUiGuard.takeAdministratorLease();if(systemUi==null){systemUi=new SystemUiGuard(this);systemUi.start();}}}}
    @Override protected void onPause(){if(fullscreen!=null)fullscreen.pause();if((locked||IntercomSession.active)&&systemUi!=null){SystemUiGuard.holdForAdministrator(systemUi);systemUi=null;}else releaseSystemUi();super.onPause();}
    private void releaseSystemUi(){SystemUiGuard guard=systemUi;systemUi=null;if(guard!=null)guard.close();}
    protected final void refreshDashboard(){
        if(closed)return;
        PanelConfig c=new ConfigStore(this).dashboardSettings();locked=c.lock;
        if("app".equals(c.displayMode)&&c.selected.contains("dashboard")){
            releaseSystemUi();releaseWeb();SystemUiGuard.administratorAuthenticated();
            if(!appView){appView=true;setupShown=false;LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,32,32,32);
                TextView note=new TextView(this);note.setText("Opening selected app. App mode needs prepared root support and the administrator hotspot permission.");note.setTextSize(20);layout.addView(note);
                Button setup=new Button(this);setup.setText("Open setup");setup.setOnClickListener(new View.OnClickListener(){public void onClick(View v){openSetup();}});layout.addView(setup);setContentView(layout);
            }
            ExternalAppService.open(this);return;
        }
        if(appView){appView=false;setupShown=false;}
        stopService(new Intent(this,ExternalAppService.class));
        int route=HomePolicy.route(c.dashboard,c.selected.contains("dashboard"),url,web!=null);
        if(route==HomePolicy.KEEP){immersive();return;}
        if(route==HomePolicy.SETUP){if(!isHome()){finish();return;}showSetup();return;}
        releaseWeb();setupShown=false;url=PanelConfig.webUrl(c.dashboard,false);loaded=false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WebView.setWebContentsDebuggingEnabled(false);
        FrameLayout frame=new FrameLayout(this);web=new WebView(this);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        message=new TextView(this);message.setTextColor(Color.WHITE);message.setBackgroundColor(0xcc222222);message.setPadding(32,24,32,24);message.setText("Waiting for network…");
        FrameLayout.LayoutParams banner=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);frame.addView(message,banner);
        View corner=new View(this);FrameLayout.LayoutParams hotspot=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP|Gravity.LEFT);frame.addView(corner,hotspot);
        corner.setContentDescription("Hold to open administrator settings");corner.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){openSetup();return true;}});
        setContentView(frame);immersive();
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String target){return !allowed(target);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl().toString());}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(v==web&&r.isForMainFrame())failed("Dashboard unavailable. Retrying…");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(v==web&&r.isForMainFrame()&&response.getStatusCode()>=500)failed("Dashboard server unavailable. Retrying…");}
            @Override public void onPageFinished(WebView v,String current){if(v==web&&loaded){message.setVisibility(View.GONE);DashboardRendering.apply(DashboardActivity.this,v);try{java.io.FileOutputStream out=openFileOutput("dashboard-network-status.json",MODE_PRIVATE);try{out.write(("{\"loaded\":true,\"network\":\""+router.key(false)+"\"}").getBytes("UTF-8"));}finally{out.close();}}catch(Exception ignored){}}}
        });
        loadWhenConnected();
    }
    private void openSetup(){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}
    private void showSetup(){
        releaseSystemUi();
        if(setupShown)return;releaseWeb();setupShown=true;url=null;loaded=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,32,32,32);
        TextView title=new TextView(this);title.setText("Tuya HA Wall Panel");title.setTextSize(28);layout.addView(title);
        TextView note=new TextView(this);note.setText("Set up a dashboard URL and enable the Dashboard module. Administrator settings remain available without Wi-Fi or Home Assistant.");note.setTextSize(18);layout.addView(note);
        Button setup=new Button(this);setup.setText("Open setup");setup.setOnClickListener(new View.OnClickListener(){public void onClick(View v){openSetup();}});layout.addView(setup);
        setContentView(layout);immersive();openSetup();
    }
    private void releaseWeb(){handler.removeCallbacksAndMessages(null);WebView old=web;web=null;if(old!=null){old.stopLoading();old.destroy();}}
    private boolean allowed(String target){try{PanelConfig.webUrl(target,false);return true;}catch(IllegalArgumentException e){Toast.makeText(this,"Only HTTP/HTTPS navigation is available in this build",Toast.LENGTH_SHORT).show();return false;}}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void immersive(){if(fullscreen!=null)fullscreen.apply();}
    private void failed(String reason){if(closed)return;loaded=false;message.setText(reason);message.setVisibility(View.VISIBLE);handler.removeCallbacks(retry);handler.postDelayed(retry,5000);}
    private void loadWhenConnected(){
        if(closed||loaded||web==null)return;
        if(router.selected(false)==null){message.setText("Waiting for network…");message.setVisibility(View.VISIBLE);handler.removeCallbacks(retry);handler.postDelayed(retry,2000);return;}
        networkKey=router.key(false);loaded=true;web.loadUrl(url);
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(fullscreen!=null)fullscreen.focus(focused);}
    @Override public void onBackPressed(){if(!isHome()&&!locked)finish();}
    @Override protected void onDestroy(){if(current.get()==this)current.clear();closed=true;if(fullscreen!=null)fullscreen.close();releaseSystemUi();releaseWeb();super.onDestroy();}
}
