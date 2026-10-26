package local.wallpanel.app;

import android.app.Application;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

/** The board controls used by Tuya itself, independent of the ALSA codec. */
final class VendorAudio {
    private static Method post;
    static void init(Application app){
        try{
            Class<?> bus=Class.forName("com.sznaner.nrmessage.NRMessage");
            bus.getMethod("init",Application.class).invoke(null,app);
            post=bus.getMethod("post",String.class,String.class);
        }catch(Exception ignored){post=null;}
    }
    static String read(String node)throws IOException{
        FileInputStream in=new FileInputStream("/proc/vendor/"+node);
        try{byte[] bytes=new byte[64];int n=in.read(bytes);if(n<=0)throw new IOException("Empty board state");return new String(bytes,0,n,"UTF-8").trim();}
        finally{in.close();}
    }
    static void write(String node,String value)throws IOException{
        FileOutputStream out=new FileOutputStream("/proc/vendor/"+node);
        try{out.write(value.getBytes("UTF-8"));out.flush();}finally{out.close();}
        if(!value.equals(read(node)))throw new IOException("Board state did not change");
    }
    static boolean aux()throws IOException{
        String value=read("aux_mode");
        if(!value.equals("aux")&&!value.equals("local"))throw new IOException("Unknown board source");
        return value.equals("aux");
    }
    static void source(boolean aux)throws IOException{
        String value=aux?"aux":"local";write("aux_mode",value);
        // Notify the vendor UI through its existing local message bus.
        if(post!=null)try{
            post.invoke(null,"com.sznaner.callback.change.aux",value);
            post.invoke(null,"com.sznaner.auxchange",value);
            post.invoke(null,"com.sznaner.noti.auxChange",value);
        }catch(Exception ignored){}
    }
    static boolean external()throws IOException{
        String value=read("horn_mode");
        if(!value.equals("on")&&!value.equals("off"))throw new IOException("Unknown speaker state");
        return value.equals("on");
    }
    static void external(boolean enabled)throws IOException{write("horn_mode",enabled?"on":"off");}
}
