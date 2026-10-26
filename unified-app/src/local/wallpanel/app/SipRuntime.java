package local.wallpanel.app;
import android.content.*;
import android.os.Build;
/** Independent optional SIP service. No MQTT/profile requirement and no test calls. */
final class SipRuntime {
    static volatile SipService current;
    static void reconcile(Context context){NetworkRouter.get(context);SipStore store=new SipStore(context);Intent intent=new Intent(context,SipService.class);
        try{if(!store.enabled()){context.stopService(intent);store.status("SIP is disabled");return;}if(!store.configured()){store.status("Finish Intercom settings to enable SIP");return;}store.load().validate();if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}catch(Exception invalid){store.status("SIP settings need attention");}
    }
    static SipEngine.State state(){SipService s=current;return s==null?SipEngine.State.IDLE:s.callState;}
    static boolean inCall(){return state()!=SipEngine.State.IDLE;}
    static void answer(){SipService s=current;if(s!=null&&s.engine!=null)s.engine.answer();}
    static void hangup(){SipService s=current;if(s!=null&&s.engine!=null)s.engine.hangup();}
    private SipRuntime(){}
}
