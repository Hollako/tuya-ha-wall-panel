package com.wallpanel.audioreceiver;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
/** Android-owned bonded-device chooser; LAN pages request it, pairing stays on the panel. */
public final class BluetoothConnectActivity extends Activity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("Choose a paired phone on the panel");text.setPadding(24,24,24,24);setContentView(text);BluetoothAudioConnect.choose(this,message->{Toast.makeText(this,message,Toast.LENGTH_LONG).show();finish();});}
}
