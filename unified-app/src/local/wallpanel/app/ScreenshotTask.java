package local.wallpanel.app;
import java.io.*;
import java.util.concurrent.*;

/** One memory-only capture/upload; root helper monitors pipe closure and owner death. */
final class ScreenshotTask implements Closeable {
    interface Listener {void finished(boolean ok,String error);}
    private volatile boolean closed;
    private volatile android.net.LocalSocket capture;
    private volatile MqttSession upload;
    private final ScheduledExecutorService deadline=Executors.newSingleThreadScheduledExecutor();
    void start(final File executable,final int owner,final PanelConfig config,final Listener listener){
        deadline.schedule(new Runnable(){public void run(){close();}},25,TimeUnit.SECONDS);
        new Thread(new Runnable(){public void run(){boolean ok=false;String error="Screenshot cancelled or timed out";
            try{
                if(closed)return;
                capture=PrivilegeChannel.open("CAPTURE "+owner+"\n");
                if(closed){close();return;}
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputStream in=capture.getInputStream();byte[] buffer=new byte[8192];int n;
                while(!closed&&(n=in.read(buffer))>=0){if(bytes.size()+n>ManagementPolicy.LIMIT)throw new IOException("Capture too large");bytes.write(buffer,0,n);}in.close();
                if(closed)return;byte[] png=bytes.toByteArray();if(!ManagementPolicy.png(png))throw new IOException("Invalid PNG");
                upload=new MqttSession(NetworkRouter.current().haSockets());String id="ws-"+config.deviceId.replace("wp_","");if(id.length()>23)id=id.substring(0,23);
                if(closed)return;upload.open(config,id,null,new String[0]);if(closed)return;
                upload.publishImage(config.mqttBase+"/device/screenshot",png);if(closed)return;upload.disconnect();ok=true;error="";
            }catch(Exception e){error="Screenshot capture/upload failed ("+e.getClass().getSimpleName()+")";}
            finally{close();deadline.shutdownNow();listener.finished(ok,error);}
        }},"wallpanel-screenshot").start();
    }
    @Override public void close(){closed=true;MqttSession session=upload;if(session!=null)session.close();android.net.LocalSocket current=capture;if(current!=null)try{current.close();}catch(IOException ignored){}}
}
