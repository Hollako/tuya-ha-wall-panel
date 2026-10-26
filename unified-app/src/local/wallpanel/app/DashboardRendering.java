package local.wallpanel.app;

import android.content.Context;
import android.os.Build;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Target API 28 enables native CSS RGBA hex parsing. This only records its result. */
final class DashboardRendering {
    static void apply(Context context,WebView web){try{
        java.io.InputStream in=context.getAssets().open("dashboard-theme-compat.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try{byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>65536)throw new java.io.IOException("Compatibility asset too large");}}finally{in.close();}
        web.evaluateJavascript(new String(out.toByteArray(),"UTF-8"),null);
    }catch(Exception ignored){}inspect(context,web);}
    // A hidden, immediately removed probe; no stylesheet rewriting or page reload.
    static final String CHECK="(function(){var e=document.createElement('span');e.style.setProperty('display','none','important');document.documentElement.appendChild(e);try{function color(v){e.style.setProperty('color',v,'important');return getComputedStyle(e).color;}var eight=color('#01020380'),four=color('#0f08'),zero=color('#0000');function rgba(v,r,g,b,a){var n=v.match(/[0-9.]+/g);return n&&n.length===4&&+n[0]===r&&+n[1]===g&&+n[2]===b&&Math.abs(+n[3]-a)<0.005;}return JSON.stringify({native_alpha_correct:!!(rgba(eight,1,2,3,128/255)&&rgba(four,0,255,0,136/255)&&rgba(zero,0,0,0,0)),eight_digit_color:eight,four_digit_color:four,transparent_color:zero});}finally{e.remove();}})()";
    static void inspect(final Context context,WebView web){
        web.evaluateJavascript(CHECK,new ValueCallback<String>(){public void onReceiveValue(String value){try{
            Object decoded=new JSONTokener(value).nextValue();if(!(decoded instanceof String))return;
            JSONObject result=new JSONObject((String)decoded);result.put("app_target_sdk",context.getApplicationInfo().targetSdkVersion);
            if(Build.VERSION.SDK_INT>=26){android.content.pm.PackageInfo provider=WebView.getCurrentWebViewPackage();if(provider!=null)result.put("webview_version",provider.versionName);}
            FileOutputStream out=context.openFileOutput("dashboard-rendering-status.json",Context.MODE_PRIVATE);try{out.write(result.toString().getBytes("UTF-8"));}finally{out.close();}
        }catch(Exception ignored){}}});
    }
    private DashboardRendering(){}
}
