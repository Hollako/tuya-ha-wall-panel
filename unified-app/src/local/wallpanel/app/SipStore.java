package local.wallpanel.app;
import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** SIP credentials use the existing app Keystore; runtime diagnostics contain no credentials. */
final class SipStore {
    private final Context context;private final SharedPreferences prefs;
    private String cachedCipher;private SipConfig cached;
    SipStore(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("sip_configuration",Context.MODE_PRIVATE);}
    boolean configured(){return prefs.getString("configuration","").length()>0;}
    boolean enabled(){return new ConfigStore(context).dashboardSettings().selected.contains("sip");}
    synchronized SipConfig load()throws Exception{
        String cipher=prefs.getString("configuration","");if(cipher.length()==0)return new SipConfig();
        if(cached==null||!cipher.equals(cachedCipher)){
            JSONObject data=new JSONObject(Secrets.decrypt(cipher));SipConfig value=decode(data);String stored=cipher;
            if(!data.has("station_brand")||data.has("speaker_boost_db")){stored=Secrets.encrypt(encode(value).toString());if(!prefs.edit().putString("configuration",stored).commit())throw new IOException("Door brand migration could not be saved");}
            cached=value;cachedCipher=stored;
        }
        return cached;
    }
    void save(SipConfig c)throws Exception{c.validate();String data=Secrets.encrypt(encode(c).toString());if(!prefs.edit().putString("configuration",data).commit())throw new IOException("SIP settings could not be saved");}
    String status(){return prefs.getString("runtime_status","SIP is disabled");}
    void status(String text){prefs.edit().putString("runtime_status",text).apply();}
    static JSONObject encode(SipConfig c)throws Exception{return new JSONObject().put("v",1).put("server",c.server).put("server_port",c.serverPort).put("user",c.user).put("auth_user",c.authUser).put("password",c.password).put("local_address",c.localAddress).put("sip_port",c.sipPort).put("rtp_port",c.rtpPort).put("auto_answer",c.autoAnswer).put("ring_seconds",c.ringSeconds).put("volume",c.volume).put("aec",c.aec).put("noise_suppression",c.noiseSuppressor).put("gain_control",c.gainControl).put("ringtone_enabled",c.ringtoneEnabled).put("ring_volume",c.ringVolume).put("video_enabled",c.videoEnabled).put("video_url",c.videoUrl).put("station_brand",c.stationBrand).put("door_server",c.doorServer).put("door_user",c.doorUser).put("door_password",c.doorPassword).put("door_one_enabled",c.doorOneEnabled).put("door_two_enabled",c.doorTwoEnabled);}
    static SipConfig decode(JSONObject d)throws Exception{
        if(d.optInt("v",0)!=1)throw new IllegalArgumentException("Unknown SIP settings");SipConfig c=new SipConfig();c.server=d.getString("server");c.user=d.getString("user");c.password=d.getString("password");c.authUser=d.optString("auth_user","");c.localAddress=d.optString("local_address","");c.serverPort=d.optInt("server_port",5060);c.sipPort=d.optInt("sip_port",5060);c.rtpPort=d.optInt("rtp_port",10000);c.autoAnswer=d.optBoolean("auto_answer",false);c.ringSeconds=d.optInt("ring_seconds",45);c.volume=d.optInt("volume",75);c.aec=d.optBoolean("aec",true);c.noiseSuppressor=d.optBoolean("noise_suppression",true);c.gainControl=d.optBoolean("gain_control",true);c.ringtoneEnabled=d.optBoolean("ringtone_enabled",true);c.ringVolume=d.optInt("ring_volume",65);c.videoEnabled=d.optBoolean("video_enabled",false);c.videoUrl=d.optString("video_url","");c.doorServer=d.optString("door_server","");c.doorUser=d.optString("door_user","");c.doorPassword=d.optString("door_password","");c.stationBrand=d.optString("station_brand",c.doorServer.length()>0?"hikvision":"generic");c.doorOneEnabled=d.optBoolean("door_one_enabled",true);c.doorTwoEnabled=d.optBoolean("door_two_enabled",false);c.validate();return c;
    }
    void enabled(boolean on)throws Exception{
        synchronized(ConfigStore.class){
            SharedPreferences general=context.getSharedPreferences("wall_panel",Context.MODE_PRIVATE);Set<String> modules=new HashSet<String>(general.getStringSet("modules",new HashSet<String>()));if(modules.contains("sip")==on)return;
            if(on)modules.add("sip");else modules.remove("sip");long revision=general.getLong("revision",0);
            SharedPreferences.Editor edit=general.edit().putStringSet("modules",modules).putLong("revision",revision+1).putLong("saved_at",System.currentTimeMillis());
            String draft=general.getString("draft_payload","");
            if(draft.length()>0&&general.getLong("draft_revision",-1)==revision){JSONObject d=new JSONObject(Secrets.decrypt(draft));JSONArray old=d.getJSONArray("modules"),updated=new JSONArray();for(int i=0;i<old.length();i++)if(!old.getString(i).equals("sip"))updated.put(old.getString(i));if(on)updated.put("sip");d.put("modules",updated);edit.putString("draft_payload",Secrets.encrypt(d.toString())).putLong("draft_revision",revision+1);}
            if(!edit.commit())throw new IOException("SIP module selection could not be saved");
        }
    }
    boolean importPending()throws Exception{
        File file=new File(context.getFilesDir(),"sip-migration-import.json");if(!file.exists())return false;if(file.length()>16384)throw new IOException("Invalid SIP migration file");
        byte[] bytes=new byte[(int)file.length()];FileInputStream in=new FileInputStream(file);try{int at=0,n;while(at<bytes.length&&(n=in.read(bytes,at,bytes.length-at))>0)at+=n;if(at!=bytes.length)throw new IOException("Incomplete migration");}finally{in.close();}
        JSONObject data=new JSONObject(new String(bytes,"UTF-8"));if(data.optBoolean("disable",false))enabled(false);else if(data.has("options")){JSONObject options=data.getJSONObject("options");SipConfig c=decode(encode(load()));Iterator<String> keys=options.keys();while(keys.hasNext()){String key=keys.next();if(key.equals("video_url"))c.videoUrl=options.getString(key);else if(key.equals("video_enabled"))c.videoEnabled=options.getBoolean(key);else if(key.equals("ringtone_enabled"))c.ringtoneEnabled=options.getBoolean(key);else if(key.equals("ring_volume"))c.ringVolume=options.getInt(key);else if(key.equals("call_volume"))c.volume=options.getInt(key);else if(key.equals("station_brand"))c.stationBrand=options.getString(key);else if(key.equals("door_server"))c.doorServer=options.getString(key);else if(key.equals("door_user"))c.doorUser=options.getString(key);else if(key.equals("door_password"))c.doorPassword=options.getString(key);else if(key.equals("door_one_enabled"))c.doorOneEnabled=options.getBoolean(key);else if(key.equals("door_two_enabled"))c.doorTwoEnabled=options.getBoolean(key);else throw new IllegalArgumentException("Unsupported intercom option");}save(c);}else{SipConfig c=decode(data.getJSONObject("configuration"));save(c);enabled(data.optBoolean("enable",false));}if(!file.delete())throw new IOException("Imported; remove protected migration file");return true;
    }
}
