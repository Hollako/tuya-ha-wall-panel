package local.wallpanel.app;
import java.io.*;
import org.json.*;

/** App-side management state/scheduling. The native worker owns privileged reboot. */
final class ManagementRuntime implements Closeable {
    interface Host {boolean online();boolean owned();int workerPid();Object connection();PanelConfig config();void publish(String suffix,String payload,boolean retained);}
    private final Host host;private final File helper;
    private volatile boolean closed;private volatile JSONObject backend;
    private ScreenshotTask job;private long lastAttempt;private volatile long anchor;private int sequence;private String error="";
    ManagementRuntime(Host host,File helper){this.host=host;this.helper=helper;}
    private static long now(){return System.nanoTime()/1000000;}
    void start(){new Thread(new Runnable(){public void run(){loop();}},"wallpanel-management").start();}
    void backend(JSONObject data){backend=data;}
    synchronized void screenshot(){
        long time=now();if(closed||!host.online()||!host.owned()||job!=null||lastAttempt>0&&time-lastAttempt<5000)return;
        final ScreenshotTask request=new ScreenshotTask();job=request;lastAttempt=anchor=time;error="";
        request.start(helper,host.workerPid(),host.config(),new ScreenshotTask.Listener(){public void finished(boolean ok,String failure){synchronized(ManagementRuntime.this){
            if(job!=request||closed)return;job=null;if(ok){sequence++;error="";}else error=failure;
        }}});
    }
    private synchronized JSONObject state()throws JSONException {
        JSONObject nativeData=backend;JSONObject result=new JSONObject();result.put("protocol_version",1);result.put("owner","app");result.put("management_available",true);
        int brightness=nativeData==null?120:nativeData.optInt("brightness",120);result.put("brightness",brightness<1||brightness>255?120:brightness);
        result.put("actual_brightness",nativeData==null?0:nativeData.optInt("actual_brightness",0));result.put("display_on",nativeData!=null&&nativeData.optBoolean("display_on",false));
        result.put("reboot_pending",nativeData!=null&&nativeData.optBoolean("reboot_pending",false));result.put("screenshot_sequence",sequence);result.put("screenshot_busy",job!=null);
        result.put("error",error.length()>0?error:nativeData==null?"":nativeData.optString("error",""));return result;
    }
    private void loop(){
        boolean ownedBefore=false;Object previous=null;String sent="";
        while(!closed){
            boolean owned=host.owned();long time=now();if(owned&&!ownedBefore)anchor=time;if(!owned&&ownedBefore)cancelCapture();ownedBefore=owned;
            boolean busy;synchronized(this){busy=job!=null;}
            if(ManagementPolicy.due(time,anchor,host.online(),owned,busy))screenshot();
            if(owned&&host.online())try{String json=state().toString();Object connection=host.connection();if(connection!=previous||!json.equals(sent)){host.publish("/device/state",json,true);previous=connection;sent=json;}}catch(JSONException ignored){}
            try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
    synchronized void cancelCapture(){ScreenshotTask old=job;job=null;if(old!=null)old.close();}
    @Override public synchronized void close(){closed=true;cancelCapture();}
}
