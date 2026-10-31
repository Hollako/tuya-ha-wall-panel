package local.wallpanel.app;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

/** Native administrator gate; all editable settings use the shared local web contract. */
public final class MainActivity extends Activity {
 private ConfigStore store;
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);store=new ConfigStore(this);BridgeService.ensureStarted(this);SipRuntime.reconcile(this);ReceiverRuntime.reconcile(this);if(store.locked())authenticate();else open();}
 private void authenticate(){
  if(store.pinWaitMillis()>0){Toast.makeText(this,"Wait 30 seconds before trying again",Toast.LENGTH_LONG).show();finish();return;}
  EditText pin=new EditText(this);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  new AlertDialog.Builder(this).setTitle("Administrator PIN").setView(pin).setCancelable(false).setNegativeButton("Cancel",(d,w)->finish()).setPositiveButton("Unlock",(d,w)->{try{if(store.verify(pin.getText().toString())){open();return;}}catch(Exception ignored){}Toast.makeText(this,"Incorrect PIN",Toast.LENGTH_SHORT).show();authenticate();}).show();
 }
 private void open(){ExternalAppService.administratorAuthenticated(this);SystemUiGuard.administratorAuthenticated();Intent portal=new Intent(this,com.wallpanel.multiroom.GroupManagerActivity.class).putExtra("path",getIntent().getBooleanExtra("audio_page",false)?"/":"/settings");String section=getIntent().getStringExtra("settings_section");if(section!=null&&section.matches("[a-z_]{1,32}"))portal.putExtra("section",section);startActivity(portal);finish();}
}
