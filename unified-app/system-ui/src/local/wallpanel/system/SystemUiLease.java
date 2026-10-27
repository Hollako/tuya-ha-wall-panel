package local.wallpanel.system;
import android.os.Binder;
import android.os.IBinder;
import java.io.*;
import java.lang.reflect.*;

/** Root-only, fixed System UI policy leased to one authenticated dashboard socket. */
public final class SystemUiLease {
    private static final String OWNER="local.wallpanel.app.dashboard";
    // Android 8: expansion, notifications, system icons, Home/Back/Recents and search.
    private static final int PRIMARY=0x03ff0000,SECONDARY=0x7;
    public static void main(String[] args)throws Exception {
        try{hold();}catch(Exception e){Throwable cause=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;System.out.println("{\"v\":1,\"type\":\"ui_guard\",\"ready\":false,\"error\":\""+cause.getClass().getSimpleName()+"\"}");System.out.flush();}
    }
    private static void hold()throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root required");
        AppTaskGuard.restoreStale();
        final IBinder token=new Binder();
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"statusbar");
        if(binder==null)throw new IOException("Status bar unavailable");
        Class<?> api=Class.forName("com.android.internal.statusbar.IStatusBarService");
        Object service=Class.forName("com.android.internal.statusbar.IStatusBarService$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Method disable=api.getMethod("disable",int.class,IBinder.class,String.class);
        Method disable2=api.getMethod("disable2",int.class,IBinder.class,String.class);
        AppTaskGuard app=null;
        try {
            disable.invoke(service,PRIMARY,token,OWNER);
            disable2.invoke(service,SECONDARY,token,OWNER);
            api.getMethod("collapsePanels").invoke(service);
            System.out.println("{\"v\":1,\"type\":\"ui_guard\",\"ready\":true,\"app_guard\":true}");System.out.flush();
            BufferedReader input=new BufferedReader(new InputStreamReader(new FileInputStream(FileDescriptor.in),"US-ASCII"));
            String line;
            while((line=input.readLine())!=null){
                if(line.length()>200||!line.startsWith("APP ")||app!=null)throw new IOException("Invalid app policy request");
                app=new AppTaskGuard(line.substring(4));app.start();
                System.out.println("{\"v\":1,\"ready\":true,\"app_guard\":true}");System.out.flush();
            }
        } finally {
            try{if(app!=null)app.close();}finally{
                try{disable2.invoke(service,0,token,OWNER);}finally{disable.invoke(service,0,token,OWNER);}
            }
        }
    }
}
