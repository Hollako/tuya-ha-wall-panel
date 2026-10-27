package local.wallpanel.system;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.provider.Settings;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/** Root-owned, package-scoped foreground recovery for one live UI lease. */
public final class AppTaskGuard implements Closeable {
    private final Context context;private final ActivityManager activities;private final String target;
    private final String previous,policy;private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private boolean closed;private long nextLaunch;private int retry=2000;
    public AppTaskGuard(String name)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root required");
        target=AppTaskPolicy.target(name);
        if(Looper.myLooper()==null)Looper.prepare();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        activities=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(context.getPackageManager().getLaunchIntentForPackage(target)==null)throw new IOException("App unavailable");
        previous=Settings.Global.getString(context.getContentResolver(),"policy_control");policy=AppTaskPolicy.immersive(previous,target);
    }
    private static File stateFile(){return new File(System.getProperty("wallpanel.policy.state","/data/local/wallpanel/app-mode-policy"));}
    public static void restoreStale()throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root required");
        File file=stateFile();if(!file.exists())return;
        if(file.length()>16384||!file.getCanonicalPath().equals(file.getAbsolutePath()))throw new IOException("Invalid policy backup");
        String before,applied;DataInputStream in=new DataInputStream(new FileInputStream(file));
        try{before=in.readBoolean()?in.readUTF():null;applied=in.readUTF();if(in.read()!=-1)throw new IOException("Invalid policy backup");}finally{in.close();}
        if(Looper.myLooper()==null)Looper.prepare();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        if(applied.equals(Settings.Global.getString(context.getContentResolver(),"policy_control")))Settings.Global.putString(context.getContentResolver(),"policy_control",before);
        file.delete();
    }
    private void savePolicy()throws Exception {
        File file=stateFile(),temp=new File(file.getPath()+".new");
        if(!temp.getCanonicalPath().equals(temp.getAbsolutePath()))throw new IOException("Unsafe policy backup");
        FileOutputStream raw=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(raw);
        try{out.writeBoolean(previous!=null);if(previous!=null)out.writeUTF(previous);out.writeUTF(policy);out.flush();raw.getFD().sync();}finally{out.close();}
        temp.setReadable(false,false);temp.setWritable(false,false);temp.setReadable(true,true);temp.setWritable(true,true);
        if(!temp.renameTo(file))throw new IOException("Cannot save policy backup");
    }
    public void start()throws Exception {
        savePolicy();
        if(!Settings.Global.putString(context.getContentResolver(),"policy_control",policy))throw new IOException("Fullscreen policy unavailable");
        timer.scheduleWithFixedDelay(new Runnable(){public void run(){tick();}},500,500,TimeUnit.MILLISECONDS);
    }
    private synchronized void tick(){
        if(closed)return;
        try{
            List<ActivityManager.RunningTaskInfo> tasks=activities.getRunningTasks(1);
            if(tasks.isEmpty()||tasks.get(0).topActivity==null)return;
            if(AppTaskPolicy.allowed(tasks.get(0).topActivity.getPackageName(),target)){retry=2000;return;}
            long now=android.os.SystemClock.elapsedRealtime();if(now<nextLaunch)return;
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(target);
            if(launch==null){nextLaunch=now+30000;return;}
            nextLaunch=now+retry;retry=Math.min(30000,retry*2);
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }catch(Exception ignored){nextLaunch=android.os.SystemClock.elapsedRealtime()+5000;}
    }
    public synchronized void close(){
        if(closed)return;closed=true;timer.shutdownNow();
        if(policy.equals(Settings.Global.getString(context.getContentResolver(),"policy_control")))Settings.Global.putString(context.getContentResolver(),"policy_control",previous);
        stateFile().delete();
    }
}
