package local.wallpanel.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private ConfigStore store;
    private LinearLayout form;
    private EditText dashboard,ha,broker,port,user,password,pin,mqttBase,externalPackage;
    private Spinner displayMode;
    private CheckBox tls,lock;
    private Spinner profile;
    private TextView status;
    private TextView homeStatus;
    private TextView hardwareStatus;
    private final Runnable runtimeRefresh=new Runnable(){public void run(){
        if(formReady&&store.dashboardSettings().revision!=loadedRevision){
            if(!dirty)showForm();else status.setText("Saved setup changed from HA. Your open edits are preserved; reopen setup to load the new URL before saving.");
        }
        if(hardwareStatus!=null)hardwareStatus.setText(store.runtimeStatus());draftHandler.postDelayed(this,1000);
    }};
    private Button test;
    private boolean unlocked=false;
    private boolean formReady=false,dirty=false;
    private String committedForm="",lastDraft="";
    private long loadedRevision;
    private final Handler draftHandler=new Handler();
    private final Runnable draftWriter=new Runnable(){public void run(){persistDraft();}};
    private final HashMap<String,CheckBox> modules=new HashMap<String,CheckBox>();
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new ConfigStore(this);if(store.locked())authenticate();else showForm();}
    private void authenticate(){
        if(store.pinWaitMillis()>0){Toast.makeText(this,"Wait 30 seconds before trying again",Toast.LENGTH_LONG).show();finish();return;}
        final EditText entry=new EditText(this);entry.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Administrator PIN").setView(entry).setCancelable(false)
            .setNegativeButton("Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){finish();}})
            .setPositiveButton("Unlock",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                try {if(store.verify(entry.getText().toString())){unlocked=true;showForm();return;}}catch(Exception ignored){}
                Toast.makeText(MainActivity.this,"Incorrect PIN",Toast.LENGTH_SHORT).show();authenticate();
            }}).show();
    }
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,14,0,6);form.addView(t);return t;}
    private EditText field(String title,String value,int type){text(title,14);EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setInputType(type);form.addView(e);return e;}
    private CheckBox check(String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setChecked(value);form.addView(c);return c;}
    private Button button(String label,View.OnClickListener listener){Button b=new Button(this);b.setText(label);b.setOnClickListener(listener);form.addView(b);return b;}
    private void showForm(){
        ExternalAppService.administratorAuthenticated(this);
        SystemUiGuard.administratorAuthenticated();
        final PanelConfig c;
        try{c=store.load();}catch(Exception e){new AlertDialog.Builder(this).setTitle("Settings unavailable").setMessage("Stored credentials could not be decrypted. No settings were overwritten.").setPositiveButton("Close",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){finish();}}).show();return;}
        formReady=false;loadedRevision=c.revision;draftHandler.removeCallbacksAndMessages(null);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);status.setTextSize(15);status.setPadding(16,8,16,4);page.addView(status);
        LinearLayout controls=new LinearLayout(this);
        Button saveButton=new Button(this);saveButton.setText("Save settings");saveButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){save(false);}});
        Button openButton=new Button(this);openButton.setText("Save and open");openButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){save(true);}});
        controls.addView(saveButton,new LinearLayout.LayoutParams(0,-2,1));controls.addView(openButton,new LinearLayout.LayoutParams(0,-2,1));page.addView(controls);
        ScrollView scroll=new ScrollView(this);form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,8,24,32);scroll.addView(form);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);modules.clear();
        String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        text("Tuya HA Wall Panel",26);text("Build "+version+"\nThe app starts saved hardware automatically after boot and updates, and recovers its hardware worker if needed. Hardware stays active while Wi-Fi reconnects. Screenshots refresh every five minutes. Panel installation prepares supported hardware with backups.",15);
        text("Panel identity: "+c.deviceId,13);
        button("Panel installation",new View.OnClickListener(){public void onClick(View v){persistDraft();startActivity(new Intent(MainActivity.this,PanelSetupActivity.class));}});
        button("Network assignments",new View.OnClickListener(){public void onClick(View v){persistDraft();startActivity(new Intent(MainActivity.this,NetworkSettingsActivity.class));}});
        button("Intercom settings",new View.OnClickListener(){public void onClick(View v){persistDraft();startActivity(new Intent(MainActivity.this,SipSettingsActivity.class));}});
        button("Audio receiver settings",new View.OnClickListener(){public void onClick(View v){persistDraft();startActivity(new Intent(MainActivity.this,ReceiverSettingsActivity.class));}});
        homeStatus=text("",14);refreshHomeStatus();
        button("Set as Home app",new View.OnClickListener(){public void onClick(View v){if(save(false))LauncherSupport.chooseHome(MainActivity.this);}});
        text("Choose Tuya HA Wall Panel in Android's Home app settings. Home opens your saved dashboard, or setup if no dashboard is configured. Android's pull-down and navigation controls are blocked while the dashboard is active; setup restores them.",14);
        text("Display mode",21);displayMode=new Spinner(this);displayMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Web dashboard","Installed app"}));displayMode.setSelection("app".equals(c.displayMode)?1:0);form.addView(displayMode);
        dashboard=field("Dashboard URL",c.dashboard,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        externalPackage=field("Selected app package",c.appPackage,InputType.TYPE_CLASS_TEXT);externalPackage.setKeyListener(null);
        button("Choose installed app",new View.OnClickListener(){public void onClick(View v){chooseApp();}});
        button("Allow administrator hotspot",new View.OnClickListener(){public void onClick(View v){persistDraft();startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}});
        text("Installed app mode uses prepared root support and the overlay permission. Hold the top-left corner to open setup; your administrator PIN applies when navigation locking is enabled. Intercom calls take priority. The selected app keeps its own settings and navigation.",14);
        displayMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long id){changed();}});
        ha=field("Home Assistant URL (saved for integration setup)",c.haUrl,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        text("MQTT",21);broker=field("Broker hostname or IP",c.broker,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        port=field("Broker port",String.valueOf(c.port),InputType.TYPE_CLASS_NUMBER);tls=check("TLS (trusted certificate and matching hostname required)",c.tls);
        mqttBase=field("Panel MQTT base topic",c.mqttBase,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        user=field("MQTT username",c.username,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        password=field("MQTT password",c.password,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        test=button("Test MQTT connection",new View.OnClickListener(){public void onClick(View v){probe();}});
        text("Hardware profile",21);profile=new Spinner(this);profile.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Generic / unverified hardware","G10 / PX30","A133 / Android 11 (hardware pending)"}));profile.setSelection(c.profile.equals("g10")?1:c.profile.equals("a133")?2:0);form.addView(profile);
        text("Buttons, relays, knob, audio and display become active after starting the service below. Prepare panel installation first on a new supported device. The worker verifies hardware before taking ownership.",14);
        for(ModuleCatalog.Module m:ModuleCatalog.ALL){CheckBox box=check(m.label+(m.implemented?"":" — not migrated"),c.selected.contains(m.id));modules.put(m.id,box);}
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long id){refreshModules();}});refreshModules();
        text("Hardware service",21);hardwareStatus=text(store.runtimeStatus(),14);
        button("Start / apply hardware",new View.OnClickListener(){public void onClick(View v){
            if(!save(false))return;
            try{PanelConfig selected=store.load();selected.validateBroker();HardwareTopics.base(selected.mqttBase);if(!selected.profile.equals("g10"))throw new IllegalArgumentException(selected.profile.equals("a133")?"A133 direct hardware is pending verification. Dashboard and SIP work independently.":"Select G10 first");store.setRuntimeEnabled(true);BridgeService.ensureStarted(MainActivity.this);}
            catch(Exception e){status.setText("Could not start: "+e.getMessage());}
        }});
        button("Stop hardware",new View.OnClickListener(){public void onClick(View v){store.setRuntimeEnabled(false);store.runtimeStatus("Hardware stopped by administrator");stopService(new Intent(MainActivity.this,BridgeService.class));}});
        text("Dashboard navigation",21);lock=check("Lock Back navigation inside this app",c.lock);
        text("The dashboard blocks Android's pull-down menu, system navigation and notification alerts. Hold the top-left corner to open setup; with navigation locked, your PIN is required. System controls return in setup. HA Open setup remains available.",14);
        pin=field("New administrator PIN (6–12 digits; not kept in drafts; blank keeps existing)","",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        try{
            committedForm=snapshot();String draft=store.loadDraft();
            if(draft!=null){restoreDraft(draft);lastDraft=draft;dirty=!snapshot().equals(committedForm);}
            status.setText(dirty?"Unfinished setup restored. Tap Save settings to apply it.":store.lastSavedAt()>0?"Saved setup loaded.":"Settings loaded. Tap Save settings after editing.");
        }catch(Exception error){status.setText("Saved setup loaded; an unfinished draft could not be restored.");}
        watchFields();formReady=true;
    }
    private void chooseApp(){
        java.util.TreeMap<String,String> choices=new java.util.TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(android.content.pm.ResolveInfo info:getPackageManager().queryIntentActivities(launcher,0)){
            if(info.activityInfo==null||getPackageName().equals(info.activityInfo.packageName))continue;
            String name=info.activityInfo.packageName;if(getPackageManager().getLaunchIntentForPackage(name)==null)continue;
            choices.put(info.loadLabel(getPackageManager()).toString()+" ("+name+")",name);
        }
        final String[] labels=choices.keySet().toArray(new String[choices.size()]);final String[] packages=choices.values().toArray(new String[choices.size()]);
        new AlertDialog.Builder(this).setTitle("Choose installed app").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int index){externalPackage.setText(packages[index]);displayMode.setSelection(1);changed();}}).setNegativeButton("Cancel",null).show();
    }
    private void refreshModules(){String selected=profile.getSelectedItemPosition()==1?"g10":profile.getSelectedItemPosition()==2?"a133":"generic";for(ModuleCatalog.Module m:ModuleCatalog.ALL){CheckBox box=modules.get(m.id);box.setEnabled(m.supported(selected));box.setAlpha(m.supported(selected)?1f:0.45f);}}
    private PanelConfig collect(){
        PanelConfig c=new PanelConfig();c.deviceId=store.deviceId();c.revision=loadedRevision;c.dashboard=dashboard.getText().toString();c.haUrl=ha.getText().toString();c.broker=broker.getText().toString().trim();c.mqttBase=mqttBase.getText().toString().trim();
        try{c.port=Integer.parseInt(port.getText().toString());}catch(NumberFormatException e){throw new IllegalArgumentException("Enter a numeric broker port");}
        c.username=user.getText().toString();c.password=password.getText().toString();c.tls=tls.isChecked();c.lock=lock.isChecked();c.displayMode=displayMode.getSelectedItemPosition()==1?"app":"web";c.appPackage=externalPackage.getText().toString();c.profile=profile.getSelectedItemPosition()==1?"g10":profile.getSelectedItemPosition()==2?"a133":"generic";
        for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(modules.get(m.id).isChecked()&&m.supported(c.profile))c.selected.add(m.id);return c;
    }
    private boolean save(boolean open){
        draftHandler.removeCallbacks(draftWriter);
        try {
            if(store.locked()&&!unlocked)throw new IllegalStateException("Administrator authentication required");
            PanelConfig c=collect();c.validate();
            if(store.runtimeEnabled()&&!BridgeService.sameConnection(store.load(),c))throw new IllegalArgumentException("Stop hardware before changing MQTT connection or profile");
            if(open){
                if(!c.selected.contains("dashboard"))throw new IllegalArgumentException("Select the Dashboard / app module first");
                if("app".equals(c.displayMode)){
                    AppModePolicy.packageName(c.appPackage);
                    if(getPackageManager().getLaunchIntentForPackage(c.appPackage)==null)throw new IllegalArgumentException("Selected app is unavailable");
                    if(!android.provider.Settings.canDrawOverlays(this))throw new IllegalArgumentException("Allow the administrator hotspot permission first");
                }else PanelConfig.webUrl(c.dashboard,false);
            }
            store.save(c,pin.getText().toString());loadedRevision=c.revision;formReady=false;pin.setText("");committedForm=snapshot();dirty=false;lastDraft="";draftHandler.removeCallbacks(draftWriter);formReady=true;unlocked=true;status.setText("Settings saved successfully.");Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
            if(store.runtimeEnabled())BridgeService.ensureStarted(this);
            SipRuntime.reconcile(this);
            ReceiverRuntime.reconcile(this);
            if(open)startActivity(LauncherSupport.dashboardIntent(this));
            return true;
        }catch(IllegalArgumentException e){persistDraft();status.setText("Not saved: "+e.getMessage());}
        catch(IllegalStateException e){status.setText(e.getMessage());}
        catch(Exception e){status.setText("Settings could not be saved ("+e.getClass().getSimpleName()+").");}
        formReady=true;
        return false;
    }
    private void probe(){
        final PanelConfig c;try{c=collect();c.validateBroker();}catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        test.setEnabled(false);status.setText("Connecting to broker…");
        new Thread(new Runnable(){public void run(){String result;try{result=MqttProbe.test(c,NetworkRouter.get(MainActivity.this).haSockets());}catch(Exception e){result="Connection failed ("+e.getClass().getSimpleName()+"). Check host, port, credentials and TLS.";}final String message=result;runOnUiThread(new Runnable(){public void run(){if(!isFinishing()){status.setText(message);test.setEnabled(true);}}});}},"wallpanel-mqtt-test").start();
    }
    @Override protected void onStop(){draftHandler.removeCallbacks(draftWriter);draftHandler.removeCallbacks(runtimeRefresh);persistDraft();super.onStop();if(store!=null&&store.locked()){unlocked=false;finish();}}
    private void refreshHomeStatus(){if(homeStatus!=null)homeStatus.setText(LauncherSupport.isDefaultHome(this)?"Home app: Tuya HA Wall Panel":"Home app: another launcher is selected");}
    @Override protected void onResume(){super.onResume();if(formReady&&(!store.locked()||unlocked))SystemUiGuard.administratorAuthenticated();refreshHomeStatus();draftHandler.removeCallbacks(runtimeRefresh);draftHandler.post(runtimeRefresh);}
    @Override protected void onDestroy(){draftHandler.removeCallbacksAndMessages(null);super.onDestroy();}
    private String snapshot()throws Exception {
        JSONObject d=new JSONObject();d.put("v",3);d.put("display_mode",displayMode.getSelectedItemPosition());d.put("app_package",externalPackage.getText().toString());d.put("dashboard",dashboard.getText().toString());d.put("ha_url",ha.getText().toString());d.put("mqtt_base",mqttBase.getText().toString());
        d.put("broker",broker.getText().toString());d.put("port",port.getText().toString());d.put("username",user.getText().toString());d.put("password",password.getText().toString());
        d.put("tls",tls.isChecked());d.put("lock",lock.isChecked());d.put("profile",profile.getSelectedItemPosition());
        JSONArray selected=new JSONArray();for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(modules.get(m.id).isChecked())selected.put(m.id);d.put("modules",selected);return d.toString();
    }
    private void restoreDraft(String json)throws Exception {
        JSONObject d=new JSONObject(json);if(d.getInt("v")!=1&&d.getInt("v")!=2&&d.getInt("v")!=3)throw new IllegalArgumentException("Unknown draft version");
        // Parse every field before touching the form, so malformed drafts cannot half-restore it.
        String a=d.getString("dashboard"),b=d.getString("ha_url"),c=d.getString("broker"),p=d.getString("port"),u=d.getString("username"),secret=d.getString("password");
        boolean secure=d.getBoolean("tls"),locked=d.getBoolean("lock");int hardware=d.getInt("profile");if(hardware<0||hardware>2)throw new IllegalArgumentException("Invalid draft profile");
        JSONArray selected=d.getJSONArray("modules");java.util.HashSet<String> ids=new java.util.HashSet<String>();
        for(int i=0;i<selected.length();i++){String id=selected.getString(i);if(ModuleCatalog.find(id)==null)throw new IllegalArgumentException("Invalid draft module");ids.add(id);}
        int mode=d.optInt("display_mode",0);if(mode<0||mode>1)throw new IllegalArgumentException("Invalid display mode");String target=d.optString("app_package","");
        displayMode.setSelection(mode);externalPackage.setText(target);dashboard.setText(a);ha.setText(b);broker.setText(c);port.setText(p);mqttBase.setText(d.optString("mqtt_base",mqttBase.getText().toString()));user.setText(u);password.setText(secret);tls.setChecked(secure);lock.setChecked(locked);profile.setSelection(hardware);
        for(ModuleCatalog.Module m:ModuleCatalog.ALL)modules.get(m.id).setChecked(ids.contains(m.id));refreshModules();
    }
    private void watchFields(){
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){changed();}public void afterTextChanged(Editable e){}};
        for(EditText e:new EditText[]{dashboard,ha,broker,port,user,password,pin,mqttBase})e.addTextChangedListener(watcher);
        CompoundButton.OnCheckedChangeListener toggled=new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton b,boolean value){changed();}};
        tls.setOnCheckedChangeListener(toggled);lock.setOnCheckedChangeListener(toggled);for(CheckBox box:modules.values())box.setOnCheckedChangeListener(toggled);
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int position,long id){refreshModules();changed();}});
    }
    private void changed(){
        if(!formReady)return;
        try{
            dirty=!snapshot().equals(committedForm)||pin.getText().length()>0;draftHandler.removeCallbacks(draftWriter);
            if(dirty){status.setText("Unsaved changes. Keeping a protected draft…");draftHandler.postDelayed(draftWriter,750);}
            else{if(lastDraft.length()>0)store.clearDraft();lastDraft="";status.setText("Saved setup loaded.");}
        }catch(Exception e){status.setText("Changes are not saved. Please use Save settings.");}
    }
    private void persistDraft(){
        if(!formReady||!dirty)return;
        try{String json=snapshot();if(!json.equals(lastDraft)){store.saveDraft(json,loadedRevision);lastDraft=json;}
            status.setText(pin.getText().length()>0?"Draft kept; the new PIN requires Save settings.":"Draft kept locally. Tap Save settings to apply it.");
        }catch(Exception e){status.setText("Draft could not be protected. Use Save settings before leaving.");}
    }
}
