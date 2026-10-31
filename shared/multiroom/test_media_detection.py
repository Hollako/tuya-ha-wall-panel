"""Exercise production source arbitration with simulated helper/session evidence."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
sources={
'android/content/Context.java':'''package android.content;public class Context {public static final int MODE_PRIVATE=0;public final SharedPreferences prefs=new SharedPreferences();public SharedPreferences getSharedPreferences(String n,int m){return prefs;}}''',
'android/content/SharedPreferences.java':'''package android.content;import java.util.*;public class SharedPreferences {public final Map<String,String> m=new HashMap<>();public String getString(String k,String d){return m.containsKey(k)?m.get(k):d;}}''',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
'android/os/Process.java':'package android.os;public class Process {public static int myUid(){return 10065;}}',
'android/net/LocalSocketAddress.java':'package android.net;public class LocalSocketAddress {public enum Namespace {ABSTRACT}public LocalSocketAddress(String n,Namespace v){}}',
'android/net/Credentials.java':'package android.net;public class Credentials {public int getUid(){return 1000;}}',
'android/net/LocalSocket.java':'''package android.net;import java.io.*;public class LocalSocket implements Closeable {public void connect(LocalSocketAddress a){}public Credentials getPeerCredentials(){return new Credentials();}public void setSoTimeout(int n){}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}public InputStream getInputStream(){return new ByteArrayInputStream("fixture\\n".getBytes());}public void close(){}}''',
'org/json/JSONException.java':'package org.json;public class JSONException extends Exception {public JSONException(String s){super(s);}}',
'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject {public static final Object NULL=new Object();public static JSONObject next;private final Map<String,Object> m=new HashMap<>();public JSONObject(){}public JSONObject(String value){m.putAll(next.m);}public JSONObject put(String k,Object v)throws JSONException{m.put(k,v);return this;}public String getString(String k)throws JSONException{if(!m.containsKey(k))throw new JSONException(k);return String.valueOf(m.get(k));}public String optString(String k){return optString(k,"");}public String optString(String k,String d){Object v=m.get(k);return v==null||v==NULL?d:v.toString();}public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}public JSONArray optJSONArray(String k){return (JSONArray)m.get(k);}public JSONObject optJSONObject(String k){return (JSONObject)m.get(k);}}''',
'org/json/JSONArray.java':'''package org.json;import java.util.*;public class JSONArray {private final List<Object> values=new ArrayList<>();public JSONArray put(Object v){values.add(v);return this;}public int length(){return values.size();}public JSONObject getJSONObject(int i){return (JSONObject)values.get(i);}}''',
'com/wallpanel/audioreceiver/ReceiverRemote.java':'''package com.wallpanel.audioreceiver;import java.util.*;public class ReceiverRemote {public static Map<String,Object> snapshot(){return Collections.<String,Object>singletonMap("available",false);}public static String playback(){return "playing";}public static boolean request(String verb){return true;}}''',
'com/wallpanel/audioreceiver/ReceiverState.java':'package com.wallpanel.audioreceiver;public class ReceiverState {public static final ReceiverState INSTANCE=new ReceiverState();public String getTitle(){return "AirPlay song";}public String getArtworkId(){return "";}}',
'com/wallpanel/multiroom/GroupRuntime.java':'package com.wallpanel.multiroom;public class GroupRuntime {public static boolean active,paused;public static boolean active(){return active;}public static boolean paused(){return paused;}}',
'com/wallpanel/multiroom/DetectionTest.java':'''package com.wallpanel.multiroom;import org.json.*;import android.content.*;
public class DetectionTest {
static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}static JSONObject bt(boolean connected,boolean nativeActive,boolean known)throws Exception{return new JSONObject().put("connected",connected).put("renderingKnown",known).put("rendering",nativeActive).put("playing",false);}
static JSONObject session(String kind,String state)throws Exception{return new JSONObject().put("kind",kind).put("state",state).put("title",kind+" song");}
static JSONObject sample(Context c,JSONObject bluetooth,JSONArray sessions)throws Exception{JSONObject.next=new JSONObject().put("bluetooth",bluetooth).put("sessions",sessions);android.os.SystemClock.now+=2000;return MediaActivity.snapshot(c);}
public static void main(String[] args)throws Exception{
Context c=new Context();JSONObject value=sample(c,bt(true,true,true),new JSONArray());ok(value.getString("kind").equals("bluetooth"));ok(value.getString("state").equals("playing")); // G10: no active MediaSession, native track is playing.
value=sample(c,bt(false,true,true),new JSONArray());ok(value.getString("kind").equals("bluetooth")); // Native proof remains useful before a profile callback arrives.
value=sample(c,bt(true,true,true),new JSONArray().put(session("bluetooth","paused")));ok(value.getString("state").equals("playing"));
value=sample(c,bt(true,false,true),new JSONArray().put(session("apps","playing")));ok(value.getString("kind").equals("apps")); // Connected idle Bluetooth must not steal another source.
value=sample(c,bt(true,false,true),new JSONArray().put(session("bluetooth","playing")).put(session("apps","playing")));ok(value.getString("kind").equals("apps")); // Stale BT metadata is not an actively rendering source.
value=sample(c,bt(true,false,true),new JSONArray().put(session("apps","paused")));ok(value.getString("kind").equals("none")); // Connection alone cannot choose over another paused source.
value=sample(c,bt(true,false,true),new JSONArray());ok(value.getString("kind").equals("bluetooth"));ok(value.getString("state").equals("paused"));
value=sample(c,bt(false,false,true),new JSONArray());ok(value.getString("kind").equals("none"));
value=sample(c,new JSONObject(),new JSONArray().put(session("bluetooth","playing")));ok(value.getString("kind").equals("bluetooth")); // Older helper remains compatible.
GroupRuntime.active=true;c.prefs.m.put("role","leader");c.prefs.m.put("source_kind","bluetooth");c.prefs.m.put("source_title","Stale file title");
value=sample(c,bt(true,false,true),new JSONArray().put(session("bluetooth","playing")));ok(value.getString("kind").equals("bluetooth"));ok(value.getString("state").equals("buffering"));ok(value.getString("title").equals("bluetooth song"));
GroupRuntime.paused=true;value=sample(c,bt(true,false,true),new JSONArray());ok(value.getString("state").equals("paused"));GroupRuntime.paused=false;
c.prefs.m.put("role","receiver");value=sample(c,bt(true,false,true),new JSONArray());ok(value.getString("kind").equals("receiving")); // A paired idle phone must not hide incoming group audio.
GroupRuntime.active=false;value=sample(c,bt(true,true,true),new JSONArray().put(session("apps","playing")));ok(value.getString("kind").equals("ambiguous"));
JSONObject paused=bt(true,false,true).put("remotePaused",true);value=sample(c,paused,new JSONArray().put(session("bluetooth","playing")));ok(value.getString("state").equals("paused"));
System.out.println("PASS: "+checks+" production source-detection checks: native Bluetooth, stale/missing sessions, idle/paused phones, conflicts, receiver state and stalled handoff");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='media-detection-') as folder:
 temp=Path(folder);files=[]
 for name,body in sources.items():
  path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
 files.extend(root/'shared/multiroom/src/com/wallpanel/multiroom'/name for name in ('MediaActivity.java','SourceDecision.java'))
 files.append(root/'shared/audio-receiver/src/com/wallpanel/audioreceiver/AudioOutputGate.java')
 java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,*map(str,files)],check=True)
 subprocess.run([java,'-cp',folder,'com.wallpanel.multiroom.DetectionTest'],check=True,timeout=10)
