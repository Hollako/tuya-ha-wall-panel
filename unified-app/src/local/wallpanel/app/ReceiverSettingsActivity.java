package local.wallpanel.app;
/** Compatibility entry point; settings have one implementation. */
public final class ReceiverSettingsActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,MainActivity.class).putExtra("settings_section","airplay"));finish();}
}
