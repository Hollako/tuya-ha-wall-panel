package local.wallpanel.app;
import android.content.Context;
import android.net.LocalSocket;
import java.io.*;
import org.json.JSONObject;

/** Dashboard-scoped socket lease. No root requests, shell or permanent system changes. */
final class SystemUiGuard implements Closeable {
    private static SystemUiGuard administratorLease;
    private volatile boolean closed;
    private volatile LocalSocket socket;
    interface Listener {void ready(boolean value);}
    private final Context context;private final String appPackage;private final Listener listener;
    SystemUiGuard(Context c){this(c,null,null);}
    SystemUiGuard(Context c,String target,Listener callback){context=c.getApplicationContext();appPackage=target;listener=callback;}
    static synchronized void holdForAdministrator(SystemUiGuard guard){if(administratorLease!=null&&administratorLease!=guard)administratorLease.close();administratorLease=guard;}
    static synchronized SystemUiGuard takeAdministratorLease(){SystemUiGuard guard=administratorLease;administratorLease=null;return guard;}
    static synchronized void administratorAuthenticated(){SystemUiGuard guard=takeAdministratorLease();if(guard!=null)guard.close();}
    void start(){new Thread(new Runnable(){public void run(){
        int delay=1500;while(!closed){LocalSocket current=null;try{
            current=PrivilegeChannel.open("UI_GUARD\n");socket=current;if(closed)break;
            current.setSoTimeout(15000);InputStream in=current.getInputStream();ByteArrayOutputStream line=new ByteArrayOutputStream();int b;
            while((b=in.read())>=0&&b!='\n'){if(line.size()>1024)throw new IOException("Invalid guard reply");line.write(b);}
            JSONObject result=new JSONObject(new String(line.toByteArray(),"UTF-8"));
            if(!result.optBoolean("ready",false))throw new IOException("Guard unavailable");
            if(appPackage!=null){
                AppModePolicy.packageName(appPackage);
                if(!result.optBoolean("app_guard",false))throw new IOException("App guard upgrade required");
                current.getOutputStream().write(("APP "+appPackage+"\n").getBytes("US-ASCII"));current.getOutputStream().flush();
                line.reset();while((b=in.read())>=0&&b!='\n'){if(line.size()>1024)throw new IOException("Invalid app guard reply");line.write(b);}
                JSONObject ack=new JSONObject(new String(line.toByteArray(),"UTF-8"));
                if(!ack.optBoolean("ready",false)||!ack.optBoolean("app_guard",false))throw new IOException("App guard unavailable");
            }
            record(true);if(listener!=null)listener.ready(true);delay=1500;current.setSoTimeout(0);while(!closed&&in.read()!=-1){}
            if(!closed)throw new IOException("Guard lease ended");
        }catch(Exception ignored){if(!closed){record(false);if(listener!=null)listener.ready(false);}}finally{release(current);if(socket==current)socket=null;}
        if(!closed)try{Thread.sleep(delay);delay=Math.min(15000,delay*2);}catch(InterruptedException ignored){}
        }
    }},"wallpanel-system-ui").start();}
    private void record(boolean ready){try{FileOutputStream out=context.openFileOutput("ui-guard-status.json",Context.MODE_PRIVATE);try{out.write(("{\"ready\":"+ready+",\"scope\":\"dashboard\"}").getBytes("UTF-8"));}finally{out.close();}}catch(IOException ignored){}}
    private static void release(LocalSocket s){if(s==null)return;try{s.shutdownOutput();}catch(IOException ignored){}try{s.shutdownInput();}catch(IOException ignored){}try{s.close();}catch(IOException ignored){}}
    public void close(){closed=true;release(socket);socket=null;}
}
