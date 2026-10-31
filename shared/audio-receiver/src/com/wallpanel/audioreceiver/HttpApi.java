package com.wallpanel.audioreceiver;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public class HttpApi implements Runnable {
 private final Context context;private final PrefStore prefs;private final ReceiverState state;private final LogBuf logBuf;
 private final WifiManager.MulticastLock multicastLock;private final PowerManager.WakeLock wakeLock;
 private final AudioSourceModule bluetooth;
 private com.wallpanel.multiroom.GroupHub groupHub;
 private volatile boolean running=true;private final RebindableServer listener;
 private final ExecutorService clients=new ThreadPoolExecutor(1,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 public HttpApi(Context c,WifiManager wifi,PowerManager power,PrefStore p,ReceiverState s,LogBuf logs,AudioSourceModule bluetooth){this.bluetooth=bluetooth;context=c;prefs=p;state=s;logBuf=logs;listener=new RebindableServer(p.getHaPort());multicastLock=wifi.createMulticastLock("wpar_mdns");multicastLock.setReferenceCounted(false);wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wpar:stream");wakeLock.setReferenceCounted(false);}
 public void acquireMulticastLock(){if(!multicastLock.isHeld())multicastLock.acquire();state.setMulticastLockHeld(true);}
 public void releaseMulticastLock(){if(multicastLock.isHeld())multicastLock.release();state.setMulticastLockHeld(false);}
 public PowerManager.WakeLock getWakeLock(){return wakeLock;}
 public void run(){try{groupHub=new com.wallpanel.multiroom.GroupHub(context,prefs);groupHub.networkListener(()->listener.rebind());groupHub.start();state.setHaPort(prefs.getHaPort());state.setHaTokenEnabled(!prefs.getHaToken().isEmpty());while(running){Socket s=listener.accept();try{clients.execute(()->handle(s));}catch(RejectedExecutionException e){s.close();}}}catch(Exception e){if(running)Log.w("HttpApi","API failed",e);}finally{listener.close();if(groupHub!=null)groupHub.close();}}
 private void reply(Socket s,int code,String type,byte[] data)throws IOException{OutputStream out=s.getOutputStream();String reason=code==200?"OK":code==401?"Unauthorized":code==404?"Not Found":code==500?"Internal Server Error":"Bad Request";out.write(("HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes("US-ASCII"));out.write(data);out.flush();}
 private void json(Socket s,int code,String body)throws IOException{reply(s,code,"application/json; charset=utf-8",body.getBytes("UTF-8"));}
 private void handle(Socket socket){try(Socket s=socket){
  s.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),"ISO-8859-1"));String first=in.readLine();if(first==null||first.length()>2048)return;String[] parts=first.split(" ");if(parts.length!=3)return;String method=parts[0],uri=parts[1].split("\\?",2)[0];
  Map<String,String> headers=new HashMap<>();String line;int size=0;while((line=in.readLine())!=null&&!line.isEmpty()){size+=line.length();if(size>8192)return;int n=line.indexOf(':');if(n>0)headers.put(line.substring(0,n).toLowerCase(Locale.ROOT),line.substring(n+1).trim());}
  if(method.equals("GET")&&(uri.equals("/")||uri.equals("/index.html")||uri.equals("/groups")||uri.equals("/settings"))){
   try(InputStream asset=context.getAssets().open(uri.equals("/settings")?"settings.html":"groups.html");ByteArrayOutputStream page=new ByteArrayOutputStream()){
    byte[] buffer=new byte[4096];int n;while((n=asset.read(buffer))!=-1)page.write(buffer,0,n);
    reply(s,200,"text/html; charset=utf-8",page.toByteArray());
   }return;
  }
  if(method.equals("GET")&&uri.equals("/api/v1/identity")){try{String query=parts[1].split("\\?",2)[1];if(!query.startsWith("challenge="))throw new IllegalArgumentException();json(s,200,groupHub.identity(query.substring(10)).toString());}catch(Exception invalid){json(s,400,"{}");}return;}
  String token=prefs.getHaToken();if(!token.isEmpty()&&!("Bearer "+token).equals(headers.get("authorization"))){json(s,401,"{}");return;}
  if(method.equals("POST")&&!headers.getOrDefault("content-type","").split(";",2)[0].trim().equalsIgnoreCase("application/json")){json(s,400,"{\"error\":\"JSON content type required\"}");return;}
  if(uri.equals("/api/v1/settings")||uri.equals("/api/v1/settings-action")){
   try{
    if(method.equals("GET")&&uri.equals("/api/v1/settings")){json(s,200,SettingsHub.snapshot(context,prefs).toString());return;}
    if(!method.equals("POST")){json(s,400,"{}");return;}
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>65536){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short settings body");at+=n;}
    org.json.JSONObject body=new org.json.JSONObject(new String(new String(data).getBytes("ISO-8859-1"),"UTF-8"));String pin=headers.getOrDefault("x-panel-pin",""),nativeKey=headers.getOrDefault("x-local-admin","");
    json(s,200,(uri.endsWith("settings-action")?SettingsHub.action(context,body.getString("action"),pin,nativeKey):SettingsHub.save(context,prefs,body,pin,nativeKey)).toString());
   }catch(SecurityException denied){json(s,403,"{\"error\":\"Administrator PIN required\"}");}
   catch(IllegalArgumentException|org.json.JSONException invalid){json(s,400,new org.json.JSONObject().put("error",invalid.getMessage()).toString());}
   catch(Exception failed){json(s,500,new org.json.JSONObject().put("error",failed.getMessage()==null?"Settings could not be applied":failed.getMessage()).toString());}
   return;
  }
  if(method.equals("GET")){
   if(uri.equals("/api/v1/library")){
    try{Map<String,String> query=new HashMap<>();String[] raw=parts[1].split("\\?",2);if(raw.length==2)for(String field:raw[1].split("&")){String[] pair=field.split("=",2);query.put(URLDecoder.decode(pair[0],"UTF-8"),pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"");}
     json(s,200,groupHub.library(query.getOrDefault("target",""),query.getOrDefault("folder","root"),Integer.parseInt(query.getOrDefault("offset","0")),Integer.parseInt(query.getOrDefault("limit","100")),query.getOrDefault("q",""),Boolean.parseBoolean(query.getOrDefault("refresh","false"))).toString());
    }catch(IllegalArgumentException invalid){json(s,400,"{\"error\":\"Invalid music library request\"}");}catch(Exception unavailable){json(s,500,"{\"error\":\"Music library unavailable; check source panel access and music permission\"}");}return;
   }
   if(uri.equals("/api/v1/groups")){json(s,200,groupHub.snapshot().toString());return;}
   if(uri.equals("/api/v1/status")){json(s,200,buildStatusJson());return;}
   if(uri.equals("/api/v1/metadata")){json(s,200,buildMetadataJson());return;}
   if(uri.equals("/api/v1/cover")||uri.equals("/api/v1/cover.jpg")){byte[] art;String id;synchronized(state){art=state.getArtwork();id=state.getArtworkId();}String wanted=headers.get("if-match");if(art==null||wanted!=null&&!wanted.equals("\""+id+"\""))json(s,404,"{}");else reply(s,200,art.length>1&&(art[0]&255)==137?"image/png":"image/jpeg",art);return;}
  }else if(method.equals("POST")){
   if(uri.equals("/api/v1/group-command")){
    String origin=headers.get("origin");if(origin!=null&&!Objects.equals(new URI(origin).getRawAuthority(),headers.get("host"))){json(s,400,"{\"error\":\"Use this panel's group page\"}");return;}
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>16384){json(s,400,"{}");return;}
    char[] bytes=new char[length];int at=0;while(at<length){int count=in.read(bytes,at,length-at);if(count<0)throw new IOException("Short body");at+=count;}
    try{String body=new String(new String(bytes).getBytes("ISO-8859-1"),"UTF-8");json(s,200,groupHub.command(new org.json.JSONObject(body)).toString());}
    catch(SecurityException denied){json(s,400,"{\"error\":\"The other room requires its API token\"}");}
    catch(org.json.JSONException|IllegalArgumentException invalid){json(s,400,new org.json.JSONObject().put("error",invalid.getMessage()).toString());}
    catch(Exception failed){json(s,500,new org.json.JSONObject().put("error",failed instanceof IllegalStateException?failed.getMessage():"Room is unavailable or rejected the command").toString());}return;
   }
   if(uri.equals("/api/v1/group-volume")){
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>128){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short body");at+=n;}
    try{Object value=new org.json.JSONObject(new String(data)).get("volume");if(!(value instanceof Number)){json(s,400,"{}");return;}
     com.wallpanel.multiroom.GroupRuntime.volume(context,((Number)value).doubleValue());json(s,200,"{\"accepted\":true}");
    }catch(org.json.JSONException|IllegalArgumentException invalid){json(s,400,"{}");}catch(IOException failed){json(s,500,"{}");}return;
   }
   if(uri.equals("/api/v1/transport")){
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>128){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short body");at+=n;}
    try{
     String command=new org.json.JSONObject(new String(data)).getString("command");RemoteProtocol.verb(command);
     boolean accepted=ReceiverRemote.request(command);
     json(s,200,"{\"accepted\":"+accepted+",\"scope\":\"source_group\"}");
    }catch(IllegalArgumentException|org.json.JSONException invalid){json(s,400,"{}");}
    return;
   }
   if(uri.equals("/api/v1/enable")||uri.equals("/api/v1/disable")){
    boolean enable=uri.endsWith("/enable");json(s,200,"{\"accepted\":true}");new Handler(Looper.getMainLooper()).post(()->{Intent i=new Intent(context,ReceiverService.class);i.setAction(enable?ReceiverService.ACTION_START:ReceiverService.ACTION_STOP);context.startService(i);});return;
   }
   if(uri.equals("/api/v1/timing")||uri.equals("/api/v1/group-timing")){
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
    if(length<1||length>256){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short body");at+=n;}
    try {
      boolean group=uri.equals("/api/v1/group-timing");
      org.json.JSONObject body=new org.json.JSONObject(new String(data));Object value=body.get(group?"roomDelayMs":"latencyOffsetMs");
      if(!(value instanceof Integer||value instanceof Long)){json(s,400,"{}");return;}
      long offset=((Number)value).longValue();if(offset< -500||offset>500){json(s,400,"{}");return;}
      if(group)com.wallpanel.multiroom.GroupRuntime.roomTiming(context,(int)offset);else prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,(int)offset);
      json(s,200,"{\"accepted\":true,\"restartRequired\":false}");
    }catch(org.json.JSONException invalid){json(s,400,"{}");}catch(IOException failed){json(s,500,"{\"error\":\"Unable to save timing\"}");}
    return;
   }
   if(uri.equals("/api/v1/volume")){
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>1024){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short body");at+=n;}
    org.json.JSONObject body=new org.json.JSONObject(new String(data));int volume=body.has("volume")?body.getInt("volume"):body.getInt("trim");if(volume<0||volume>100){json(s,400,"{}");return;}
    prefs.put(PrefStore.KEY_VOLUME_TRIM,volume);new Handler(Looper.getMainLooper()).post(()->state.setVolumeTrim(volume));json(s,200,"{\"ok\":true}");return;
   }
  }
  json(s,404,"{}");
 }catch(Exception e){Log.w("HttpApi","Request failed",e);}}
  private String buildStatusJson() {
    Map<String, Object> app = new HashMap<>();
    try{app.put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName);}catch(Exception ignored){app.put("version","unknown");}
    app.put("enabled", prefs.isEnabled());
    app.put("autostart", prefs.isAutostart());

    Map<String, Object> receiver = new HashMap<>();
    receiver.put("remote",ReceiverRemote.snapshot());
    receiver.put("state", state.getState().name());
    receiver.put("error",state.getErrorMessage());
    receiver.put("name", prefs.getReceiverName());
    receiver.put("protocol", "airplay2");
    receiver.put("rtspPort", prefs.getRtspPort());

    Map<String, Object> root = new HashMap<>();
    root.put("available", false);
    root.put("helperAvailable", state.isRootAvailable());
    root.put("command", state.getSuCommand());

    Map<String, Object> processes = new HashMap<>();
    processes.put("uptimeSeconds", (System.currentTimeMillis() - state.getUptimeStartMs()) / 1000);
    processes.put("restarts", state.getRestarts());

    Map<String, Object> audio = new HashMap<>();
    audio.put("backend", "android");
    audio.put("rate", 48000);
    audio.put("format", "S16_LE");
    audio.put("channels", 2);
    audio.put("outputDescription", state.getOutputDescription());
    audio.put("trim", state.getVolumeTrim());
    audio.put("latencyOffsetMs", prefs.getLatencyOffsetMs());
    audio.put("timing",state.getAudioTiming());
    audio.put("underruns", state.getUnderruns());
    audio.put("corrections", state.getDriftCorrections());

    Map<String, Object> stream = new HashMap<>();
    stream.put("active", state.isPlaying());
    stream.put("playing", state.isPlaying());
    stream.put("clientIp", state.getClientIp());
    stream.put("clientModel", state.getClientModel());
    stream.put("title", state.getTitle());
    stream.put("artist", state.getArtist());
    stream.put("album", state.getAlbum());
    stream.put("artwork", state.isArtworkAvailable());stream.put("artworkId",state.getArtworkId());

    Map<String, Object> network = new HashMap<>();
    network.put("ip", state.getLocalIp());
    network.put("multicastLock", state.isMulticastLockHeld());

    Map<String, Object> ha = new HashMap<>();
    ha.put("port", state.getHaPort());
    ha.put("token", state.isHaTokenEnabled());

    Map<String, Object> status = new HashMap<>();
    status.put("app", app);
    status.put("receiver", receiver);
    status.put("root", root);
    status.put("processes", processes);
    status.put("audio", audio);
    status.put("stream", stream);
    status.put("network", network);
    status.put("ha", ha);
    Map<String,Object> group=new LinkedHashMap<>();
    group.put("active",com.wallpanel.multiroom.GroupRuntime.active());group.put("paused",com.wallpanel.multiroom.GroupRuntime.paused());
    group.put("status",com.wallpanel.multiroom.GroupRuntime.status());
    android.content.SharedPreferences grouping=context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE);
    group.put("role",grouping.getString("role","receiver"));group.put("roomDelayMs",grouping.getInt("room_delay_ms",0));
    group.put("volume",com.wallpanel.multiroom.GroupRuntime.volume(context));group.put("muted",com.wallpanel.multiroom.GroupRuntime.muted(context));
    status.put("group",group);
    Map<String, Object> sources = new LinkedHashMap<>();
    sources.put("airplay", new AirPlaySourceModule(state).snapshot());
    sources.put("bluetooth", bluetooth.snapshot());
    status.put("sources", sources);
    status.put("bluetooth", bluetooth.snapshot());

    return Json.object(status);
  }

  private String buildMetadataJson() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("playing", state.isPlaying());
    meta.put("clientIp", state.getClientIp());
    meta.put("clientModel", state.getClientModel());
    meta.put("title", state.getTitle());
    meta.put("artist", state.getArtist());
    meta.put("album", state.getAlbum());
    meta.put("artworkAvailable", state.isArtworkAvailable());meta.put("artworkId",state.getArtworkId());
    return Json.object(meta);
  }


 public void stop(){running=false;listener.close();clients.shutdownNow();}
}
