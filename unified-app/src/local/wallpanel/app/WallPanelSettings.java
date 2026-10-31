package local.wallpanel.app;
import android.content.*;
import android.os.*;
import com.wallpanel.audioreceiver.SettingsHub;
import org.json.*;
import java.util.*;

/** Product fields backed by the original validated/encrypted stores, not a second configuration. */
final class WallPanelSettings implements SettingsHub.Product {
 private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
 WallPanelSettings(Context value){context=value.getApplicationContext();}
 public boolean locked(){return new ConfigStore(context).locked();}
 public boolean ownsAudio(){return new ConfigStore(context).dashboardSettings().selected.contains("audio")||context.getSharedPreferences("wpar_prefs",Context.MODE_PRIVATE).getBoolean("enabled",false);}
 public boolean authorize(String pin)throws Exception{return new ConfigStore(context).verify(pin);}
 private JSONObject revision(JSONObject section,long revision)throws Exception{return section.put("revision",revision);}
 public JSONArray sections()throws Exception{
  ConfigStore store=new ConfigStore(context);PanelConfig c=store.load();JSONArray sections=new JSONArray();
  JSONArray apps=new JSONArray().put(new JSONObject().put("value","").put("label","Choose an installed app"));
  android.content.pm.PackageManager pm=context.getPackageManager();for(android.content.pm.ResolveInfo app:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){String pkg=app.activityInfo.packageName;if(!pkg.equals(context.getPackageName()))apps.put(new JSONObject().put("value",pkg).put("label",app.loadLabel(pm).toString()));}
  sections.put(revision(SettingsHub.section("dashboard","Dashboard and kiosk","Web dashboard or installed app, with the same administrator lock. Changes use the existing setup store.",new JSONArray()
   .put(SettingsHub.field("module_dashboard","Enable dashboard / app","boolean",c.selected.contains("dashboard")))
   .put(SettingsHub.select("display_mode","Display mode",c.displayMode,"web","Web dashboard","app","Installed app"))
   .put(SettingsHub.field("dashboard","Dashboard URL","text",c.dashboard))
   .put(SettingsHub.field("app_package","Installed app","select",c.appPackage).put("options",apps))
   .put(SettingsHub.field("lock","Lock dashboard navigation","boolean",c.lock))
   .put(SettingsHub.secret("new_pin","New administrator PIN (6–12 digits)",store.hasPin()?"set":""))),c.revision));
  sections.put(revision(SettingsHub.section("home_assistant","Home Assistant and MQTT","HA is optional for audio and intercom. Existing passwords are kept when left blank.",new JSONArray()
   .put(SettingsHub.field("ha_url","Home Assistant URL","text",c.haUrl))
   .put(SettingsHub.field("broker","MQTT broker hostname or IP","text",c.broker))
   .put(SettingsHub.number("port","MQTT port",c.port,1,65535))
   .put(SettingsHub.field("tls","MQTT TLS","boolean",c.tls))
   .put(SettingsHub.field("mqtt_base","Panel MQTT base topic","text",c.mqttBase))
   .put(SettingsHub.field("username","MQTT username","text",c.username))
   .put(SettingsHub.secret("password","MQTT password",c.password))),c.revision));
  NetworkPolicy network=NetworkRouter.get(context).load();
  sections.put(SettingsHub.section("panel_networks","Panel network assignments","HA/dashboard and SIP/video can use different networks. The shared Audio network setting follows HA when set to Automatic.",new JSONArray()
   .put(SettingsHub.select("ha","HA and dashboard network",network.ha,"auto","Android default","ethernet","Ethernet","wifi","Wi-Fi"))
   .put(SettingsHub.field("ha_fallback","Allow HA network fallback","boolean",network.haFallback))
   .put(SettingsHub.select("intercom","SIP and video network",network.intercom,"auto","Android default","ethernet","Ethernet","wifi","Wi-Fi"))
   .put(SettingsHub.field("intercom_fallback","Allow intercom network fallback","boolean",network.intercomFallback))));
  JSONArray hardware=new JSONArray().put(SettingsHub.select("profile","Hardware profile",c.profile,"generic","Generic / unverified","g10","G10 / PX30","a133","A133 / Android 11"))
   .put(SettingsHub.field("runtime_enabled","Run hardware service at startup","boolean",store.runtimeEnabled()));
  for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(!m.id.equals("sip")&&!m.id.equals("dashboard"))hardware.put(SettingsHub.field("module_"+m.id,m.label,"boolean",c.selected.contains(m.id)));
  sections.put(revision(SettingsHub.section("hardware","Hardware modules","Enable only fitted hardware. Unsupported layouts remain protected by the native adapter. SIP is configured separately below. Knob pages and HA action mappings remain in the HA integration.",hardware),c.revision));
  if(c.selected.contains("audio")){SharedPreferences route=context.getSharedPreferences("audio_routes",Context.MODE_PRIVATE);sections.put(SettingsHub.section("audio_routes","Audio input and speakers","Saved board routing, shared with HA. Saving a different input also switches current audio. Streaming can temporarily use digital playback without overwriting the startup choice.",new JSONArray()
   .put(SettingsHub.select("source","Source on startup",route.getBoolean("line_input",false)?"Line Input":"Android playback","Android playback","Local Playback / digital","Line Input","Line Input").put("disabled",!BridgeService.hardwareRunning()))
   .put(SettingsHub.field("external_speakers","External speakers","boolean",route.getBoolean("external_speakers",false)).put("disabled",!BridgeService.hardwareRunning()))));}
  JSONObject display=BridgeService.displayOptions();if(c.selected.contains("display"))sections.put(SettingsHub.section("display","Main display","Zero disables an idle timer. Dimming must happen before display-off. Values are the same as HA's display controls.",new JSONArray()
   .put(SettingsHub.slider("brightness","Brightness (1–255)",display.optInt("brightness",120),1,255).put("disabled",!BridgeService.hardwareRunning()))
   .put(SettingsHub.number("dim_after","Dim after (seconds)",display.optInt("dim_after",60),0,86400).put("disabled",!BridgeService.hardwareRunning()))
   .put(SettingsHub.number("off_after","Turn display off after (seconds)",display.optInt("off_after",300),0,86400).put("disabled",!BridgeService.hardwareRunning()))
   .put(SettingsHub.slider("dim_brightness","Dimmed brightness (1–255)",display.optInt("dim_brightness",15),1,255).put("disabled",!BridgeService.hardwareRunning()))));
  if(c.selected.contains("knob_display")){
   JSONArray knob=new JSONArray();for(String field:new String[]{"follow_main","activity","ring"})knob.put(SettingsHub.field(field,field.equals("follow_main")?"Follow main display":field.equals("activity")?"Wake knob display for 15 seconds on use":"Show accent ring","boolean",BridgeService.knobOption("/knob/display/"+field+"/state","OFF").equals("ON")).put("disabled",!BridgeService.hardwareRunning()));
   knob.put(SettingsHub.select("color","Accent color",BridgeService.knobOption("/knob/display/color/state","Cyan"),"Cyan","Cyan","White","White","Red","Red","Green","Green","Blue","Blue","Amber","Amber","Purple","Purple").put("disabled",!BridgeService.hardwareRunning()));
   sections.put(SettingsHub.section("knob","Knob display","Uses the same saved hardware controls as HA. Configure weather, page entities and action mappings in the HA integration.",knob));
  }
  SipStore sip=new SipStore(context);JSONObject data=SipStore.encode(sip.load());JSONArray fields=new JSONArray().put(SettingsHub.field("enabled","Enable SIP intercom","boolean",sip.enabled()));
  String[][] labels={{"server","SIP server"},{"server_port","SIP server port"},{"user","Extension"},{"auth_user","Authentication username"},{"password","SIP password"},{"local_address","Local address override (usually blank)"},{"sip_port","Local SIP port"},{"rtp_port","Local RTP port (even)"},{"auto_answer","Auto-answer"},{"ring_seconds","Ring timeout (seconds)"},{"ringtone_enabled","Incoming ringtone"},{"ring_volume","Ringtone volume (%)"},{"volume","Call volume (%)"},{"aec","Echo cancellation"},{"noise_suppression","Noise suppression"},{"gain_control","Automatic gain control"},{"video_enabled","Video preview"},{"video_url","RTSP video URL"},{"station_brand","Door station brand"},{"door_server","Door station URL (http://IP or https://IP)"},{"door_user","Door station username"},{"door_password","Door station password"},{"door_one_enabled","Door 1 enabled"},{"door_two_enabled","Door 2 enabled"}};
  for(String[] entry:labels){String key=entry[0],label=entry[1];Object value=data.get(key);JSONObject field;
   if(key.equals("password")||key.equals("door_password"))field=SettingsHub.secret(key,label,(String)value);
   else if(key.equals("video_url")){fields.put(SettingsHub.field(key,"RTSP video URL (address and path)","text",CameraAddress.visible((String)value)));fields.put(SettingsHub.field("video_user","Camera username","text",CameraAddress.user((String)value)));fields.put(SettingsHub.secret("video_password","Camera password",CameraAddress.password((String)value)));continue;}
   else if(key.equals("station_brand"))field=SettingsHub.select(key,label,(String)value,"generic","Generic SIP","hikvision","Hikvision");
   else if(value instanceof Boolean)field=SettingsHub.field(key,label,"boolean",value);
   else if(value instanceof Number){int min=key.equals("ring_seconds")?5:key.equals("server_port")?1:1024,max=key.equals("ring_seconds")?180:65535;if(key.equals("volume")||key.equals("ring_volume"))field=SettingsHub.slider(key,label,((Number)value).intValue(),0,100);else field=SettingsHub.number(key,label,((Number)value).intValue(),min,max);}
   else field=SettingsHub.field(key,label,"text",value);fields.put(field);
  }
  sections.put(SettingsHub.section("intercom","SIP, video and door access","Intercom works without HA. Network assignment is under Panel network assignments. Saved credentials are not exposed to LAN browsers; blank keeps them unchanged.",fields));
  return sections;
 }
 private static String secret(JSONObject values,String key,String previous)throws Exception{if(!values.has(key))return previous;if(values.isNull(key))return "";String next=values.getString(key);return next.isEmpty()?previous:next;}
 public void save(String section,JSONObject v,JSONObject request)throws Exception{
  if(section.equals("audio_routes")){if(v.has("source"))BridgeService.webAudio(new JSONObject().put("command","source").put("value",v.getString("source")));if(v.has("external_speakers"))BridgeService.webAudio(new JSONObject().put("command","external_speakers").put("value",v.getBoolean("external_speakers")));return;}
  if(section.equals("display")){JSONObject old=BridgeService.displayOptions();int dim=v.optInt("dim_after",old.optInt("dim_after",60)),off=v.optInt("off_after",old.optInt("off_after",300));if(dim>0&&off>0&&dim>=off)throw new IllegalArgumentException("Dim timer must be shorter than display-off timer");if(v.has("dim_after")||v.has("off_after")){BridgeService.webSetting("/display/policy/dim_after/set","0");BridgeService.webSetting("/display/policy/off_after/set",Integer.toString(off));BridgeService.webSetting("/display/policy/dim_after/set",Integer.toString(dim));}if(v.has("brightness"))BridgeService.webSetting("/device/brightness/set",Integer.toString(v.getInt("brightness")));if(v.has("dim_brightness"))BridgeService.webSetting("/display/policy/dim_brightness/set",Integer.toString(v.getInt("dim_brightness")));return;}
  if(section.equals("knob")){Iterator<String> keys=v.keys();while(keys.hasNext()){String key=keys.next();String suffix=key.equals("led")?"/knob/led/color/set":"/knob/display/"+key+"/set";Object value=v.get(key);BridgeService.webSetting(suffix,value instanceof Boolean?((Boolean)value?"ON":"OFF"):value.toString());}return;}
  if(section.equals("panel_networks")){NetworkRouter router=NetworkRouter.get(context);NetworkPolicy p=router.load();p.ha=v.optString("ha",p.ha);p.intercom=v.optString("intercom",p.intercom);p.haFallback=v.optBoolean("ha_fallback",p.haFallback);p.intercomFallback=v.optBoolean("intercom_fallback",p.intercomFallback);router.save(p);return;}
  if(section.equals("intercom")){SipStore store=new SipStore(context);JSONObject next=SipStore.encode(store.load());String video=next.getString("video_url");Iterator<String> keys=v.keys();while(keys.hasNext()){String key=keys.next();if(key.equals("enabled")||key.equals("video_url")||key.equals("video_user")||key.equals("video_password"))continue;if(key.equals("password")||key.equals("door_password"))next.put(key,secret(v,key,next.getString(key)));else next.put(key,v.get(key));}
   if(v.has("video_url")||v.has("video_user")||v.has("video_password")){String url=v.optString("video_url",CameraAddress.visible(video));boolean inline=!url.isEmpty()&&new java.net.URI(url).getUserInfo()!=null;if(inline&&(v.has("video_user")||v.has("video_password")))throw new IllegalArgumentException("Use separate camera login fields or credentials inside the URL");next.put("video_url",inline?url:CameraAddress.withLogin(url,v.optString("video_user",CameraAddress.user(video)),secret(v,"video_password",CameraAddress.password(video))));}
   SipConfig config=SipStore.decode(next);store.save(config);if(v.has("enabled"))store.enabled(v.getBoolean("enabled"));main.post(()->SipRuntime.reconcile(context));return;
  }
  ConfigStore store=new ConfigStore(context);PanelConfig c=store.load();if(request.getLong("revision")!=c.revision)throw new IllegalStateException("Settings changed elsewhere. Reload this section before saving.");String pin="";
  if(section.equals("dashboard")){c.dashboard=v.optString("dashboard",c.dashboard);c.displayMode=v.optString("display_mode",c.displayMode);c.appPackage=v.optString("app_package",c.appPackage);c.lock=v.optBoolean("lock",c.lock);if(v.has("module_dashboard")){if(v.getBoolean("module_dashboard"))c.selected.add("dashboard");else c.selected.remove("dashboard");}pin=v.isNull("new_pin")?"":v.optString("new_pin","");if(c.displayMode.equals("app")&&context.getPackageManager().getLaunchIntentForPackage(c.appPackage)==null)throw new IllegalArgumentException("The selected app is unavailable");}
  else if(section.equals("home_assistant")){c.haUrl=v.optString("ha_url",c.haUrl);c.broker=v.optString("broker",c.broker);c.port=v.optInt("port",c.port);c.tls=v.optBoolean("tls",c.tls);c.mqttBase=v.optString("mqtt_base",c.mqttBase);c.username=v.optString("username",c.username);c.password=secret(v,"password",c.password);}
  else if(section.equals("hardware")){c.profile=v.optString("profile",c.profile);for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(!m.supported(c.profile))c.selected.remove(m.id);for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(v.has("module_"+m.id)){if(v.getBoolean("module_"+m.id)){if(!m.supported(c.profile))throw new IllegalArgumentException("Hardware profile does not support "+m.label);c.selected.add(m.id);}else c.selected.remove(m.id);}}
  else throw new IllegalArgumentException("Unknown panel section");
  if(section.equals("hardware")&&v.optBoolean("runtime_enabled",false))c.validateBroker();
  store.save(c,pin);if(section.equals("hardware")&&v.has("runtime_enabled"))store.setRuntimeEnabled(v.getBoolean("runtime_enabled"));
  main.post(()->{if(store.runtimeEnabled())BridgeService.ensureStarted(context);else context.stopService(new Intent(context,BridgeService.class));SipRuntime.reconcile(context);ReceiverRuntime.reconcile(context);ExternalAppService.reconcile(context);});
 }
 public String action(String action)throws Exception{
  if(action.equals("android_settings")){context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "Android settings opened on the panel";}
  if(action.equals("dashboard")){main.post(()->context.startActivity(LauncherSupport.dashboardIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));return "Opening the dashboard";}
  if(action.equals("installation")){main.post(()->context.startActivity(new Intent(context,PanelSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));return "Installation and repair opened on the panel";}
  if(action.equals("home_app")){context.startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "Choose SmartWay Panel as Home on the panel";}
  if(action.equals("microphone")){context.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "Open Permissions on the panel to grant microphone access";}
  if(action.equals("restart_app")){AppRestartReceiver.request(context,()->{});return "App restart scheduled";}
  if(action.equals("hardware_status"))return new ConfigStore(context).runtimeStatus();
  throw new IllegalArgumentException("Unknown panel action");
 }
}
