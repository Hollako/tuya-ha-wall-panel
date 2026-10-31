package local.wallpanel.app;
/** Compatibility entry point; SIP fields use the same encrypted store and shared renderer. */
public final class SipSettingsActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,MainActivity.class).putExtra("settings_section","intercom"));finish();}
}
