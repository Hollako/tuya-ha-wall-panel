package local.wallpanel.app;
/** Compatibility entry point; Bluetooth configuration is in shared settings. */
public final class BluetoothSettingsActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,MainActivity.class).putExtra("settings_section","bluetooth"));finish();}
}
