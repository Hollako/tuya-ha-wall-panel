package local.wallpanel.app;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;

/** Private administrator screen; entering it is read-only until Prepare/Restore. */
public final class PanelSetupActivity extends Activity {
    private TextView status;private Button check,apply,restore;private boolean running;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(28,20,28,24);ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Panel installation");title.setTextSize(26);page.addView(title);
        TextView note=new TextView(this);note.setText("Prepare the root support and corrected startup, private hardware configuration, and conflicting Tuya apps. Existing settings are kept and changes are backed up. Automatic hardware setup currently supports verified G10 / PX30 wiring. No relays, audio, display tests or reboot are performed.");note.setTextSize(16);page.addView(note);
        status=new TextView(this);status.setTextSize(17);status.setPadding(0,20,0,20);page.addView(status);
        check=button(page,"Check installation",0);apply=button(page,"Prepare / repair panel",1);restore=button(page,"Restore previous setup",2);
        Button back=new Button(this);back.setText("Back to settings");back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});page.addView(back);
        TextView help=new TextView(this);help.setText("If Android blocks app-level root, use the supplied one-time ADB installer: python bootstrap_panel.py --device PANEL_IP:5555. The app cannot grant itself root. Then return here to manage setup. Configure the G10 profile, modules, dashboard and MQTT in app settings before starting hardware. WebView installation is separate.");help.setPadding(0,20,0,0);page.addView(help);operate(0);
    }
    private Button button(LinearLayout page,String label,final int action){Button b=new Button(this);b.setText(label);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){operate(action);}});page.addView(b);return b;}
    private void busy(boolean value){running=value;check.setEnabled(!value);apply.setEnabled(!value);restore.setEnabled(!value);}
    private void operate(final int action){if(running)return;busy(true);status.setText(action==0?"Checking panel support...":"Preparing backed-up changes. Keep power connected...");
        new Thread(new Runnable(){public void run(){String message;ConfigStore store=new ConfigStore(getApplicationContext());boolean before=store.runtimeEnabled();JSONObject result;try{result=action==0?PanelSetup.check(getApplicationContext()):PanelSetup.change(getApplicationContext(),action==2);message=describe(result,action);}catch(Exception e){result=new JSONObject();try{result.put("ok",false);result.put("error","setup_exception");result.put("exception",e.getClass().getSimpleName());}catch(Exception ignored){}message="Setup did not complete ("+e.getClass().getSimpleName()+"). Check installation before trying again. Hardware remains stopped after a failed change.";}
            try{result.put("action",action==0?"check":action==1?"prepare":"restore");result.put("runtime_before",before);result.put("runtime_after",store.runtimeEnabled());java.io.FileOutputStream saved=openFileOutput("panel-setup-result.json",MODE_PRIVATE);try{saved.write(result.toString().getBytes("UTF-8"));}finally{saved.close();}}catch(Exception ignored){}
            final String text=message;runOnUiThread(new Runnable(){public void run(){if(!isFinishing()){status.setText(text);busy(false);}}});
        }},"wallpanel-installation").start();
    }
    private String describe(JSONObject data,int action){
        if(!data.optBoolean("ok",false)){String error=data.optString("error","setup_failed");if(error.equals("adb_bootstrap_required"))return "Android did not grant this app root access. Run the one-time ADB installer below; it handles the privilege service, hardware setup and conflicting startup apps together.";
            if(error.equals("unsupported_hardware"))return "This hardware does not match the verified G10 adapter. No automatic changes were applied. Dashboard-only use remains available.";
            if(error.equals("selinux_policy_setup_required"))return "This firmware needs additional permission policy setup. Automatic setup has stopped without weakening Android security.";
            if(error.equals("no_previous_setup"))return "There is no previous setup snapshot yet. Prepare panel creates it. Hardware was not stopped.";
            return "Setup needs attention: "+error.replace('_',' ')+". Your previous setup is backed up.";}
        if(action>0)return action==2?"Previous setup restored. App hardware stays stopped. Review app settings and select your preferred Home app before continuing.":"Panel prepared with backups. Saved configuration was preserved. Choose G10 and your modules in settings. Existing enabled hardware resumes automatically. Reboot and physical checks are yours.";
        if(!data.optBoolean("root",false))return (data.optBoolean("candidate_a133",false)?"A133 wiring detected.\n":data.optBoolean("candidate_g10",false)?"G10 wiring detected.\n":"Hardware needs a root-level check.\n")+"Panel support is not installed or needs upgrading. Prepare panel can request root; if Android blocks it, use the one-time ADB installer.";
        return "Hardware: "+(data.optBoolean("supported",false)?"verified G10 / PX30":"unsupported layout")+"\nPrivilege service: available\nStartup: "+(data.optBoolean("boot_ready",false)?"configured":"needs preparation")+"\nHardware permission policy: "+(data.optBoolean("policy_ready",false)?"ready":"requires setup")+"\nConflicting apps/startup: "+(data.optBoolean("conflicts",false)?"found":"none enabled")+"\nPrevious setup backup: "+(data.optBoolean("backup_available",false)?"available":"created when you prepare the panel");
    }
    @Override protected void onStop(){super.onStop();if(new ConfigStore(this).locked())finish();}
}
