package local.wallpanel.app;

import android.content.Context;
import com.wallpanel.audioreceiver.PrefStore;
import com.wallpanel.audioreceiver.ReceiverNetwork;
import java.net.*;
import java.util.Enumeration;
import org.json.JSONObject;

/** Address announcement only. Never includes the API token or changes routing. */
final class GroupEndpoint {
    static String payload(Context context)throws Exception{
        PrefStore prefs=ReceiverRuntime.settings(context);
        String id=context.getSharedPreferences("wallpanel_groups",0).getString("id","");
        if(!id.matches("panel-[A-Za-z0-9-]{8,80}"))return null;
        String iface=ReceiverNetwork.interfaceName(context,prefs);
        NetworkInterface network=iface==null?null:NetworkInterface.getByName(iface);
        if(network==null||!network.isUp())return null;
        Enumeration<InetAddress> addresses=network.getInetAddresses();
        while(addresses.hasMoreElements()){
            InetAddress address=addresses.nextElement();
            if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&!address.isLinkLocalAddress()&&!address.isAnyLocalAddress())
                return new JSONObject().put("v",1).put("id",id).put("host",address.getHostAddress()).put("port",prefs.getHaPort()).toString();
        }
        return null;
    }
    private GroupEndpoint(){}
}
