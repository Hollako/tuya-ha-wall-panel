package com.wallpanel.multiroom;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import com.wallpanel.audioreceiver.PrefStore;
/** Same local group UI as mobile browsers; no external website or HA required. */
public final class GroupManagerActivity extends Activity {
 private WebView web;private PrefStore preferences;private final android.os.Handler handler=new android.os.Handler();private int attempts;
 private boolean local(String url){try{java.net.URI uri=new java.net.URI(url);return uri.getScheme().equals("http")&&uri.getHost().equals("127.0.0.1")&&uri.getPort()==preferences.getHaPort()&&java.util.Arrays.asList("/","/groups","/settings").contains(uri.getPath());}catch(Exception invalid){return false;}}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  String path="/settings".equals(getIntent().getStringExtra("path"))?"/settings":"/";
  if(!path.equals("/settings")&&!MusicLibrary.allowed(this))requestPermissions(new String[]{MusicLibrary.permission()},93);
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);LinearLayout tools=new LinearLayout(this);Button back=new Button(this);back.setText("Back");back.setOnClickListener(v->onBackPressed());tools.addView(back,new LinearLayout.LayoutParams(0,-2,1));Button keyboard=new Button(this);keyboard.setText("Hide keyboard");keyboard.setOnClickListener(v->((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0));tools.addView(keyboard,new LinearLayout.LayoutParams(0,-2,1));page.addView(tools);web=new WebView(this);web.setWebChromeClient(new android.webkit.WebChromeClient());page.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
  preferences=new PrefStore(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
  final String base="http://127.0.0.1:"+preferences.getHaPort();web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !local(url);}@Override public void onPageFinished(WebView view,String url){if(local(url))web.evaluateJavascript("window.setPanelSession&&window.setPanelSession({token:"+org.json.JSONObject.quote(preferences.getHaToken())+",admin:"+org.json.JSONObject.quote(com.wallpanel.audioreceiver.SettingsHub.localKey())+"})",null);}
   @Override public void onReceivedError(WebView view,int code,String description,String failing){if(local(failing)&&attempts++<15)handler.postDelayed(()->{if(web!=null)web.loadUrl(failing);},700);}
  });String section=getIntent().getStringExtra("section");web.loadUrl(base+path+(path.equals("/settings")&&section!=null&&section.matches("[a-z_]{1,32}")?"#section-"+section:""));
 }
 @Override public void onBackPressed(){if(web==null){finish();return;}web.evaluateJavascript("window.panelHasUnsaved?window.panelHasUnsaved():false",value->{if("true".equals(value))new android.app.AlertDialog.Builder(this).setTitle("Unsaved settings").setMessage("Leave this page and discard its unfinished edits?").setNegativeButton("Stay",null).setPositiveButton("Leave",(d,w)->leave()).show();else leave();});}
 private void leave(){if(web==null){finish();return;}web.evaluateJavascript("window.panelDiscard=true",ignored->{if(web!=null&&web.canGoBack())web.goBack();else finish();});}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
