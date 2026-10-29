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
    private EditText name,trim,airplayPort,webPort,webToken;
    private com.wallpanel.multiroom.TimingSlider offset;
    private Spinner iface;
    private final java.util.ArrayList<String> interfaceIds=new java.util.ArrayList<String>();
    private CheckBox enabled,webEnabled;
    private TextView webAddress;
    private TextView status;
    private String message="";
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){status.setText((message.isEmpty()?"":message+"\n")+ReceiverStatusText.format(ReceiverState.INSTANCE));PrefStore saved=new PrefStore(ReceiverSettingsActivity.this);offset.refresh(saved.getLatencyOffsetMs());String ip=ReceiverState.INSTANCE.getLocalIp();webAddress.setText(saved.isHaEnabled()?"Web page: http://"+(ip==null?"PANEL_IP":ip)+":"+saved.getHaPort()+"/":"Receiver web page is off");handler.postDelayed(this,1000);}};
    private void text(String value){TextView t=new TextView(this);t.setText(value);t.setPadding(0,12,0,6);form.addView(t);}
    private EditText field(String label,String value,boolean number){text(label);EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);form.addView(e);return e;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);final PrefStore prefs=new PrefStore(this);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);SetupKeyboard.prepare(this,page);
        LinearLayout navigation=new LinearLayout(this);page.addView(navigation);
        Button back=new Button(this);back.setText("Back to setup");navigation.addView(back,new LinearLayout.LayoutParams(0,-2,1));back.setOnClickListener(v->{SetupKeyboard.hide(this);finish();});
        Button keyboard=new Button(this);keyboard.setText("Hide keyboard");navigation.addView(keyboard,new LinearLayout.LayoutParams(0,-2,1));keyboard.setOnClickListener(v->SetupKeyboard.hide(this));
        ScrollView scroll=new ScrollView(this);form=new LinearLayout(this);form.setOrientation(1);form.setPadding(24,20,24,24);scroll.addView(form);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        text("Audio receiver");text("Receive music through AirPlay. Incoming intercom calls temporarily silence receiver audio.");
        enabled=new CheckBox(this);enabled.setText("Enable AirPlay receiver");enabled.setChecked(prefs.isEnabled());form.addView(enabled);
        name=field("Receiver name",prefs.getReceiverName(),false);
        offset=new com.wallpanel.multiroom.TimingSlider(this,"AirPlay timing (-500 to +500 ms)",prefs.getLatencyOffsetMs(),ms->prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,ms));form.addView(offset);
        text("Timing saves and applies when you release the slider. Other settings use the Save button below.");
        trim=field("Receiver trim (0 to 100%)",""+prefs.getVolumeTrim(),true);
        text("Network interface");iface=new Spinner(this);form.addView(iface);loadInterfaces(prefs.getInterfaceName());
        Button networks=new Button(this);networks.setText("Refresh network interfaces");form.addView(networks);networks.setOnClickListener(v->loadInterfaces(interfaceIds.get(iface.getSelectedItemPosition())));
        text("Automatic follows HA's selected network. A specific interface applies only to the receiver. Saving a different receiver name, network or enabled state restarts the receiver.");
        text("Advanced");airplayPort=field("AirPlay listening port (default 7000)",""+prefs.getRtspPort(),true);
        text("Receiver web page");webEnabled=new CheckBox(this);webEnabled.setText("Enable receiver web page");webEnabled.setChecked(prefs.isHaEnabled());form.addView(webEnabled);
        webPort=field("Web page port (default 8099)",""+prefs.getHaPort(),true);
        webToken=field("Web access token (optional)",prefs.getHaToken(),false);webToken.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        text("This page provides receiver status, playback controls and live timing adjustments. It is separate from the HA integration. Save changes before opening it; changing the web page switch or port restarts the receiver.");
        webAddress=new TextView(this);webAddress.setTextIsSelectable(true);form.addView(webAddress);
        Button openWeb=new Button(this);openWeb.setText("Open receiver web page");form.addView(openWeb);openWeb.setOnClickListener(v->openWebPage());
        Button save=new Button(this);save.setText("Save receiver settings");form.addView(save);
        Button ptp=new Button(this);ptp.setText("Check PTP Helper");form.addView(ptp);
        ptp.setOnClickListener(v->{SetupKeyboard.hide(this);ptp.setEnabled(false);new Thread(()->{boolean available=com.wallpanel.audioreceiver.PrivilegeChannel.available();handler.post(()->{if(!isFinishing()){ptp.setEnabled(true);ptp.setText(available?"PTP helper available":"PTP helper unavailable");}});},"receiver-ptp-check").start();});
        Button logs=new Button(this);logs.setText("View Logs");form.addView(logs);logs.setOnClickListener(v->showLogs());
        TextView heading=new TextView(this);heading.setText("Status");heading.setTextSize(18);heading.setTypeface(null,android.graphics.Typeface.BOLD);heading.setPadding(0,24,0,8);form.addView(heading);
        status=new TextView(this);status.setTextSize(12);status.setTypeface(android.graphics.Typeface.MONOSPACE);status.setBackgroundColor(0xfff0f0f0);status.setTextColor(0xff1b1b1b);status.setPadding(8,8,8,8);form.addView(status);
        save.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            try{
                SetupKeyboard.hide(ReceiverSettingsActivity.this);
                String label=name.getText().toString().trim(),network=interfaceIds.get(iface.getSelectedItemPosition());
                int timing=offset.value(),level=Integer.parseInt(trim.getText().toString());
                int rtsp=Integer.parseInt(airplayPort.getText().toString()),http=Integer.parseInt(webPort.getText().toString());String token=webToken.getText().toString();
                if(rtsp<1024||rtsp>65535||http<1024||http>65535||rtsp==http)throw new IllegalArgumentException("Use different AirPlay and web ports between 1024 and 65535");
                if(!token.matches("[!-~]{0,256}"))throw new IllegalArgumentException("Use a web token without spaces or line breaks");
                if(label.length()==0||label.length()>63||label.indexOf('\n')>=0||label.indexOf('\r')>=0)throw new IllegalArgumentException("Use a receiver name of 1 to 63 characters");
                if(timing< -500||timing>500||level<0||level>100)throw new IllegalArgumentException("Check timing and trim limits");
                if(!network.matches("[A-Za-z0-9_.-]{0,32}"))throw new IllegalArgumentException("Invalid interface name");
                boolean restart=prefs.isEnabled()!=enabled.isChecked()||!prefs.getReceiverName().equals(label)||!prefs.getInterfaceName().equals(network)||prefs.getRtspPort()!=rtsp;
                boolean webRestart=prefs.isHaEnabled()!=webEnabled.isChecked()||prefs.getHaPort()!=http;
                if(enabled.isChecked()&&android.os.Build.VERSION.SDK_INT<27)throw new IllegalArgumentException("The receiver requires Android 8.1 or newer");
                if(enabled.isChecked()&&!com.wallpanel.audioreceiver.PrivilegeChannel.available())throw new IllegalArgumentException("AirPlay helper is not prepared. Run Prepare / repair panel first.");
                prefs.put(PrefStore.KEY_RECEIVER_NAME,label);prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,timing);prefs.put(PrefStore.KEY_VOLUME_TRIM,level);prefs.put(PrefStore.KEY_INTERFACE_NAME,network);
                prefs.put(PrefStore.KEY_HA_ENABLED,webEnabled.isChecked());prefs.put(PrefStore.KEY_HA_PORT,http);prefs.put(PrefStore.KEY_HA_TOKEN,token);prefs.put(PrefStore.KEY_RTSP_PORT,rtsp);prefs.put(PrefStore.KEY_AUTOSTART,true);prefs.put(PrefStore.KEY_ENABLED,enabled.isChecked());
                ReceiverState.INSTANCE.setVolumeTrim(level);
                if(webRestart)ReceiverRuntime.restartForWebSettings(ReceiverSettingsActivity.this);else if(restart)ReceiverRuntime.apply(ReceiverSettingsActivity.this);else ReceiverRuntime.reconcile(ReceiverSettingsActivity.this);
                Toast.makeText(ReceiverSettingsActivity.this,"Receiver settings saved",Toast.LENGTH_SHORT).show();
                message="";
            }catch(Exception failed){message=failed.getMessage();status.setText(message);}
        }});
    }
    private void loadInterfaces(String selected){
        java.util.TreeMap<String,String> found=new java.util.TreeMap<String,String>();
        try{
            java.util.Enumeration<java.net.NetworkInterface> all=java.net.NetworkInterface.getNetworkInterfaces();
            while(all!=null&&all.hasMoreElements()){
                java.net.NetworkInterface network=all.nextElement();String id=network.getName();
                if(network.isLoopback()||id.equals("sit0")||id.equals("ip6tnl0")||!id.matches("[A-Za-z0-9_.-]{1,32}"))continue;
                String label=(id.startsWith("wlan")?"Wi-Fi":id.startsWith("eth")||id.startsWith("en")?"Ethernet":"Network")+" ("+id+")";
                String address="";java.util.Enumeration<java.net.InetAddress> addresses=network.getInetAddresses();
                while(addresses.hasMoreElements()){java.net.InetAddress value=addresses.nextElement();if(value instanceof java.net.Inet4Address&&!value.isLinkLocalAddress()){address=value.getHostAddress();break;}}
                found.put(id,label+(address.isEmpty()?" - no IPv4 address":" - "+address));
            }
        }catch(Exception ignored){}
        if(!selected.isEmpty()&&!found.containsKey(selected))found.put(selected,"Saved interface ("+selected+") - unavailable");
        interfaceIds.clear();interfaceIds.add("");java.util.ArrayList<String> labels=new java.util.ArrayList<String>();labels.add("Automatic - follow HA network");
        for(java.util.Map.Entry<String,String> item:found.entrySet()){interfaceIds.add(item.getKey());labels.add(item.getValue());}
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);iface.setAdapter(adapter);iface.setSelection(Math.max(0,interfaceIds.indexOf(selected)));
    }
    private void showLogs(){
        SetupKeyboard.hide(this);TextView content=new TextView(this);content.setTextIsSelectable(true);content.setTypeface(android.graphics.Typeface.MONOSPACE);content.setTextSize(12);content.setPadding(16,12,16,12);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        try{content.setText(ReceiverLogs.tail(new java.io.File(getFilesDir(),"app.log")));}catch(java.io.IOException failed){content.setText("Cannot read receiver logs.");}
        new android.app.AlertDialog.Builder(this).setTitle("Recent Logs").setView(scroll).setPositiveButton("OK",null).show();
    }
    private void openWebPage(){
        PrefStore saved=new PrefStore(this);
        if(!saved.isEnabled()||!saved.isHaEnabled()){Toast.makeText(this,"Enable the receiver and its web page, then save settings first",Toast.LENGTH_LONG).show();return;}
        SetupKeyboard.hide(this);final android.webkit.WebView web=new android.webkit.WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new android.webkit.WebViewClient());
        String url="http://127.0.0.1:"+saved.getHaPort()+"/";
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Receiver web page").setView(web).setPositiveButton("Close",null).setNeutralButton("Reload",null).create();
        dialog.setOnShowListener(v->{dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),(int)(getResources().getDisplayMetrics().heightPixels*.94));dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(button->web.reload());});
        dialog.setOnDismissListener(v->{web.stopLoading();web.destroy();});dialog.show();web.loadUrl(url);
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
}
