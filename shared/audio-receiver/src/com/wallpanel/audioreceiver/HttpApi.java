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
 private volatile boolean running=true;private ServerSocket serverSocket;
 private final ExecutorService clients=new ThreadPoolExecutor(1,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 public HttpApi(Context c,WifiManager wifi,PowerManager power,PrefStore p,ReceiverState s,LogBuf logs,AudioSourceModule bluetooth){this.bluetooth=bluetooth;context=c;prefs=p;state=s;logBuf=logs;multicastLock=wifi.createMulticastLock("wpar_mdns");multicastLock.setReferenceCounted(false);wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wpar:stream");wakeLock.setReferenceCounted(false);}
 public void acquireMulticastLock(){if(!multicastLock.isHeld())multicastLock.acquire();state.setMulticastLockHeld(true);}
 public void releaseMulticastLock(){if(multicastLock.isHeld())multicastLock.release();state.setMulticastLockHeld(false);}
 public PowerManager.WakeLock getWakeLock(){return wakeLock;}
 public void run(){try{serverSocket=new ServerSocket(prefs.getHaPort());state.setHaPort(prefs.getHaPort());state.setHaTokenEnabled(!prefs.getHaToken().isEmpty());while(running){Socket s=serverSocket.accept();try{clients.execute(()->handle(s));}catch(RejectedExecutionException e){s.close();}}}catch(Exception e){if(running)Log.w("HttpApi","API failed",e);}}
 private void reply(Socket s,int code,String type,byte[] data)throws IOException{OutputStream out=s.getOutputStream();String reason=code==200?"OK":code==401?"Unauthorized":code==404?"Not Found":"Bad Request";out.write(("HTTP/1.1 "+code+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes("US-ASCII"));out.write(data);out.flush();}
 private void json(Socket s,int code,String body)throws IOException{reply(s,code,"application/json; charset=utf-8",body.getBytes("UTF-8"));}
 private void handle(Socket socket){try(Socket s=socket){
  s.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));String first=in.readLine();if(first==null||first.length()>2048)return;String[] parts=first.split(" ");if(parts.length!=3)return;String method=parts[0],uri=parts[1].split("\\?",2)[0];
  Map<String,String> headers=new HashMap<>();String line;int size=0;while((line=in.readLine())!=null&&!line.isEmpty()){size+=line.length();if(size>8192)return;int n=line.indexOf(':');if(n>0)headers.put(line.substring(0,n).toLowerCase(Locale.ROOT),line.substring(n+1).trim());}
  if(method.equals("GET")&&(uri.equals("/")||uri.equals("/index.html"))){
   try(InputStream asset=context.getAssets().open("timing.html");ByteArrayOutputStream page=new ByteArrayOutputStream()){
    byte[] buffer=new byte[4096];int n;while((n=asset.read(buffer))!=-1)page.write(buffer,0,n);
    reply(s,200,"text/html; charset=utf-8",page.toByteArray());
   }return;
  }
  String token=prefs.getHaToken();if(!token.isEmpty()&&!("Bearer "+token).equals(headers.get("authorization"))){json(s,401,"{}");return;}
  if(method.equals("GET")){
   if(uri.equals("/api/v1/status")){json(s,200,buildStatusJson());return;}
   if(uri.equals("/api/v1/metadata")){json(s,200,buildMetadataJson());return;}
   if(uri.equals("/api/v1/cover")||uri.equals("/api/v1/cover.jpg")){byte[] art=state.getArtwork();if(art==null)json(s,404,"{}");else reply(s,200,art.length>1&&(art[0]&255)==137?"image/png":"image/jpeg",art);return;}
  }else if(method.equals("POST")){
   if(uri.equals("/api/v1/enable")||uri.equals("/api/v1/disable")){
    boolean enable=uri.endsWith("/enable");json(s,200,"{\"accepted\":true}");new Handler(Looper.getMainLooper()).post(()->{Intent i=new Intent(context,ReceiverService.class);i.setAction(enable?ReceiverService.ACTION_START:ReceiverService.ACTION_STOP);context.startService(i);});return;
   }
   if(uri.equals("/api/v1/timing")){
    int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
    if(length<1||length>256){json(s,400,"{}");return;}
    char[] data=new char[length];int at=0;while(at<length){int n=in.read(data,at,length-at);if(n<0)throw new IOException("Short body");at+=n;}
    try {
      org.json.JSONObject body=new org.json.JSONObject(new String(data));Object value=body.get("latencyOffsetMs");
      if(!(value instanceof Integer||value instanceof Long)){json(s,400,"{}");return;}
      long offset=((Number)value).longValue();if(offset< -500||offset>500){json(s,400,"{}");return;}
      prefs.put(PrefStore.KEY_LATENCY_OFFSET_MS,(int)offset);
      json(s,200,"{\"accepted\":true,\"restartRequired\":false}");
    }catch(org.json.JSONException invalid){json(s,400,"{}");}
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
    stream.put("artwork", state.isArtworkAvailable());

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
    meta.put("artworkAvailable", state.isArtworkAvailable());
    return Json.object(meta);
  }


 public void stop(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(IOException ignored){}clients.shutdownNow();}
}
