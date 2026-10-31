package local.wallpanel.app;
/** Compatibility entry point; network choices use the same app/web configuration. */
public final class NetworkSettingsActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,MainActivity.class).putExtra("settings_section","panel_networks"));finish();}
}
