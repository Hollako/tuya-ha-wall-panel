package com.wallpanel.audioreceiver;
import android.content.*;
import android.net.*;
import android.os.*;
import android.bluetooth.BluetoothAdapter;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** One typed settings contract used by native setup and LAN browsers. */
public final class SettingsHub {
 public interface Product {
  JSONArray sections()throws Exception;
  default boolean ownsAudio(){return true;}
  boolean locked();boolean authorize(String pin)throws Exception;
  void save(String section,JSONObject values,JSONObject request)throws Exception;
  String action(String action)throws Exception;
 }
 private static Product product;
 private static final String localKey=UUID.randomUUID().toString()+UUID.randomUUID();
 public static void product(Product value){product=value;}
 public static boolean ownsBluetoothName(){return product==null||product.ownsAudio();}
 public static String localKey(){return localKey;}
 public static JSONObject section(String id,String title,String help,JSONArray fields)throws JSONException{return new JSONObject().put("id",id).put("title",title).put("help",help).put("fields",fields);}
 public static JSONObject field(String key,String title,String type,Object value)throws JSONException{return new JSONObject().put("key",key).put("label",title).put("type",type).put("value",value);}
 public static JSONObject number(String key,String title,int value,int min,int max)throws JSONException{return field(key,title,"number",value).put("min",min).put("max",max);}
 public static JSONObject slider(String key,String title,int value,int min,int max)throws JSONException{return number(key,title,value,min,max).put("type","range");}
 public static JSONObject secret(String key,String title,String value)throws JSONException{return field(key,title,"secret","").put("configured",!value.isEmpty());}
 public static JSONObject select(String key,String title,String value,String... options)throws JSONException{
  JSONArray choices=new JSONArray();for(int i=0;i<options.length;i+=2)choices.put(new JSONObject().put("value",options[i]).put("label",options[i+1]));return field(key,title,"select",value).put("options",choices);
 }
 public static JSONArray interfaces()throws Exception{
  JSONArray choices=new JSONArray().put(new JSONObject().put("value","").put("label","Automatic — follow panel network"));
  Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all!=null&&all.hasMoreElements()){NetworkInterface link=all.nextElement();if(link.isLoopback()||!link.isUp()||link.getName().equals("sit0"))continue;String ip="";for(InetAddress address:Collections.list(link.getInetAddresses()))if(address instanceof Inet4Address)ip=address.getHostAddress();if(ip.isEmpty())continue;choices.put(new JSONObject().put("value",link.getName()).put("label",link.getName()+" — "+ip));}return choices;
 }
 public static JSONObject snapshot(Context context,PrefStore p)throws Exception{
  JSONArray sections=new JSONArray();
  sections.put(section("audio_network","Audio identity and network","Shared by AirPlay, Bluetooth naming, room discovery and grouping. Bluetooth pairing itself does not use Ethernet/Wi-Fi.",new JSONArray()
   .put(field("receiver_name","Room name","text",p.getReceiverName()).put("maxLength",63))
   .put(field("network_interface","Audio network","select",p.getInterfaceName()).put("options",interfaces()))));
  sections.put(section("airplay","AirPlay receiver","Playback starts when a sender connects. Timing and input trim are separate from the shared room volume.",new JSONArray()
   .put(field("enabled","Enable AirPlay receiver","boolean",p.isEnabled()))
   .put(slider("latency_offset_ms","AirPlay timing (ms)",p.getLatencyOffsetMs(),-500,500))
   .put(slider("volume_trim","Advanced: AirPlay input trim (%)",p.getVolumeTrim(),0,100))
   .put(number("rtsp_port","Advanced: AirPlay port",p.getRtspPort(),1024,65535))));
  android.content.SharedPreferences group=context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE);
  sections.put(section("grouping","Grouping","Discovery and controls stay available after boot. Capture/streaming runs only when playback is requested. Stopping a group does not restart it automatically.",new JSONArray()
   .put(number("buffer_ms","Advanced: leader buffer (ms)",group.getInt("buffer_ms",2000),1000,5000))
   .put(slider("room_delay_ms","Room timing (ms)",group.getInt("room_delay_ms",0),-500,500))));
  BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
  sections.put(section("bluetooth","Bluetooth","Uses the shared Room name above. Pairing and Android permission prompts must be confirmed on the panel.",new JSONArray().put(field("enabled","Bluetooth enabled","boolean",bt!=null&&bt.isEnabled()).put("disabled",bt==null))
   .put(field("bt_handoff_recovery","Automatically recover Bluetooth when grouping","boolean",context.getSharedPreferences("wpar_prefs",Context.MODE_PRIVATE).getBoolean("bt_handoff_recovery",true)).put("disabled",bt==null).put("hint","May briefly reconnect the current phone if its audio stream stalls during a group change."))));
  sections.put(section("web","Web and LAN controls","Shared endpoint for this web page, HA audio controls and room grouping. Changing its port or token requires reconnecting. Blank token keeps the current value.",new JSONArray()
   .put(number("ha_port","LAN control port",p.getHaPort(),1024,65535))
   .put(secret("ha_token","LAN API token",p.getHaToken()))));
  if(product!=null){JSONArray extra=product.sections();for(int i=0;i<extra.length();i++)sections.put(extra.getJSONObject(i));}
  return new JSONObject().put("sections",sections).put("pinRequired",product!=null&&product.locked()).put("controlAutostart",true).put("product",context.getPackageName());
 }
 public static void validate(JSONObject section,JSONObject values)throws Exception{
  JSONArray fields=section.getJSONArray("fields");Map<String,JSONObject> allowed=new HashMap<>();for(int i=0;i<fields.length();i++){JSONObject field=fields.getJSONObject(i);allowed.put(field.getString("key"),field);}
  Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();JSONObject field=allowed.get(key);if(field==null||field.optBoolean("disabled"))throw new IllegalArgumentException("Unknown or unavailable setting: "+key);
   Object value=values.get(key);String type=field.getString("type");
   if(type.equals("boolean")){if(!(value instanceof Boolean))throw new IllegalArgumentException("Invalid switch: "+key);}
   else if(type.equals("number")||type.equals("range")){if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue()||((Number)value).intValue()<field.getInt("min")||((Number)value).intValue()>field.getInt("max"))throw new IllegalArgumentException("Out of range: "+key);}
   else{if(type.equals("secret")&&value==JSONObject.NULL)continue;if(!(value instanceof String)||((String)value).length()>field.optInt("maxLength",4096)||((String)value).indexOf('\0')>=0)throw new IllegalArgumentException("Invalid text: "+key);
    if(type.equals("select")){JSONArray choices=field.getJSONArray("options");boolean found=false;for(int i=0;i<choices.length();i++)found|=value.equals(choices.getJSONObject(i).getString("value"));if(!found&&!value.equals(field.get("value")))throw new IllegalArgumentException("Unavailable choice: "+key);}
   }
  }
 }
 public static synchronized JSONObject save(Context context,PrefStore p,JSONObject request,String pin,String nativeToken)throws Exception{
  authorize(pin,nativeToken);if(com.wallpanel.multiroom.GroupRuntime.intercom())throw new IllegalStateException("End the intercom call before changing settings");
  String id=request.getString("section");JSONObject values=request.getJSONObject("values"),schema=null;JSONArray sections=snapshot(context,p).getJSONArray("sections");for(int i=0;i<sections.length();i++)if(sections.getJSONObject(i).getString("id").equals(id))schema=sections.getJSONObject(i);if(schema==null)throw new IllegalArgumentException("Unknown settings section");validate(schema,values);
  boolean restart=false,web=false;
  SharedPreferences preferences=context.getSharedPreferences("wpar_prefs",Context.MODE_PRIVATE);SharedPreferences.Editor edit=preferences.edit();
  if(id.equals("audio_network")){
   if(values.has("receiver_name")){String name=values.getString("receiver_name").trim();if(name.isEmpty()||name.indexOf('\n')>=0||name.indexOf('\r')>=0)throw new IllegalArgumentException("Enter a room name");restart|=!name.equals(p.getReceiverName());edit.putString(PrefStore.KEY_RECEIVER_NAME,name);context.getSharedPreferences("wallpanel_group_directory",Context.MODE_PRIVATE).edit().remove("room_name").apply();}
   if(values.has("network_interface")){String iface=values.getString("network_interface");restart|=!iface.equals(p.getInterfaceName());edit.putString(PrefStore.KEY_INTERFACE_NAME,iface);}
  }else if(id.equals("airplay")){
   if(values.has("enabled")){boolean enable=values.getBoolean("enabled");if(enable&&!PrivilegeChannel.available())throw new IllegalStateException("Prepare the AirPlay helper on the panel first");restart|=enable!=p.isEnabled();edit.putBoolean(PrefStore.KEY_ENABLED,enable);}
   if(values.has("rtsp_port")){if(values.getInt("rtsp_port")==p.getHaPort())throw new IllegalArgumentException("AirPlay and LAN controls need different ports");restart|=values.getInt("rtsp_port")!=p.getRtspPort();edit.putInt(PrefStore.KEY_RTSP_PORT,values.getInt("rtsp_port"));}
   if(values.has("latency_offset_ms"))edit.putInt(PrefStore.KEY_LATENCY_OFFSET_MS,values.getInt("latency_offset_ms"));
   if(values.has("volume_trim"))edit.putInt(PrefStore.KEY_VOLUME_TRIM,values.getInt("volume_trim"));
  }else if(id.equals("web")){
   if(values.has("ha_port")){if(values.getInt("ha_port")==p.getRtspPort()||Arrays.asList(1704,1705,1718).contains(values.getInt("ha_port")))throw new IllegalArgumentException("LAN port is reserved for audio");web|=values.getInt("ha_port")!=p.getHaPort();edit.putInt(PrefStore.KEY_HA_PORT,values.getInt("ha_port"));}
   boolean clear=values.has("ha_token")&&values.isNull("ha_token");String token=clear?"":values.optString("ha_token","");if(!token.matches("[!-~]{0,256}"))throw new IllegalArgumentException("Token must have no spaces or line breaks");if(!token.isEmpty()||clear){edit.putString(PrefStore.KEY_HA_TOKEN,token);web=true;}
  }else if(id.equals("grouping")){
   if(values.has("buffer_ms"))context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE).edit().putInt("buffer_ms",values.getInt("buffer_ms")).commit();
   if(values.has("room_delay_ms"))com.wallpanel.multiroom.GroupRuntime.roomTiming(context,values.getInt("room_delay_ms"));
  }else if(id.equals("bluetooth")){
   if(values.has("bt_handoff_recovery"))edit.putBoolean("bt_handoff_recovery",values.getBoolean("bt_handoff_recovery"));
   if(values.has("enabled")){BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();if(bt==null||values.getBoolean("enabled")!=bt.isEnabled()&&!(values.getBoolean("enabled")?bt.enable():bt.disable()))throw new IllegalStateException("Android rejected the Bluetooth change");}
  }else{if(product==null)throw new IllegalArgumentException("Unavailable product settings");product.save(id,values,request);}
  if(!edit.putBoolean(PrefStore.KEY_HA_ENABLED,true).putBoolean(PrefStore.KEY_AUTOSTART,true).commit())throw new IOException("Settings could not be saved");
  if(id.equals("bluetooth")&&values.has("bt_handoff_recovery"))com.wallpanel.multiroom.GroupRuntime.bluetoothRecovery(values.getBoolean("bt_handoff_recovery"));
  BootFlags.save(context,p.isEnabled(),true);ReceiverState.INSTANCE.setVolumeTrim(p.getVolumeTrim());
  if(restart||web){final boolean renew=web;new Handler(Looper.getMainLooper()).postDelayed(()->{if(renew)context.stopService(new Intent(context,ReceiverService.class));new Handler(Looper.getMainLooper()).postDelayed(()->context.startForegroundService(new Intent(context,ReceiverService.class).setAction(renew?ReceiverService.ACTION_ENSURE_STARTED:p.isEnabled()?ReceiverService.ACTION_RECONFIGURE:ReceiverService.ACTION_STOP)),renew?300:0);},350);}
  return new JSONObject().put("accepted",true).put("reconnect",web);
 }
 private static void authorize(String pin,String nativeToken)throws Exception{if(product!=null&&product.locked()&&!localKey.equals(nativeToken)&&!product.authorize(pin))throw new SecurityException("Administrator PIN required");}
 public static JSONObject action(Context context,String name,String pin,String nativeToken)throws Exception{
  authorize(pin,nativeToken);String message;
  if(name.equals("check_ptp"))message=PrivilegeChannel.available()?"PTP helper available":"PTP helper unavailable";
  else if(name.equals("bluetooth_connect")){context.startActivity(new Intent(context,BluetoothConnectActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="Choose a paired phone on the panel";}
  else if(name.equals("logs")){File file=new File(context.getFilesDir(),"app.log");ArrayDeque<String> lines=new ArrayDeque<>();try(BufferedReader in=new BufferedReader(new FileReader(file))){String line;while((line=in.readLine())!=null){if(lines.size()==100)lines.removeFirst();lines.add(line);}}message=android.text.TextUtils.join("\n",lines);}
  else if(name.equals("app_permissions")){context.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="App permissions requested on the panel";}
  else if(name.equals("android_bluetooth")){context.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="Bluetooth settings opened on the panel";}
  else if(name.equals("bluetooth_pair")){context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="Confirm discoverability on the panel";}
  else if(product!=null)message=product.action(name);else throw new IllegalArgumentException("Unknown action");
  return new JSONObject().put("accepted",true).put("message",message);
 }
 private SettingsHub(){}
}
