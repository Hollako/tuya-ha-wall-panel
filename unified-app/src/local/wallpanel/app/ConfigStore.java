package local.wallpanel.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;

final class ConfigStore {
    private final SharedPreferences prefs;
    private final Context context;
    ConfigStore(Context c){context=c;prefs=c.getSharedPreferences("wall_panel",Context.MODE_PRIVATE);}
    PanelConfig dashboardSettings(){
        PanelConfig c=new PanelConfig();c.revision=prefs.getLong("revision",0);c.dashboard=prefs.getString("dashboard","");c.lock=prefs.getBoolean("lock",false);
        c.selected=new HashSet<String>(prefs.getStringSet("modules",new HashSet<String>()));return c;
    }
    PanelConfig load()throws Exception {
        PanelConfig c=new PanelConfig();c.deviceId=deviceId();c.revision=prefs.getLong("revision",0);c.dashboard=prefs.getString("dashboard","");c.haUrl=prefs.getString("ha_url","");
        c.broker=prefs.getString("broker","");c.port=prefs.getInt("port",1883);c.tls=prefs.getBoolean("tls",false);c.mqttBase=mqttBase(c.deviceId);
        c.username=prefs.getString("username","");c.password=Secrets.decrypt(prefs.getString("password",""));
        c.profile=prefs.getString("profile","generic");c.lock=prefs.getBoolean("lock",false);
        c.selected=new HashSet<String>(prefs.getStringSet("modules",new HashSet<String>()));return c;
    }
    synchronized String deviceId(){
        String id=prefs.getString("device_id","");
        if(id.length()==0){id="wp_"+java.util.UUID.randomUUID().toString().replace("-","");if(!prefs.edit().putString("device_id",id).commit())throw new IllegalStateException("Panel identity could not be saved");}
        if(!id.matches("[a-z0-9_]{1,48}"))throw new IllegalStateException("Stored panel identity is invalid");return id;
    }
    boolean hasPin(){return prefs.getString("pin","").length()>0;}
    long pinWaitMillis(){return Math.max(0,prefs.getLong("pin_retry_until",0)-System.currentTimeMillis());}
    boolean verify(String pin)throws Exception {
        if(pinWaitMillis()>0)return false;
        boolean good=Secrets.verifyPin(pin,prefs.getString("pin",""));int failures=good?0:prefs.getInt("pin_failures",0)+1;
        long until=failures>=5?System.currentTimeMillis()+30000:0;
        if(!prefs.edit().putInt("pin_failures",failures>=5?0:failures).putLong("pin_retry_until",until).commit())return false;
        return good;
    }
    boolean locked(){return prefs.getBoolean("lock",false);}
    boolean runtimeEnabled(){return prefs.getBoolean("runtime_enabled",false);}
    boolean runtimeVerified(){return prefs.getBoolean("runtime_verified",false);}
    void markRuntimeVerified(){if(!prefs.edit().putBoolean("runtime_verified",true).commit())throw new IllegalStateException("Hardware verification could not be saved");}
    void setRuntimeEnabled(boolean enabled){
        SharedPreferences.Editor change=prefs.edit().putBoolean("runtime_enabled",enabled);if(!enabled)change.putBoolean("runtime_verified",false);
        if(!change.commit())throw new IllegalStateException("Hardware startup setting could not be saved");
    }
    String runtimeStatus(){return prefs.getString("runtime_status","Hardware is stopped");}
    void runtimeStatus(String value){if(!value.equals(runtimeStatus()))prefs.edit().putString("runtime_status",value).apply();}
    private String mqttBase(String id){
        String base=prefs.getString("mqtt_base",null);if(base!=null)return base;
        base="wallpanel/"+id;java.io.File bootstrap=new java.io.File(context.getFilesDir(),"runtime-topic.txt");
        if(bootstrap.isFile()&&bootstrap.length()<256){
            try{java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(bootstrap),"UTF-8"));
                try{String proposed=reader.readLine();if(proposed!=null)base=HardwareTopics.base(proposed.trim());}finally{reader.close();}
            }catch(Exception ignored){}
        }
        if(!prefs.edit().putString("mqtt_base",base).commit())throw new IllegalStateException("MQTT identity could not be saved");return base;
    }
    long lastSavedAt(){return prefs.getLong("saved_at",0);}
    void applyDashboard(String value){
        value=DashboardConfig.validate(value);
        synchronized(ConfigStore.class){
            if(value.equals(prefs.getString("dashboard","")))return;
            if(!prefs.edit().putString("dashboard",value).putLong("revision",prefs.getLong("revision",0)+1)
                .putLong("saved_at",System.currentTimeMillis()).commit())throw new IllegalStateException("Dashboard update could not be saved");
        }
    }
    void saveDraft(String json,long revision)throws Exception {
        if(json==null||json.length()>65536)throw new IllegalArgumentException("Draft is too large");
        synchronized(ConfigStore.class){
        if(revision!=prefs.getLong("revision",0))throw new IllegalStateException("Saved setup changed. Reopen settings before editing.");
        String protectedDraft=Secrets.encrypt(json);
        if(!prefs.edit().putString("draft_payload",protectedDraft).putLong("draft_revision",prefs.getLong("revision",0)).commit())
            throw new IllegalStateException("Draft could not be saved");
        }
    }
    String loadDraft()throws Exception {
        String value=prefs.getString("draft_payload","");if(value.length()==0)return null;
        if(prefs.getLong("draft_revision",-1)!=prefs.getLong("revision",0)){clearDraft();return null;}
        return Secrets.decrypt(value);
    }
    void clearDraft(){
        if(!prefs.edit().remove("draft_payload").remove("draft_revision").commit())throw new IllegalStateException("Draft could not be cleared");
    }
    void save(PanelConfig c,String newPin)throws Exception {
        synchronized(ConfigStore.class){
        c.validate();if(!deviceId().equals(c.deviceId))throw new IllegalArgumentException("Panel identity cannot be changed here");String pin=prefs.getString("pin","");
        if(c.revision!=prefs.getLong("revision",0))throw new IllegalStateException("Saved setup changed. Reopen settings before saving.");
        if(newPin.length()>0)pin=Secrets.pinRecord(newPin);
        if(c.lock&&pin.length()==0)throw new IllegalArgumentException("Set an administrator PIN before locking navigation");
        String encrypted=Secrets.encrypt(c.password);
        if(!prefs.edit().putString("dashboard",c.dashboard).putString("ha_url",c.haUrl).putString("broker",c.broker)
            .putInt("port",c.port).putBoolean("tls",c.tls).putString("username",c.username).putString("password",encrypted).putString("mqtt_base",c.mqttBase)
            .putString("profile",c.profile).putStringSet("modules",new HashSet<String>(c.selected))
            .putBoolean("lock",c.lock).putString("pin",pin)
            .putLong("revision",prefs.getLong("revision",0)+1).putLong("saved_at",System.currentTimeMillis())
            .remove("draft_payload").remove("draft_revision").commit())throw new IllegalStateException("Settings could not be saved");
        c.revision=prefs.getLong("revision",0);
        }
    }
}
