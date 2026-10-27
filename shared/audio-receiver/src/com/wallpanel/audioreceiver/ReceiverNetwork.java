package com.wallpanel.audioreceiver;

import android.content.Context;
import android.net.*;

/** Never rebinds the hosting process or changes SIP/HA network preferences. */
public final class ReceiverNetwork {
    public interface Resolver {String interfaceName();}
    private static volatile Resolver resolver;
    public static void resolver(Resolver value){resolver=value;}
    public static String interfaceName(Context context,PrefStore prefs){
        String explicit=prefs.getInterfaceName();if(!explicit.isEmpty())return explicit;
        Resolver current=resolver;if(current!=null)return current.interfaceName();
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network=cm.getActiveNetwork();LinkProperties link=network==null?null:cm.getLinkProperties(network);
        return link==null?null:link.getInterfaceName();
    }
    private ReceiverNetwork(){}
}
