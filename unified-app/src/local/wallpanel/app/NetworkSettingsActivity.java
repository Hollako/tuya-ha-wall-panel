package local.wallpanel.app;
import android.app.Activity;
import android.os.*;
import android.widget.*;
import android.view.View;
import java.io.*;
import org.json.JSONObject;

/** Private administrator choices, independent of the main setup draft. */
public final class NetworkSettingsActivity extends Activity {
    private NetworkRouter router;private Spinner ha,sip;private CheckBox haFallback,sipFallback;private TextView status;private final Handler main=new Handler();
    private Spinner choice(LinearLayout page,String title,String value){TextView label=new TextView(this);label.setText(title);label.setTextSize(20);page.addView(label);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,NetworkPolicy.NAMES);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(NetworkPolicy.index(value));page.addView(s);return s;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);router=NetworkRouter.get(this);ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(24,24,24,24);scroll.addView(page);setContentView(scroll);
        try{importPending();}catch(Exception e){Toast.makeText(this,"Network import failed; previous choices retained",Toast.LENGTH_LONG).show();}
        NetworkPolicy p=router.load();TextView title=new TextView(this);title.setText("Network assignments");title.setTextSize(27);page.addView(title);ha=choice(page,"HA dashboard and MQTT",p.ha);haFallback=new CheckBox(this);haFallback.setText("Allow HA to use Android's default if its selected interface is unavailable");haFallback.setChecked(p.haFallback);page.addView(haFallback);sip=choice(page,"Intercom: SIP, audio, video and door unlock",p.intercom);sipFallback=new CheckBox(this);sipFallback.setText("Allow intercom to use Android's default if its selected interface is unavailable");sipFallback.setChecked(p.intercomFallback);page.addView(sipFallback);
        TextView note=new TextView(this);note.setText("Fallback applies when an interface is missing, not when a server rejects a login. Network IP settings and other apps are not changed. Separate IP ranges are recommended. An explicit intercom interface selects its own IPv4 address. The SIP local-IP override only applies in Auto mode.");page.addView(note);status=new TextView(this);status.setPadding(0,20,0,20);page.addView(status);
        Button save=new Button(this);save.setText("Save network assignments");page.addView(save);save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{NetworkPolicy next=new NetworkPolicy();next.ha=NetworkPolicy.IDS[ha.getSelectedItemPosition()];next.intercom=NetworkPolicy.IDS[sip.getSelectedItemPosition()];next.haFallback=haFallback.isChecked();next.intercomFallback=sipFallback.isChecked();apply(next);Toast.makeText(NetworkSettingsActivity.this,"Network choices saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(NetworkSettingsActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}}});Button back=new Button(this);back.setText("Back to setup");page.addView(back);back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});
    }
    private void apply(NetworkPolicy p){NetworkPolicy old=router.load();if(SipRuntime.inCall()&&(!old.intercom.equals(p.intercom)||old.intercomFallback!=p.intercomFallback))throw new IllegalStateException("End the call before changing the intercom network");router.save(p);}
    private void importPending()throws Exception{File f=new File(getFilesDir(),"network-settings-import.json");if(!f.exists())return;if(f.length()>4096)throw new IOException("Invalid import");byte[] b=new byte[(int)f.length()];FileInputStream in=new FileInputStream(f);try{int at=0,n;while(at<b.length&&(n=in.read(b,at,b.length-at))>0)at+=n;if(at!=b.length)throw new IOException("Incomplete import");}finally{in.close();}JSONObject d=new JSONObject(new String(b,"UTF-8"));NetworkPolicy p=new NetworkPolicy();p.ha=d.getString("ha_network");p.intercom=d.getString("intercom_network");p.haFallback=d.optBoolean("ha_fallback",false);p.intercomFallback=d.optBoolean("intercom_fallback",false);apply(p);if(!f.delete())throw new IOException("Remove protected import file");if(d.optBoolean("probe",false))NetworkDiagnostics.run(this);}
    private final Runnable tick=new Runnable(){public void run(){status.setText(router.summary());main.postDelayed(this,1000);}};
    @Override protected void onResume(){super.onResume();main.post(tick);}
    @Override protected void onPause(){main.removeCallbacksAndMessages(null);super.onPause();}
}
