package com.wallpanel.audioreceiver;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Dedicated receiver uses the same local audio/settings UI as the unified product. */
public class MainActivity extends Activity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);startForegroundService(new Intent(this,ReceiverService.class).setAction(ReceiverService.ACTION_ENSURE_STARTED));startActivity(new Intent(this,com.wallpanel.multiroom.GroupManagerActivity.class));finish();}
}
