package com.wallpanel.multiroom;
/** Compatibility entry point; no second source/volume/playback form. */
public final class GroupSettingsActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent().setClassName(this,getPackageName()+".MainActivity").putExtra("audio_page",true));finish();}
}
