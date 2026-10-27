package local.wallpanel.app;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** App-owned worker lifetime; closing stdin releases its lease and permits fallback. */
final class NativeWorker implements Closeable {
    interface Listener {void message(JSONObject data);void exited(String reason);}
    private volatile android.net.LocalSocket socket;
    private volatile boolean closing,requestedStop;
    private final ArrayBlockingQueue<Request> requests=new ArrayBlockingQueue<Request>(32);
    private long sequence;
    private static final class Request {final String command;final long at=System.nanoTime();Request(String c){command=c;}}
    private static byte[] read(InputStream in,int maximum)throws IOException {
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;
            while((n=in.read(b))>=0){if(out.size()+n>maximum)throw new IOException("Input too large");out.write(b,0,n);}return out.toByteArray();
        }finally{in.close();}
    }
    private static String sha(byte[] data)throws Exception {byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format(java.util.Locale.US,"%02x",v&255));return b.toString();}
    private static void write(File file,byte[] data)throws IOException {FileOutputStream out=new FileOutputStream(file);try{out.write(data);out.getFD().sync();}finally{out.close();}}
    static File prepare(Context context,PanelConfig config)throws Exception {
        String prefix=NativeAssets.prefix(config.profile,android.os.Build.SUPPORTED_ABIS);JSONObject metadata=new JSONObject(new String(read(context.getAssets().open(prefix+"manifest.json"),8192),"UTF-8"));
        if(metadata.getInt("protocol")!=1||!config.profile.equals(metadata.getString("profile"))||!NativeAssets.abi(android.os.Build.SUPPORTED_ABIS).equals(metadata.getString("abi"))||!metadata.optBoolean("runtime_ready",false))throw new IOException("Unsupported worker package");
        byte[] binary=read(context.getAssets().open(prefix+"wallpanel-core"),1024*1024);
        if(!sha(binary).equals(metadata.getString("sha256")))throw new IOException("Worker integrity check failed");
        File parent=context.getFilesDir().getCanonicalFile();File folder=new File(parent,"g10-runtime");
        if(!folder.mkdirs()&&!folder.isDirectory())throw new IOException("Cannot prepare worker folder");
        if(!folder.getCanonicalFile().getParentFile().equals(parent))throw new IOException("Unsafe worker folder");
        File staged=new File(folder,"wallpanel-core.new");write(staged,binary);if(!staged.setExecutable(true,true))throw new IOException("Worker permission failed");
        if(!staged.renameTo(new File(folder,"wallpanel-core")))throw new IOException("Worker staging failed");
        byte[] capture=read(context.getAssets().open(prefix+"wallpanel-capture"),1024*1024);
        if(!sha(capture).equals(metadata.getString("capture_sha256")))throw new IOException("Capture helper integrity check failed");
        File captureStaged=new File(folder,"wallpanel-capture.new");write(captureStaged,capture);
        if(!captureStaged.setExecutable(true,true)||!captureStaged.renameTo(new File(folder,"wallpanel-capture")))throw new IOException("Capture helper staging failed");
        write(new File(folder,"worker-input.conf"),NativeSettings.render(config,new int[4][2]).getBytes("UTF-8"));return folder;
    }
    void start(final Context context,final PanelConfig config,final Listener listener){
        new Thread(new Runnable(){public void run(){String reason="Hardware worker stopped";
            try{
                if(!"g10".equals(config.profile))throw new IOException("Select the G10 profile first");
                File folder=prepare(context,config);if(closing)return;
                socket=PrivilegeChannel.open("WORKER\n");
                if(closing){close();return;}
                new Thread(new Runnable(){public void run(){writeCommands();}},"wallpanel-native-commands").start();
                InputStream in=socket.getInputStream();ByteArrayOutputStream line=new ByteArrayOutputStream();int c;
                while(!closing&&(c=in.read())>=0){
                    if(c=='\n'){String text=new String(line.toByteArray(),"UTF-8").trim();line.reset();if(text.length()>0){if(!text.startsWith("{")){write(new File(context.getFilesDir(),"native-error.txt"),text.getBytes("UTF-8"));throw new IOException("Launcher did not enter worker protocol");}JSONObject data=new JSONObject(text);if(data.getInt("v")!=1)throw new IOException("Worker protocol mismatch");listener.message(data);}}
                    else{if(line.size()>=2048)throw new IOException("Worker output too large");line.write(c);}
                }
                reason="Hardware worker exited";
            }catch(Exception e){reason="Hardware worker unavailable ("+e.getClass().getSimpleName()+")";}
            finally{boolean report=!requestedStop;shutdown();if(report)listener.exited(reason);}
        }},"wallpanel-native-reader").start();
    }
    boolean send(String operation){
        if(closing||socket==null||operation.length()>1100||!operation.matches("(?:status|management (?:reboot|status) 0|display (?:enabled|status|brightness|power|dim_after|off_after|dim_brightness) (?:0|[1-9][0-9]{0,4})|knob_status|audio_status|audiomodules [01] [01]|audio (?:power [01]|speakers [01]|level (?:0|[1-9][0-9]?|100))|knobmodules [01] [01]|knob (?:frame|media_frame|speaker_mute|title|value|detail|power|follow_main|activity|screensaver|ring|color|led) (?:[0-9a-f]+|-)|relay [12] [01]|bind [1-4] [0-2] [0-2]|modules [01] [01])"))return false;
        return requests.offer(new Request(operation));
    }
    private void writeCommands(){
        try{while(!closing){Request r=requests.poll(250,TimeUnit.MILLISECONDS);if(r==null)continue;
            if(System.nanoTime()-r.at>500000000L)continue; // Never replay an old action after a stalled pipe.
            if(++sequence>2147483647L)throw new IOException("Request identity exhausted");
            OutputStream out=socket.getOutputStream();out.write(("v1 "+sequence+" "+r.command+"\n").getBytes("US-ASCII"));out.flush();
        }}catch(Exception e){shutdown();}
    }
    boolean isRunning(){return !closing&&socket!=null;}
    private void shutdown(){closing=true;requests.clear();android.net.LocalSocket current=socket;socket=null;if(current!=null)try{current.close();}catch(IOException ignored){}}
    @Override public void close(){requestedStop=true;shutdown();}
}
