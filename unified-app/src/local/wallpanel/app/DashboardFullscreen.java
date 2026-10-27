package local.wallpanel.app;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Dashboard-local policy. No global UI settings or other-app window changes. */
final class DashboardFullscreen {
    static final int FLAGS=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    private static final int HIDDEN=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    private final Window window;
    private final View decor;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed,closed;
    private final Runnable restore=new Runnable(){public void run(){
        if(resumed&&!closed&&decor.hasWindowFocus())apply();
    }};
    DashboardFullscreen(Window target){
        window=target;decor=window.getDecorView();
        decor.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener(){
            public void onSystemUiVisibilityChange(int visibility){
                if(resumed&&!closed&&decor.hasWindowFocus()&&(visibility&HIDDEN)!=HIDDEN){
                    handler.removeCallbacks(restore);handler.postDelayed(restore,100);
                }
            }
        });
        apply();
    }
    void apply(){
        if(closed)return;
        // View flags may be cleared by menus/IME transitions. The window-level
        // flag prevents DecorView from retaining a status-bar inset/background.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
        if(decor.getSystemUiVisibility()!=FLAGS)decor.setSystemUiVisibility(FLAGS);
    }
    void resume(){if(!closed){resumed=true;apply();}}
    void pause(){resumed=false;handler.removeCallbacks(restore);}
    void focus(boolean focused){if(focused&&resumed&&!closed)apply();}
    void close(){pause();closed=true;decor.setOnSystemUiVisibilityChangeListener(null);}
}
