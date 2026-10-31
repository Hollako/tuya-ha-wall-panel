package com.wallpanel.multiroom;

import android.content.*;
import android.net.*;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import com.wallpanel.audioreceiver.PrefStore;
import com.wallpanel.audioreceiver.ReceiverNetwork;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** LAN directory and group orchestration. Audio never passes through this class. */
public final class GroupHub implements AutoCloseable {
 public static final String TYPE="_wallpanel-group._tcp.";
 private static volatile GroupHub instance;
 private final Context context;private final PrefStore api;private final SharedPreferences prefs,group;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final Map<String,Peer> peers=new LinkedHashMap<>();private final ReentrantLock operation=new ReentrantLock();
 private final NsdManager nsd;private final WifiManager.MulticastLock multicast;
 private NsdManager.DiscoveryListener discovery;private NsdManager.RegistrationListener registration;
 private final ArrayDeque<NsdServiceInfo> resolving=new ArrayDeque<>();private boolean resolverBusy;private volatile boolean closed;
 private volatile String lastOperation="Ready";private final String id;
 private String lastNetwork;private long nextDiscoveryRefresh;private Runnable networkListener;
 private final MusicLibrary music;
 private static class Peer {String id,name,host;int port;long good;JSONObject state;boolean authorized;Peer(String i,String n,String h,int p){id=i;name=n;host=h;port=p;}}
 public static GroupHub get(){return instance;}
 public GroupHub(Context value,PrefStore store){
  context=value.getApplicationContext();api=store;prefs=context.getSharedPreferences("wallpanel_group_directory",Context.MODE_PRIVATE);group=context.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE);
  music=new MusicLibrary(context);
  String saved=group.getString("id","");if(saved.isEmpty()){saved="panel-"+UUID.randomUUID();group.edit().putString("id",saved).commit();}id=saved;
  nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
  multicast=((WifiManager)context.getSystemService(Context.WIFI_SERVICE)).createMulticastLock("wallpanel-group-directory");multicast.setReferenceCounted(false);
  try{JSONArray known=new JSONArray(prefs.getString("peers","[]"));for(int i=0;i<known.length()&&i<64;i++){JSONObject p=known.getJSONObject(i);peers.put(p.getString("id"),new Peer(p.getString("id"),p.getString("name"),p.getString("host"),p.getInt("port")));}}catch(Exception ignored){}
 }
 public void networkListener(Runnable listener){networkListener=listener;}
 public void start(){instance=this;multicast.acquire();lastNetwork=networkIdentity();nextDiscoveryRefresh=SystemClock.elapsedRealtime()+30000;main.post(()->{advertise();discover();});worker.scheduleWithFixedDelay(()->{try{poll();}catch(Exception ignored){}},1,4,TimeUnit.SECONDS);}
 private String networkIdentity(){
  String iface=ReceiverNetwork.interfaceName(context,api);StringBuilder key=new StringBuilder(String.valueOf(iface));
  ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
  key.append("/bound=").append(cm.getBoundNetworkForProcess());
  for(Network network:cm.getAllNetworks()){LinkProperties link=cm.getLinkProperties(network);if(link!=null&&link.getInterfaceName()!=null&&link.getInterfaceName().equals(iface)){key.append('/').append(network);for(LinkAddress address:link.getLinkAddresses())if(address.getAddress() instanceof Inet4Address)key.append('/').append(address);}}
  return key.toString();
 }
 private void refreshDiscovery(){
  if(closed)return;advertise();
  if(discovery!=null)try{nsd.stopServiceDiscovery(discovery);return;}catch(Exception ignored){}
  discover();
 }
 private String name(){return api.getReceiverName();}
 private void advertise(){
  if(closed)return;
  if(registration!=null){try{nsd.unregisterService(registration);}catch(Exception ignored){}registration=null;}
  NsdServiceInfo service=new NsdServiceInfo();service.setServiceName("WallPanel-"+id.substring(Math.max(0,id.length()-12)));service.setServiceType(TYPE);service.setPort(api.getHaPort());service.setAttribute("id",id);service.setAttribute("v","1");
  registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){}public void onRegistrationFailed(NsdServiceInfo s,int e){}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int e){}};
  try{nsd.registerService(service,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception ignored){}
 }
 private void discover(){
  if(closed)return;
  discovery=new NsdManager.DiscoveryListener(){public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){main.post(()->{if(!closed&&discovery==this)discover();});}
   public void onStartDiscoveryFailed(String t,int e){main.postDelayed(()->{if(!closed&&discovery==this){try{nsd.stopServiceDiscovery(this);}catch(Exception ignored){}discover();}},10000);}
   public void onStopDiscoveryFailed(String t,int e){main.postDelayed(()->{if(!closed&&discovery==this)refreshDiscovery();},10000);}
   public void onServiceLost(NsdServiceInfo s){} // HTTP liveness, not an mDNS goodbye, decides availability.
   public void onServiceFound(NsdServiceInfo s){main.post(()->{if(resolving.size()<64){resolving.add(s);resolveNext();}});}};
  try{nsd.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception ignored){}
 }
 private void resolveNext(){
  if(closed||resolverBusy||resolving.isEmpty())return;resolverBusy=true;NsdServiceInfo next=resolving.remove();
  try{nsd.resolveService(next,new NsdManager.ResolveListener(){
   public void onResolveFailed(NsdServiceInfo s,int e){main.post(()->{resolverBusy=false;resolveNext();});}
   public void onServiceResolved(NsdServiceInfo s){try{byte[] raw=s.getAttributes().get("id");String peerId=raw==null?"":new String(raw,StandardCharsets.UTF_8);if(!closed&&s.getHost() instanceof Inet4Address&&validId(peerId)&&!peerId.equals(id))remember(peerId,"Wall Panel",s.getHost().getHostAddress(),s.getPort());}catch(Exception ignored){}main.post(()->{resolverBusy=false;resolveNext();});}
  });}catch(Exception ignored){resolverBusy=false;resolveNext();}
 }
 private static boolean validId(String value){return GroupPlan.validId(value);}
 private static String label(String value){return GroupPlan.label(value);}
 private synchronized void remember(String key,String name,String host,int port)throws Exception{
  if(!validId(key)||key.equals(id)||port<1||port>65535||host.length()>253)throw new IllegalArgumentException("Invalid panel address");
  Peer peer=peers.get(key);if(peer==null){if(peers.size()>=64)throw new IllegalStateException("Panel directory is full");peers.put(key,new Peer(key,name,host,port));}
  else{if(!peer.host.equals(host)||peer.port!=port){peer.good=0;peer.state=null;peer.authorized=false;}peer.host=host;peer.port=port;if(!name.equals("Wall Panel"))peer.name=name;}
  JSONArray saved=new JSONArray();for(Peer p:peers.values())saved.put(new JSONObject().put("id",p.id).put("name",p.name).put("host",p.host).put("port",p.port));prefs.edit().putString("peers",saved.toString()).apply();
 }
 private synchronized List<Peer> known(){return new ArrayList<>(peers.values());}
 private synchronized Peer peer(String key){Peer peer=peers.get(key);if(peer==null)throw new IllegalArgumentException("Panel not discovered");return peer;}
 private String credential(Peer peer){return prefs.getString("auth_host_"+peer.id,"").equals(peer.host+":"+peer.port)?prefs.getString("auth_"+peer.id,""):"";}
 public JSONObject identity(String challenge)throws Exception{return new JSONObject().put("v",1).put("id",id).put("proof",RoomIdentity.proof(api.getHaToken(),id,challenge));}
 private String recoverCredential(Peer peer)throws Exception{
  String token=credential(peer);if(!token.isEmpty())return token;String saved=prefs.getString("auth_"+peer.id,"");if(saved.isEmpty())return "";
  String nonce=RoomIdentity.challenge();JSONObject proof=requestPath(peer,null,"","/api/v1/identity?challenge="+nonce);
  if(!peer.id.equals(proof.optString("id"))||!RoomIdentity.verify(saved,peer.id,nonce,proof.optString("proof")))throw new SecurityException("Room identity could not be verified at its new address");
  synchronized(this){Peer current=peers.get(peer.id);if(current==null||!current.host.equals(peer.host)||current.port!=peer.port)throw new IOException("Room moved during identity verification");if(!prefs.edit().putString("auth_host_"+peer.id,peer.host+":"+peer.port).commit())throw new IOException("Verified room address could not be saved");}return saved;
 }
 private JSONObject request(Peer peer,JSONObject body,String token)throws Exception{
  return requestPath(peer,body,token,body==null?"/api/v1/groups":"/api/v1/group-command");
 }
 private JSONObject requestPath(Peer peer,JSONObject body,String token,String path)throws Exception{
  String[] address=path.split("\\?",2);URL url=new URL(new URI("http",null,peer.host,peer.port,address[0],null,null).toASCIIString()+(address.length==2?"?"+address[1]:""));
  ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);String iface=ReceiverNetwork.interfaceName(context,api);Network selected=null;
  if(iface!=null)for(Network n:cm.getAllNetworks()){LinkProperties link=cm.getLinkProperties(n);if(link!=null&&iface.equals(link.getInterfaceName())){selected=n;break;}}
  HttpURLConnection connection=(HttpURLConnection)(selected==null?url.openConnection():selected.openConnection(url));
  connection.setConnectTimeout(1000);connection.setReadTimeout(body!=null&&body.optString("op").equals("_leave")?5000:1500);connection.setInstanceFollowRedirects(false);
  try{if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
   if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
   int code=connection.getResponseCode();if(code==401)throw new SecurityException("API token required for "+peer.name);if(code!=200)throw new IOException("Room request failed ("+code+")");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] part=new byte[4096];int n;while((n=in.read(part))!=-1){if(bytes.size()+n>524288)throw new IOException("Room response too large");bytes.write(part,0,n);}}
   return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
  }finally{connection.disconnect();}
 }
 private void poll(){
  String network=networkIdentity();long now=SystemClock.elapsedRealtime();
  if(!network.equals(lastNetwork)){lastNetwork=network;nextDiscoveryRefresh=now+30000;main.post(()->{if(closed)return;if(networkListener!=null)networkListener.run();GroupService service=GroupRuntime.service;if(service!=null)service.networkChanged();refreshDiscovery();});}
  boolean stale=false;for(Peer p:known()){if(closed)return;pollPeer(p);synchronized(this){stale|=p.good==0||SystemClock.elapsedRealtime()-p.good>20000;}}
  if(stale&&now>=nextDiscoveryRefresh){nextDiscoveryRefresh=now+30000;main.post(this::refreshDiscovery);}
 }
 private void pollPeer(Peer p){
  Peer target;synchronized(this){target=new Peer(p.id,p.name,p.host,p.port);}
  try{JSONObject directory=request(target,null,recoverCredential(target));JSONObject found=find(directory.getJSONArray("panels"),target.id);if(!target.id.equals(directory.getString("self"))||found==null)throw new IOException("Panel identity mismatch");
   synchronized(this){if(closed||!p.host.equals(target.host)||p.port!=target.port)return;p.state=found;p.name=found.optString("name",p.name);p.good=SystemClock.elapsedRealtime();p.authorized=true;}
   mergePresets(directory.optJSONArray("presets"));GroupService service=GroupRuntime.service;if(service!=null)service.followLeader(target.id,target.host);
  }catch(SecurityException denied){synchronized(this){if(p.host.equals(target.host)&&p.port==target.port){p.authorized=false;p.good=SystemClock.elapsedRealtime();}}}catch(Exception unavailable){}
 }
 private static JSONObject find(JSONArray values,String key)throws JSONException{for(int i=0;i<values.length();i++)if(key.equals(values.getJSONObject(i).optString("id")))return values.getJSONObject(i);return null;}
 private synchronized void mergePresets(JSONArray incoming)throws JSONException{
  if(incoming==null)return;JSONArray saved=new JSONArray(prefs.getString("presets","[]"));
  for(int i=0;i<incoming.length()&&i<64;i++){JSONObject item=checkedPreset(incoming.getJSONObject(i));JSONObject old=find(saved,item.getString("id"));if(old==null){if(saved.length()<64)saved.put(item);}else if(item.getLong("updated")>old.getLong("updated")){for(int j=0;j<saved.length();j++)if(saved.getJSONObject(j).getString("id").equals(item.getString("id")))saved.put(j,item);}}
  prefs.edit().putString("presets",saved.toString()).apply();
 }
 private static JSONObject checkedPreset(JSONObject item)throws JSONException{
  if(!item.getString("id").matches("group-[A-Za-z0-9-]{8,80}")||!validId(item.getString("leader")))throw new IllegalArgumentException("Invalid saved group");
  JSONArray members=members(item.getJSONArray("members"),item.getString("leader"));
  return new JSONObject().put("id",item.getString("id")).put("name",label(item.getString("name"))).put("leader",item.getString("leader")).put("members",members).put("updated",item.getLong("updated")).put("deleted",item.optBoolean("deleted",false));
 }
 private static JSONArray members(JSONArray input,String leader)throws JSONException{
  if(input.length()>32)throw new IllegalArgumentException("Choose up to 32 rooms");ArrayList<String> values=new ArrayList<>();for(int i=0;i<input.length();i++)values.add(input.getString(i));return new JSONArray(GroupPlan.members(values,leader));
 }
 private String localLeader(){if(!GroupRuntime.active())return "";if(group.getString("role","receiver").equals("leader"))return id;String stored=group.getString("group_leader","");if(!stored.isEmpty())return stored;for(Peer p:known())if(p.host.equals(group.getString("leader","")))return p.id;return "";}
 public JSONObject library(String target,String folder,int offset,int limit,String query,boolean refresh)throws Exception{
  JSONObject result;
  if(target==null||target.isEmpty()||target.equals(id)){result=music.browse(folder,offset,limit,query,refresh);result.put("node",id);}
  else{Peer p=peer(target);MusicLibrary.folderId(folder);String path="/api/v1/library?folder="+URLEncoder.encode(folder,"UTF-8")+"&offset="+offset+"&limit="+limit+"&q="+URLEncoder.encode(query,"UTF-8")+"&refresh="+refresh;result=requestPath(p,null,credential(p),path);if(!target.equals(result.getString("node")))throw new IOException("Library room identity mismatch");}
  return result;
 }
 public String playingTitle(){String leader=localLeader();if(leader.equals(id))return group.getString("source_kind","file").equals("bluetooth")?"Bluetooth audio":group.getString("source_kind","file").equals("apps")?"App audio":group.getString("source_title","");try{Peer p=peer(leader);synchronized(this){return p.state==null?"":p.state.optString("sourceTitle","");}}catch(Exception ignored){return "";}}
 private JSONObject local()throws JSONException{
  String leader=localLeader();boolean active=GroupRuntime.active();GroupService service=GroupRuntime.service;
  String playback=!active?"idle":GroupRuntime.paused()?"paused":service!=null&&service.sourceFinished?"idle":service!=null&&service.connected?"playing":"buffering";
  boolean bluetooth=group.getString("source_kind","file").equals("bluetooth"),apps=group.getString("source_kind","file").equals("apps"),supported=BluetoothGroupSource.supported(context),appSupported=BluetoothGroupSource.appsAvailable();
  JSONObject nowPlaying=MediaActivity.snapshot(context);if(active&&!GroupRuntime.paused()&&group.getString("role","").equals("leader")&&bluetooth&&nowPlaying.optString("state").equals("buffering"))playback="buffering";
  return new JSONObject().put("id",id).put("name",name()).put("online",true).put("authorized",true).put("volume",GroupRuntime.volume(context)).put("muted",GroupRuntime.muted(context)).put("roomDelayMs",group.getInt("room_delay_ms",0)).put("active",active).put("leader",leader).put("group",leader).put("role",group.getString("role","receiver")).put("state",playback).put("connected",active&&service!=null&&service.connected).put("status",GroupRuntime.status()).put("sourceConfigured",bluetooth?supported:apps?appSupported:!group.getString("source","").isEmpty()).put("sourceTitle",bluetooth?"Bluetooth audio":apps?"App audio":group.getString("source_title","")).put("sourceKind",bluetooth?"bluetooth":apps?"apps":"file").put("bluetoothGroupAvailable",supported).put("appAudioGroupAvailable",appSupported).put("captureStatus",service==null?"inactive":service.captureStatus()).put("airplayControlAvailable",com.wallpanel.audioreceiver.ReceiverRemote.available()).put("localPlayerAvailable",MediaActivity.localAvailable()).put("nowPlaying",nowPlaying).put("sourcePending",group.contains("pending_source")).put("selectedTitle",group.getString("pending_title",""));
 }
 public synchronized JSONObject snapshot()throws JSONException{
  JSONArray rooms=new JSONArray().put(local());long now=SystemClock.elapsedRealtime();
  for(Peer peer:peers.values()){JSONObject p=peer.state==null?new JSONObject().put("id",peer.id).put("name",peer.name):new JSONObject(peer.state.toString());p.put("host",peer.host).put("port",peer.port).put("online",peer.good>0&&now-peer.good<20000).put("authorized",peer.authorized);rooms.put(p);}
  for(int i=0;i<rooms.length();i++){JSONObject room=rooms.getJSONObject(i);JSONObject leader=find(rooms,room.optString("leader"));if(room.optBoolean("active")&&!room.optString("state").equals("paused")&&leader!=null&&leader.optBoolean("active")&&(leader.optString("state").equals("paused")||leader.optString("state").equals("idle")||leader.optString("state").equals("buffering")))room.put("state",leader.getString("state"));}
  return new JSONObject().put("v",1).put("self",id).put("panels",rooms).put("presets",new JSONArray(prefs.getString("presets","[]"))).put("operation",lastOperation).put("busy",operation.isLocked());
 }
 /** Small actual-state snapshot for MQTT; no source probing, addresses or secrets. */
 public synchronized JSONObject membership()throws JSONException{
  boolean active=GroupRuntime.active();GroupService service=GroupRuntime.service;
  JSONArray rooms=new JSONArray().put(new JSONObject().put("id",id).put("active",active).put("leader",localLeader()).put("connected",active&&service!=null&&service.connected));
  for(Peer peer:peers.values())if(peer.state!=null){JSONObject p=peer.state;rooms.put(new JSONObject().put("id",peer.id).put("active",p.optBoolean("active")).put("leader",p.optString("leader")).put("connected",p.optBoolean("connected")));}
  return new JSONObject().put("v",1).put("self",id).put("panels",rooms);
 }
 private void leaveLocal()throws Exception{
  if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Leave confirmation requires a worker thread");
  CountDownLatch stopped=new CountDownLatch(1);
  main.post(new Runnable(){int attempts;public void run(){
   if(attempts++==0){GroupRuntime.stop(context);group.edit().remove("group_leader").apply();GroupRuntime.status("Room left group");}
   if(!GroupRuntime.active()){stopped.countDown();return;}
   if(attempts<50)main.postDelayed(this,50);
  }});
  if(!stopped.await(3,TimeUnit.SECONDS))throw new IOException("Room did not confirm leaving the group");
 }
 private void session(JSONObject body)throws Exception{
  final String leader=body.getString("leader");if(!validId(leader))throw new IllegalArgumentException("Invalid source room");final boolean own=leader.equals(id),restart=body.optBoolean("restart",false);final String host=own?"127.0.0.1":GroupPolicy.host(body.getString("host"));
  if(own&&!Arrays.asList("bluetooth","apps").contains(group.getString("source_kind","file")))GroupPolicy.source(group.getString("source",""));if(GroupRuntime.intercom())throw new IllegalStateException("End the intercom call first");
  main.post(new Runnable(){int waits;public void run(){
   if(closed)return;if(GroupRuntime.intercom()){GroupRuntime.status("Group change postponed: intercom is active");return;}
   if(!restart&&GroupRuntime.active()&&localLeader().equals(leader)&&group.getString("role","receiver").equals(own?"leader":"receiver")){if(!own&&GroupRuntime.service!=null)GroupRuntime.service.followLeader(leader,host);return;}
   if(GroupRuntime.active()){if(++waits>40){GroupRuntime.status("Could not stop previous group");return;}GroupRuntime.stop(context);main.postDelayed(this,50);return;}
   group.edit().putString("role",own?"leader":"receiver").putString("leader",host).putString("group_leader",leader).apply();GroupRuntime.start(context);
  }});
 }
 private JSONObject localCommand(JSONObject body)throws Exception{
  String op=body.getString("op");
  switch(op){
   case "room":
    // Validate the entire update before performing any writes.
    Double volume=body.has("volume")?body.getDouble("volume"):null;Integer delay=body.has("roomDelayMs")?GroupPolicy.roomDelay(body.getInt("roomDelayMs")):null;
    if(volume!=null&&(Double.isNaN(volume)||Double.isInfinite(volume)||volume<0||volume>1))throw new IllegalArgumentException("Invalid room volume");
    Boolean mute=body.has("muted")?body.getBoolean("muted"):null;String roomName=body.has("name")?label(body.getString("name")):null;
   if(roomName!=null){api.put(com.wallpanel.audioreceiver.PrefStore.KEY_RECEIVER_NAME,roomName);prefs.edit().remove("room_name").apply();}if(volume!=null)GroupRuntime.volume(context,volume);if(delay!=null)GroupRuntime.roomTiming(context,delay);
    if(mute!=null)GroupRuntime.mute(context,mute);break;
   case "source":GroupPolicy.source(body.getString("source"));if(GroupRuntime.active())group.edit().putString("pending_source",body.getString("source")).putString("pending_title","Audio URL").apply();else group.edit().putString("source",body.getString("source")).putString("source_kind","file").remove("source_title").remove("pending_source").remove("pending_title").apply();break;
   case "_input":
    String kind=body.getString("input");if(!Arrays.asList("file","bluetooth","apps").contains(kind))throw new IllegalArgumentException("Choose Local music, Bluetooth or App audio");
    if(kind.equals("bluetooth")){if(!BluetoothGroupSource.supported(context))throw new IllegalStateException("Enable Bluetooth and prepare the Bluetooth group bridge first");}
    else if(kind.equals("apps")){if(!BluetoothGroupSource.appsAvailable())throw new IllegalStateException("Prepare the app audio group bridge first");}
    else GroupPolicy.source(group.getString("pending_source",group.getString("source","")));
    if(GroupRuntime.intercom())throw new IllegalStateException("End the intercom call first");MediaActivity.exclusive(kind);boolean inputChanged=!kind.equals(group.getString("source_kind","file"));
    SharedPreferences.Editor inputEdit=group.edit().putString("source_kind",kind);if(kind.equals("file")&&group.contains("pending_source"))inputEdit.putString("source",group.getString("pending_source","")).putString("source_title",group.getString("pending_title","")).remove("pending_source").remove("pending_title");inputEdit.apply();
    session(new JSONObject().put("leader",id).put("restart",inputChanged||kind.equals("file")));
    // The capture reader requests playback only after its policy and sink are ready.
    break;
   case "_track":case "_source_play":
    boolean play=op.equals("_source_play")||body.optBoolean("play",false);if(GroupRuntime.intercom()&&play)throw new IllegalStateException("End the intercom call first");
    JSONObject selection=op.equals("_track")?music.resolve(body.getString("track")):new JSONObject().put("source",GroupPolicy.source(body.getString("source"))).put("title",body.optString("title",""));
    if(GroupRuntime.active()&&!play)group.edit().putString("pending_source",selection.getString("source")).putString("pending_title",selection.optString("title","").substring(0,Math.min(200,selection.optString("title","").length()))).apply();
    else{group.edit().putString("source",selection.getString("source")).putString("source_kind","file").putString("source_title",selection.optString("title","").substring(0,Math.min(200,selection.optString("title","").length()))).remove("pending_source").remove("pending_title").apply();if(play)session(new JSONObject().put("leader",id).put("restart",true));}break;
   case "_join":session(body);break;
   case "_media_transport":MediaActivity.transport(body.getString("kind"),body.getString("command"));break;
   case "_media_exclusive":MediaActivity.exclusive(body.getString("kind"));break;
   case "_leave":leaveLocal();break;
   case "_transport":String command=body.getString("command");if(!Arrays.asList("play","pause","stop").contains(command))throw new IllegalArgumentException("Invalid group transport");if(command.equals("play")&&GroupRuntime.service!=null&&GroupRuntime.service.sourceFinished)session(new JSONObject().put("leader",id).put("restart",true));else GroupRuntime.transport(command);break;
   case "_preset":mergePresets(new JSONArray().put(checkedPreset(body.getJSONObject("preset"))));break;
   default:throw new IllegalArgumentException("Unknown room command");
  }return new JSONObject().put("accepted",true);
 }
 private JSONObject send(String target,JSONObject command)throws Exception{if(target.equals(id))return localCommand(command);Peer p=peer(target);JSONObject result=request(p,command,credential(p));if(!result.optBoolean("accepted")||result.has("error"))throw new IOException("Room rejected command");if(Arrays.asList("room","source","_track","_source_play","_input","_leave","_join").contains(command.optString("op")))pollPeer(p);return result;}
 private JSONArray groupMembers(String leader)throws JSONException{JSONArray result=new JSONArray();JSONArray rooms=snapshot().getJSONArray("panels");for(int i=0;i<rooms.length();i++){JSONObject p=rooms.getJSONObject(i);if(p.optBoolean("active")&&leader.equals(p.optString("leader")))result.put(p.getString("id"));}return result;}
 private JSONObject results(LinkedHashMap<String,String> errors)throws JSONException{lastOperation=errors.isEmpty()?"Group command completed":"Some rooms did not accept the command";return new JSONObject().put("accepted",errors.isEmpty()).put("errors",new JSONObject(errors));}
 private void attempt(Map<String,String> errors,String target,JSONObject command){try{send(target,command);}catch(Exception failure){errors.put(target,failure instanceof SecurityException?"API token required":"Room unavailable or command rejected");}}
 public JSONObject command(JSONObject body)throws Exception{
  String op=body.getString("op");if(op.startsWith("_")||op.equals("room")||op.equals("source"))return send(body.optString("target",id),withoutTarget(body));
  if(!operation.tryLock())throw new IllegalStateException("Another group change is in progress");
  try{
   if(op.equals("media_transport")){String kind=body.getString("kind"),verb=body.getString("command");if(!Arrays.asList("airplay","bluetooth","apps","local").contains(kind)||!Arrays.asList("play","pause","previous","next").contains(verb))throw new IllegalArgumentException("Unknown media control");return send(body.getString("leader"),new JSONObject().put("op","_media_transport").put("kind",kind).put("command",verb));}
   if(op.equals("smart_play")){
    String leader=body.getString("leader"),verb=body.optString("command","play");if(!verb.equals("play")&&!verb.equals("pause"))throw new IllegalArgumentException("Choose play or pause");
    JSONArray desired=members(body.getJSONArray("members"),leader);if(!leader.equals(id))pollPeer(peer(leader));JSONObject room=find(snapshot().getJSONArray("panels"),leader);if(room==null||!room.optBoolean("online")||!room.optBoolean("authorized"))throw new IllegalStateException("Source room is unavailable");
    JSONObject playing=room.optJSONObject("nowPlaying");String input=body.optString("input","auto");if(input.equals("auto"))input=playing==null?"none":playing.optString("kind","none");
    if(input.equals("none")||input.equals("ambiguous"))throw new IllegalStateException(input.equals("ambiguous")?"More than one source is active; choose the source below":"Choose a source or select a local track first");
    if(input.equals("receiving")){if(desired.length()>1)throw new IllegalStateException("Select the current group leader to change its rooms");return send(leader,new JSONObject().put("op","_transport").put("command",verb));}
    if(input.equals("airplay")||input.equals("local")){
     if(input.equals("airplay")&&!room.optBoolean("airplayControlAvailable"))throw new IllegalStateException("Connect an AirPlay sender first");
     if(input.equals("local")&&!room.optBoolean("localPlayerAvailable"))throw new IllegalStateException("Choose a local library track or start local playback first");
     if(desired.length()>1)throw new IllegalStateException(input.equals("airplay")?"AirPlay rooms are selected on the sending device":"Use a local library track, Bluetooth or App audio to play across rooms");
     if(verb.equals("play")&&room.optBoolean("active"))command(new JSONObject().put("op","leave").put("target",leader));
     return send(leader,new JSONObject().put("op","_media_transport").put("kind",input).put("command",verb));
    }
    if(!Arrays.asList("bluetooth","apps","file").contains(input))throw new IllegalArgumentException("Unknown input");
    if(verb.equals("pause")){if(!room.optBoolean("active"))return send(leader,new JSONObject().put("op","_media_transport").put("kind",input).put("command","pause"));return send(leader,new JSONObject().put("op","_transport").put("command","pause"));}
    if(room.optBoolean("active")&&room.optString("sourceKind").equals(input)&&!(input.equals("file")&&room.optBoolean("sourcePending"))){send(leader,new JSONObject().put("op","_media_exclusive").put("kind",input));JSONObject result=command(new JSONObject().put("op","set_group").put("leader",leader).put("members",desired));send(leader,new JSONObject().put("op","_transport").put("command","play"));return result;}
    return command(new JSONObject().put("op","group_input").put("leader",leader).put("members",desired).put("input",input));
   }
   if(op.equals("group_input")){
    String leader=body.getString("leader");JSONArray desired=members(body.has("members")?body.getJSONArray("members"):groupMembers(leader),leader);for(int i=0;i<desired.length();i++)if(!desired.getString(i).equals(id))peer(desired.getString(i));send(leader,new JSONObject().put("op","_input").put("input",body.getString("input")));return command(new JSONObject().put("op","set_group").put("leader",leader).put("members",desired));
   }
   if(op.equals("select_track")){return send(body.getString("leader"),new JSONObject().put("op","_track").put("track",MusicLibrary.trackId(body.getString("track"))).put("play",false));}
   if(op.equals("play_track")||op.equals("play_source")){
    String leader=body.getString("leader");JSONArray desired=members(body.has("members")?body.getJSONArray("members"):groupMembers(leader),leader);for(int i=0;i<desired.length();i++)if(!desired.getString(i).equals(id))peer(desired.getString(i));
    JSONObject selection=new JSONObject().put("op",op.equals("play_track")?"_track":"_source_play").put("play",true);
    if(op.equals("play_track"))selection.put("track",MusicLibrary.trackId(body.getString("track")));else selection.put("source",GroupPolicy.source(body.getString("source"))).put("title",body.optString("title",""));
    send(leader,selection);return command(new JSONObject().put("op","set_group").put("leader",leader).put("members",desired));
   }
   if(op.equals("pair")){String host=GroupPolicy.host(body.getString("host"));int port=body.optInt("port",8099);if(port<1||port>65535)throw new IllegalArgumentException("Invalid API port");String token=body.optString("token","");if(token.length()>1024)throw new IllegalArgumentException("Invalid API token");
    Peer test=new Peer("","",host,port);JSONObject remote=request(test,null,token);String key=remote.getString("self");JSONObject found=find(remote.getJSONArray("panels"),key);if(found==null)throw new IOException("Invalid room directory");remember(key,found.getString("name"),host,port);prefs.edit().putString("auth_"+key,token).putString("auth_host_"+key,host+":"+port).apply();worker.execute(this::poll);return new JSONObject().put("accepted",true);}
   if(op.equals("save")||op.equals("delete")){
    JSONObject preset;if(op.equals("delete")){preset=find(snapshot().getJSONArray("presets"),body.getString("id"));if(preset==null)throw new IllegalArgumentException("Saved group not found");preset.put("deleted",true).put("updated",System.currentTimeMillis());}
    else preset=checkedPreset(new JSONObject().put("id",body.optString("id","group-"+UUID.randomUUID())).put("name",body.getString("name")).put("leader",body.getString("leader")).put("members",body.getJSONArray("members")).put("updated",System.currentTimeMillis()));
    mergePresets(new JSONArray().put(preset));JSONObject update=new JSONObject().put("op","_preset").put("preset",preset);LinkedHashMap<String,String> errors=new LinkedHashMap<>();for(Peer p:known())attempt(errors,p.id,update);lastOperation=errors.isEmpty()?"Group saved on panels":"Group saved; offline panels will synchronize later";return new JSONObject().put("accepted",true).put("pendingSync",new JSONArray(errors.keySet()));
   }
   if(op.equals("activate")){JSONObject preset=find(snapshot().getJSONArray("presets"),body.getString("id"));if(preset==null||preset.optBoolean("deleted"))throw new IllegalArgumentException("Saved group not found");body=new JSONObject(preset.toString()).put("op","set_group");op="set_group";}
   LinkedHashMap<String,String> errors=new LinkedHashMap<>();
   if(op.equals("set_group")){
    String leader=body.getString("leader");JSONArray desired=members(body.getJSONArray("members"),leader);JSONObject source=find(snapshot().getJSONArray("panels"),leader);if(source==null||!source.optBoolean("sourceConfigured"))throw new IllegalArgumentException("Choose an audio file or URL on the source panel first");
    for(int i=0;i<desired.length();i++)if(!desired.getString(i).equals(id))peer(desired.getString(i));String host=leader.equals(id)?desired.length()==1?"127.0.0.1":localAddress():peer(leader).host;
    JSONObject join=new JSONObject().put("op","_join").put("leader",leader).put("host",host);send(leader,join);
    JSONArray current=snapshot().getJSONArray("panels");for(int i=0;i<current.length();i++){JSONObject old=current.getJSONObject(i);String oldId=old.getString("id");if(!oldId.equals(leader)&&contains(desired,oldId)&&old.optBoolean("active")&&oldId.equals(old.optString("leader"))){JSONArray orphaned=groupMembers(oldId);for(int j=0;j<orphaned.length();j++)if(!contains(desired,orphaned.getString(j)))attempt(errors,orphaned.getString(j),new JSONObject().put("op","_leave"));}}
    for(int i=0;i<desired.length();i++)if(!desired.getString(i).equals(leader))attempt(errors,desired.getString(i),join);
    JSONArray previous=groupMembers(leader);for(int i=0;i<previous.length();i++)if(!contains(desired,previous.getString(i)))attempt(errors,previous.getString(i),new JSONObject().put("op","_leave"));return results(errors);
   }
   if(op.equals("leave")){String target=body.getString("target");JSONObject room=find(snapshot().getJSONArray("panels"),target);if(room==null)throw new IllegalArgumentException("Panel not found");JSONArray leaving=target.equals(room.optString("leader"))?groupMembers(target):new JSONArray().put(target);if(!contains(leaving,target))leaving.put(target);for(int i=leaving.length()-1;i>=0;i--)attempt(errors,leaving.getString(i),new JSONObject().put("op","_leave"));return results(errors);}
   if(op.equals("transport")||op.equals("group_volume")||op.equals("group_mute")){
    String leader=body.getString("leader");JSONArray targets=groupMembers(leader);if(targets.length()==0)throw new IllegalStateException("Group is not active");
    JSONObject action=new JSONObject();if(op.equals("transport")){String command=body.getString("command");if(!Arrays.asList("play","pause","stop").contains(command))throw new IllegalArgumentException("Invalid transport");if(!command.equals("stop"))return send(leader,new JSONObject().put("op","_transport").put("command",command));action.put("op","_leave");}
    else if(op.equals("group_volume")){double value=body.getDouble("volume");if(Double.isNaN(value)||Double.isInfinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid group volume");action.put("op","room").put("volume",value);}
    else action.put("op","room").put("muted",body.getBoolean("muted"));
    for(int i=0;i<targets.length();i++)attempt(errors,targets.getString(i),action);return results(errors);
   }
   throw new IllegalArgumentException("Unknown group command");
  }finally{operation.unlock();}
 }
 private static JSONObject withoutTarget(JSONObject body)throws JSONException{JSONObject copy=new JSONObject(body.toString());copy.remove("target");return copy;}
 private static boolean contains(JSONArray values,String key)throws JSONException{for(int i=0;i<values.length();i++)if(key.equals(values.getString(i)))return true;return false;}
 private String localAddress()throws Exception{String iface=ReceiverNetwork.interfaceName(context,api);NetworkInterface link=iface==null?null:NetworkInterface.getByName(iface);if(link!=null)for(InetAddress address:Collections.list(link.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress())return address.getHostAddress();throw new IOException("Selected audio network has no IPv4 address");}
 @Override public void close(){closed=true;if(instance==this)instance=null;worker.shutdownNow();main.post(()->{try{if(discovery!=null)nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}try{if(registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}});if(multicast.isHeld())multicast.release();}
}
