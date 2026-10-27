package local.wallpanel.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.*;
import android.view.View;
import com.wallpanel.audioreceiver.*;

/** Unified product settings; the engine is shared with the dedicated receiver. */
public final class ReceiverSettingsActivity extends Activity {
    private LinearLayout form;
    private EditText name,offset,trim,iface;
    private CheckBox enabled;
    private TextView status;
    private String message="";
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){status.setText((message.isEmpty()?"":message+"\n")+ReceiverRuntime.status());handler.postDelayed(this,1000);}};
    private void text(String value){TextView t=new TextView(this);t.setText(value);t.setPadding(0,12,0,6);form.addView(t);}
    private EditText field(String label,String value,boolean number){text(label);EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);form.addView(e);return e;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);final PrefStore prefs=new PrefStore(this);
        ScrollView scroll=new ScrollView(this);form=new LinearLayout(this);form.setOrientation(1);form.setPadding(24,20,24,24);scroll.addView(form);setContentView(scroll);
        text("Audio receiver");text("AirPlay uses the shared ARM32/ARM64 engine. SIP calls silence its output while timing continues. Bluetooth remains firmware-managed and is not enabled by these settings.");
        enabled=new CheckBox(this);enabled.setText("Enable AirPlay receiver");enabled.setChecked(prefs.isEnabled());form.addView(enabled);
        name=field("Receiver name",prefs.getReceiverName(),false);
        offset=field("Timing offset in milliseconds (-500 to 500)",""+prefs.getLatencyOffsetMs(),true);
        trim=field("Receiver trim (0 to 100%)",""+prefs.getVolumeTrim(),true);
        iface=field("Network interface (blank follows HA network; e.g. wlan0 or eth0)",prefs.getInterfaceName(),false);
        text("The one-time AirPlay helper must authorize this app before enabling. Existing standalone settings and Bluetooth bonds remain untouched. The unified app does not open the receiver's optional web server.");
        status=new TextView(this);form.addView(status);
        Button save=new Button(this);save.setText("Save receiver settings");form.addView(save);
        save.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            try{
                String label=name.getText().toString().trim(),network=iface.getText().toString().trim();
                int timing=Integer.parseInt(offset.getText().toString()),level=Integer.parseInt(trim.getText().toString());
                if(label.length()==0||label.length()>63||label.indexOf('\n')>=0||label.indexOf('\r')>=0)throw new IllegalArgumentException("Use a receiver name of 1 to 63 characters");
                if(timing< -500||timing>500||level<0||level>100)throw new IllegalArgumentException("Check timing and trim limits");
                if(!network.matches("[A-Za-z0-9_.-]{0,32}"))throw new IllegalArgumentException("Invalid interface name");
                boolean restart=prefs.isEnabled()!=enabled.isChecked()||!prefs.getReceiverName().equals(label)||!prefs.getInterfaceName().equals(network);
                if(enabled.isChecked()&&android.os.Build.VERSION.SDK_INT<27)throw new IllegalArgumentException("The receiver requires Android 8.1 or newer");
                if(enabled.isChecked()&&!com.wallpanel.audioreceiver.PrivilegeChannel.available())throw new IllegalArgumentException("AirPlay helper is not prepared for Tuya HA Wall Panel. Keep the standalone receiver running until migration is prepared.");
                prefs.put(PrefStore.KEY_RECEIVER_NAME,label);prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,timing);prefs.put(PrefStore.KEY_VOLUME_TRIM,level);prefs.put(PrefStore.KEY_INTERFACE_NAME,network);
                prefs.put(PrefStore.KEY_HA_ENABLED,false);prefs.put(PrefStore.KEY_AUTOSTART,true);prefs.put(PrefStore.KEY_ENABLED,enabled.isChecked());
                ReceiverState.INSTANCE.setVolumeTrim(level);
                if(restart)ReceiverRuntime.apply(ReceiverSettingsActivity.this);else ReceiverRuntime.reconcile(ReceiverSettingsActivity.this);
                Toast.makeText(ReceiverSettingsActivity.this,"Receiver settings saved",Toast.LENGTH_SHORT).show();
                message="";
            }catch(Exception failed){message=failed.getMessage();status.setText(message);}
        }});
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
}
