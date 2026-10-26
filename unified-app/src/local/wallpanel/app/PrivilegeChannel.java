package local.wallpanel.app;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
/** Root-owned socket accepts this app UID and two fixed operations only. */
final class PrivilegeChannel {
    static LocalSocket open(String request)throws IOException {
        if(!request.equals("WORKER\n")&&!request.equals("UI_GUARD\n")&&!request.matches("CAPTURE [1-9][0-9]{0,7}\n")&&!request.matches("SETUP (CHECK|APPLY|RESTORE)\n"))throw new IOException("Invalid privilege operation");
        LocalSocket socket=new LocalSocket();try{socket.connect(new LocalSocketAddress("/data/local/wallpanel/control.sock",LocalSocketAddress.Namespace.FILESYSTEM));socket.getOutputStream().write(request.getBytes("US-ASCII"));socket.getOutputStream().flush();return socket;}
        catch(IOException e){socket.close();throw e;}
    }
    private PrivilegeChannel(){}
}
