package com.wallpanel.multiroom;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import com.wallpanel.audioreceiver.PrefStore;
/** Same local group UI as mobile browsers; no external website or HA required. */
public final class GroupManagerActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  if(!MusicLibrary.allowed(this))requestPermissions(new String[]{MusicLibrary.permission()},93);
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);Button back=new Button(this);back.setText("Back");back.setOnClickListener(v->finish());page.addView(back);web=new WebView(this);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
  PrefStore prefs=new PrefStore(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
  final String base="http://127.0.0.1:"+prefs.getHaPort();web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !url.equals(base+"/groups")&&!url.equals(base+"/");}@Override public void onPageFinished(WebView view,String url){if(url.equals(base+"/groups")||url.equals(base+"/"))web.evaluateJavascript("window.setPanelToken("+org.json.JSONObject.quote(prefs.getHaToken())+")",null);}});web.loadUrl(base+"/groups");
 }
 @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
