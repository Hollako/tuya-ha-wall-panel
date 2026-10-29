package local.wallpanel.app;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Window changes only: no content retrieval, key filtering or automatic Back. */
public final class SettingsBackService extends AccessibilityService {
    private static SettingsBackService instance;private static String resumedClass;
    private WindowManager windows;private View button;private WindowManager.LayoutParams layout;private boolean visible;
    static void resumed(Activity activity){resumedClass=activity.getClass().getName();SettingsBackService service=instance;if(service!=null)service.display(SettingsBackPolicy.setup(resumedClass));}
    static void paused(Activity activity){if(activity.getClass().getName().equals(resumedClass)){resumedClass=null;if(instance!=null)instance.display(false);}}
    static boolean enabled(android.content.Context context){
        if(android.provider.Settings.Secure.getInt(context.getContentResolver(),"accessibility_enabled",0)!=1)return false;
        String services=android.provider.Settings.Secure.getString(context.getContentResolver(),"enabled_accessibility_services");if(services==null)return false;
        android.content.ComponentName component=new android.content.ComponentName(context,SettingsBackService.class);
        for(String item:services.split(":"))if(component.equals(android.content.ComponentName.unflattenFromString(item)))return true;return false;
    }
    @Override protected void onServiceConnected(){instance=this;windows=(WindowManager)getSystemService(WINDOW_SERVICE);display(SettingsBackPolicy.setup(resumedClass));}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString(),name=event.getClassName()==null?"":event.getClassName().toString();
        if(SettingsBackPolicy.androidSettings(pkg)){display(true);return;}
        if(getPackageName().equals(pkg)){if(SettingsBackPolicy.setup(name))display(true);else if(name.startsWith("local.wallpanel.app.")&&name.endsWith("Activity"))display(false);return;}
        if(!SettingsBackPolicy.keyboard(pkg,name))display(false);
    }
    private void display(boolean show){
        if(IntercomSession.active)show=false;if(show==visible)return;
        if(!show){visible=false;if(button!=null){try{windows.removeView(button);}catch(Exception ignored){}button=null;}return;}
        if(windows==null)return;final float density=getResources().getDisplayMetrics().density;final int size=(int)(56*density+.5f);
        View view=new View(this){private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){float s=getWidth(),h=getHeight();paint.setStyle(Paint.Style.FILL);paint.setColor(0xee263d4d);canvas.drawCircle(s/2,h/2,s*.47f,paint);
                paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.8f*density);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
                Path arrow=new Path();arrow.moveTo(s*.52f,h*.32f);arrow.lineTo(s*.34f,h*.5f);arrow.lineTo(s*.52f,h*.68f);canvas.drawPath(arrow,paint);canvas.drawLine(s*.34f,h*.5f,s*.68f,h*.5f,paint);}
        };
        view.setContentDescription("Back");view.setClickable(true);view.setOnClickListener(v->{if(!performGlobalAction(GLOBAL_ACTION_BACK))Toast.makeText(this,"Back is unavailable",Toast.LENGTH_SHORT).show();});
        layout=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=getResources().getDisplayMetrics().widthPixels-size-(int)(20*density);layout.y=getResources().getDisplayMetrics().heightPixels-size-(int)(40*density);
        view.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int x,y;boolean dragged;
            public boolean onTouch(View v,MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getRawX();startY=event.getRawY();x=layout.x;y=layout.y;dragged=false;return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-startX,dy=event.getRawY()-startY;if(Math.abs(dx)+Math.abs(dy)>8*density)dragged=true;
                    if(dragged){layout.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-size,x+(int)dx));layout.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-size,y+(int)dy));try{windows.updateViewLayout(v,layout);}catch(Exception ignored){}}return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_UP){if(!dragged)v.performClick();return true;}return event.getActionMasked()==MotionEvent.ACTION_CANCEL;
            }
        });
        try{windows.addView(view,layout);button=view;visible=true;}catch(Exception ignored){button=null;visible=false;}
    }
    @Override public void onInterrupt(){display(false);}
    @Override public boolean onUnbind(Intent intent){display(false);if(instance==this)instance=null;return super.onUnbind(intent);}
    @Override public void onDestroy(){display(false);if(instance==this)instance=null;super.onDestroy();}
}
