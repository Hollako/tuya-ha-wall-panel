package com.wallpanel.multiroom;
import android.content.Context;
import android.net.*;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.util.*;
import com.wallpanel.audioreceiver.*;

/** Shared now-playing detection. Observes sessions; never starts capture during discovery. */
public final class MediaActivity {
 public interface Local {JSONObject snapshot();void transport(String verb)throws Exception;}
 private static Local local;private static volatile JSONObject cached=new JSONObject();private static long checked;
 public static void local(Local value){local=value;}
 private static String request(String command)throws Exception{
  try(LocalSocket socket=new LocalSocket()){socket.connect(new LocalSocketAddress("wallpanel.btgroup."+android.os.Process.myUid(),LocalSocketAddress.Namespace.ABSTRACT));if(socket.getPeerCredentials().getUid()!=1000)throw new IOException("Untrusted media bridge");socket.setSoTimeout(700);socket.getOutputStream().write((command+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();ByteArrayOutputStream result=new ByteArrayOutputStream();InputStream in=socket.getInputStream();for(int i=0;i<8192;i++){int next=in.read();if(next<0)throw new IOException("Closed media bridge");if(next=='\n')return result.toString("UTF-8");result.write(next);}throw new IOException("Media status too large");}
 }
 private static synchronized JSONObject probe(){long now=SystemClock.elapsedRealtime();if(now-checked<1500)return cached;checked=now;try{cached=new JSONObject(request("MEDIA_STATUS"));}catch(Exception error){cached=new JSONObject();}return cached;}
 public static JSONObject bluetoothEvidence(){JSONObject value=probe().optJSONObject("bluetooth");return value==null?new JSONObject():value;}
 static boolean bluetoothStalled(){JSONObject value=bluetoothEvidence();return value.optBoolean("connected")&&value.optBoolean("renderingKnown")&&!value.optBoolean("rendering");}
 public static JSONObject snapshot(Context context)throws JSONException{
  JSONArray candidates=new JSONArray();LinkedHashMap<String,JSONObject> playing=new LinkedHashMap<>();JSONObject info=probe();JSONArray sessions=info.optJSONArray("sessions");
  if(sessions!=null)for(int i=0;i<sessions.length();i++){JSONObject session=sessions.getJSONObject(i),previous=playing.get(session.getString("kind"));if(previous==null||session.optString("state").equals("playing"))playing.put(session.getString("kind"),session);}
  JSONObject bt=info.optJSONObject("bluetooth");boolean connected=bt!=null&&(bt.optBoolean("connected")||bt.optBoolean("rendering")),stalled=connected&&bt.optBoolean("renderingKnown")&&!bt.optBoolean("rendering");
  if(connected){JSONObject previous=playing.get("bluetooth");String state=bt.optBoolean("rendering")||bt.optBoolean("playing")?"playing":previous==null?"paused":previous.optString("state","paused");if(bt.optBoolean("remotePaused")&&!bt.optBoolean("rendering"))state="paused";if(stalled&&state.equals("playing"))state="buffering";playing.put("bluetooth",new JSONObject().put("kind","bluetooth").put("state",state).put("title",previous==null?"Bluetooth audio":previous.optString("title","Bluetooth audio")));}
  SharedPreferencesView group=new SharedPreferencesView(context);if(GroupRuntime.active()&&group.leader){JSONObject previous=playing.get(group.kind);String title=previous==null?group.title:previous.optString("title",group.title);playing.put(group.kind,new JSONObject().put("kind",group.kind).put("title",title).put("state",GroupRuntime.paused()?"paused":group.kind.equals("bluetooth")&&stalled?"buffering":"playing"));}
  if(AudioOutputGate.INSTANCE.streaming()&&AudioOutputGate.INSTANCE.audible()||Boolean.TRUE.equals(ReceiverRemote.snapshot().get("available"))&&ReceiverRemote.playback().equals("paused"))playing.put("airplay",new JSONObject().put("kind","airplay").put("title",ReceiverState.INSTANCE.getTitle()).put("state",ReceiverRemote.playback()));
  if(!GroupRuntime.active()&&local!=null){JSONObject own=local.snapshot();if(own!=null&&own.optBoolean("itemAvailable")&&Arrays.asList("playing","buffering","paused").contains(own.optString("state"))&&!own.optString("source").equals("AirPlay"))playing.put("local",new JSONObject().put("kind","local").put("title",own.optString("title",own.optString("source"))).put("state",own.optString("state")));}
  for(JSONObject value:playing.values())candidates.put(value);
  Map<String,Boolean> states=new LinkedHashMap<>();for(JSONObject value:playing.values())states.put(value.getString("kind"),!value.optString("state").equals("paused")&&!(value.getString("kind").equals("bluetooth")&&stalled));String kind=SourceDecision.choose(states,GroupRuntime.active()&&group.leader?group.kind:null);JSONObject selected=playing.get(kind);
  if(GroupRuntime.active()&&!group.leader&&(selected==null||!Boolean.TRUE.equals(states.get(kind)))){kind="receiving";selected=new JSONObject().put("state",GroupRuntime.paused()?"paused":"playing").put("title","Incoming group audio");}
  return new JSONObject().put("kind",kind).put("state",selected==null?"idle":selected.optString("state","idle")).put("title",selected==null?"":selected.optString("title","")).put("choices",candidates).put("artworkId",kind.equals("airplay")?ReceiverState.INSTANCE.getArtworkId():"");
 }
 public static boolean localAvailable(){JSONObject value=local==null?null:local.snapshot();return value!=null&&value.optBoolean("itemAvailable")&&!value.optString("source").equals("AirPlay")&&Arrays.asList("playing","buffering","paused").contains(value.optString("state"));}
 public static void transport(String kind,String verb)throws Exception{
  if(!Arrays.asList("play","pause","previous","next").contains(verb))throw new IllegalArgumentException("Unknown media command");
  if(kind.equals("airplay")){if(!ReceiverRemote.request(verb))throw new IllegalStateException("The AirPlay sender did not accept this command");}
  else if(kind.equals("local")){JSONObject playing=local==null?null:local.snapshot();if(playing==null||!playing.optBoolean("itemAvailable")||!Arrays.asList("playing","buffering","paused").contains(playing.optString("state")))throw new IllegalStateException("No local player item is selected; choose a library track or start playback first");local.transport(verb);}
  else if(kind.equals("bluetooth")||kind.equals("apps")){if(!request("MEDIA_"+verb.toUpperCase(Locale.US)+"_"+(kind.equals("apps")?"APPS":"BT")).equals("OK MEDIA"))throw new IllegalStateException("Source controls unavailable");}
  else throw new IllegalArgumentException("Choose a playback source");
 }
 public static void exclusive(String kind)throws Exception{if(!Arrays.asList("file","bluetooth","apps").contains(kind))throw new IllegalArgumentException("Unknown source");JSONArray sessions=probe().optJSONArray("sessions");if(sessions==null)return;Set<String> paused=new HashSet<>();for(int i=0;i<sessions.length();i++){JSONObject session=sessions.getJSONObject(i);String other=session.optString("kind");if(!kind.equals(other)&&session.optString("state").equals("playing")&&paused.add(other))transport(other,"pause");}}
 private static final class SharedPreferencesView {final String kind,title;final boolean leader;SharedPreferencesView(Context c){android.content.SharedPreferences p=c.getSharedPreferences("wallpanel_groups",Context.MODE_PRIVATE);kind=p.getString("source_kind","file");title=p.getString("source_title","");leader=p.getString("role","receiver").equals("leader");}}
 private MediaActivity(){}
}
