package local.wallpanel.app;
import android.content.*;
import android.net.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.net.SocketFactory;
import org.json.JSONObject;

/** HA owns the WebView process default; intercom sockets always use their own Network. */
final class NetworkRouter {
    interface Listener {void changed();}
    private static volatile NetworkRouter instance;
    private final Context context;private final ConnectivityManager cm;private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners=new HashSet<Listener>();private final Map<String,ConnectivityManager.NetworkCallback> holds=new HashMap<String,ConnectivityManager.NetworkCallback>();
    private volatile NetworkPolicy policy;private String haKey="",sipKey="";private Network lastHa;private boolean applying;
    static synchronized NetworkRouter get(Context c){if(instance==null)instance=new NetworkRouter(c.getApplicationContext());return instance;}
    static NetworkRouter current(){NetworkRouter r=instance;if(r==null)throw new IllegalStateException("Network routing is not initialized");return r;}
    private NetworkRouter(Context c){context=c;cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);prefs=c.getSharedPreferences("network_settings",Context.MODE_PRIVATE);policy=load();
        cm.registerNetworkCallback(new NetworkRequest.Builder().build(),new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){refresh();}@Override public void onLost(Network n){refresh();}@Override public void onLinkPropertiesChanged(Network n,LinkProperties l){refresh();}});
        if(Build.VERSION.SDK_INT>=24)cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){refresh();}@Override public void onLost(Network n){refresh();}});
        prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(SharedPreferences p,String key){refresh();}});apply();
    }
    NetworkPolicy load(){NetworkPolicy p=new NetworkPolicy();p.ha=prefs.getString("ha_network","auto");p.intercom=prefs.getString("intercom_network","auto");p.haFallback=prefs.getBoolean("ha_fallback",false);p.intercomFallback=prefs.getBoolean("intercom_fallback",false);p.validate();return p;}
    void save(NetworkPolicy p){p.validate();if(!prefs.edit().putString("ha_network",p.ha).putString("intercom_network",p.intercom).putBoolean("ha_fallback",p.haFallback).putBoolean("intercom_fallback",p.intercomFallback).commit())throw new IllegalStateException("Network choices could not be saved");apply();}
    synchronized void add(Listener l){listeners.add(l);}synchronized void remove(Listener l){listeners.remove(l);}
    private void refresh(){main.post(new Runnable(){public void run(){apply();}});}
    private boolean usable(Network n){if(n==null)return false;NetworkInfo i=cm.getNetworkInfo(n);LinkProperties p=cm.getLinkProperties(n);if(i==null||!i.isConnected()||p==null)return false;for(LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return true;return false;}
    private Network choose(String choice,boolean fallback){
        if(!"auto".equals(choice)){int transport="wifi".equals(choice)?NetworkCapabilities.TRANSPORT_WIFI:NetworkCapabilities.TRANSPORT_ETHERNET;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(transport)&&usable(n))return n;}
            if(!fallback)return null;
        }
        Network n=cm.getActiveNetwork();return usable(n)?n:null;
    }
    Network selected(boolean intercom){NetworkPolicy p=policy;return choose(intercom?p.intercom:p.ha,intercom?p.intercomFallback:p.haFallback);}
    Network require(boolean intercom)throws IOException{Network n=selected(intercom);if(n==null)throw new IOException(intercom?"Waiting for intercom network":"Waiting for HA network");return n;}
    String key(boolean intercom){Network n=selected(intercom);if(n==null)return "unavailable";try{return n.getNetworkHandle()+"/"+ipv4(n);}catch(IOException e){return "unavailable";}}
    String ipv4(Network n)throws IOException{LinkProperties p=cm.getLinkProperties(n);if(p!=null)for(LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return a.getAddress().getHostAddress();throw new IOException("Network has no IPv4 address");}
    boolean owns(Network n,String ip){LinkProperties p=cm.getLinkProperties(n);if(p!=null)for(LinkAddress a:p.getLinkAddresses())if(a.getAddress().getHostAddress().equals(ip))return true;return false;}
    InetAddress resolve(Network n,String host)throws IOException{for(InetAddress a:n.getAllByName(host))if(a instanceof Inet4Address)return a;throw new IOException("No IPv4 host address on selected network");}
    MqttSession.ConnectionProvider haSockets(){final NetworkRouter r=this;return new MqttSession.ConnectionProvider(){private Network n;public java.net.Socket create()throws IOException{n=r.require(false);return n.getSocketFactory().createSocket();}public InetAddress resolve(String host)throws IOException{if(n==null)throw new IOException("Network unavailable");return r.resolve(n,host);}};}
    SocketFactory intercomSockets()throws IOException{final Network n=require(true);return new SocketFactory(){public java.net.Socket createSocket()throws IOException{return new java.net.Socket(){ {n.bindSocket(this);} @Override public void connect(SocketAddress endpoint,int timeout)throws IOException{if(!(endpoint instanceof InetSocketAddress))throw new IOException("Invalid RTSP endpoint");InetSocketAddress target=(InetSocketAddress)endpoint;super.connect(new InetSocketAddress(resolve(n,target.getHostString()),target.getPort()),timeout>0?timeout:5000);}@Override public void connect(SocketAddress endpoint)throws IOException{connect(endpoint,5000);}};}public java.net.Socket createSocket(String h,int p)throws IOException{java.net.Socket s=createSocket();try{s.connect(new InetSocketAddress(resolve(n,h),p),5000);return s;}catch(IOException e){s.close();throw e;}}public java.net.Socket createSocket(InetAddress h,int p)throws IOException{java.net.Socket s=createSocket();try{s.connect(new InetSocketAddress(h,p),5000);return s;}catch(IOException e){s.close();throw e;}}public java.net.Socket createSocket(String h,int p,InetAddress l,int lp)throws IOException{java.net.Socket s=createSocket();try{s.bind(new InetSocketAddress(l,lp));s.connect(new InetSocketAddress(resolve(n,h),p),5000);return s;}catch(IOException e){s.close();throw e;}}public java.net.Socket createSocket(InetAddress h,int p,InetAddress l,int lp)throws IOException{java.net.Socket s=createSocket();try{s.bind(new InetSocketAddress(l,lp));s.connect(new InetSocketAddress(h,p),5000);return s;}catch(IOException e){s.close();throw e;}}};}
    private void hold(String name){if("auto".equals(name)||holds.containsKey(name))return;final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){refresh();}@Override public void onLost(Network n){refresh();}};try{cm.requestNetwork(new NetworkRequest.Builder().addTransportType("wifi".equals(name)?NetworkCapabilities.TRANSPORT_WIFI:NetworkCapabilities.TRANSPORT_ETHERNET).build(),callback);holds.put(name,callback);}catch(Exception ignored){}}
    private synchronized void apply(){if(applying)return;applying=true;try{policy=load();Set<String> needed=new HashSet<String>(Arrays.asList(policy.ha,policy.intercom));for(String old:new HashSet<String>(holds.keySet()))if(!needed.contains(old)){cm.unregisterNetworkCallback(holds.remove(old));}for(String name:needed)hold(name);
        Network ha=selected(false);boolean bound=true;
        if(ha!=null){if(lastHa==null||!lastHa.equals(ha)){bound=cm.bindProcessToNetwork(ha);if(bound)lastHa=ha;}}
        else if("auto".equals(policy.ha)&&lastHa!=null){cm.bindProcessToNetwork(null);lastHa=null;}
        // A missing explicit interface blocks new HA work; retain an old binding so
        // existing WebView sockets cannot silently migrate to the other LAN.
        String nextHa=key(false),nextSip=key(true);boolean changed=!haKey.equals(nextHa)||!sipKey.equals(nextSip);haKey=nextHa;sipKey=nextSip;record(bound);
        if(changed){Listener[] copy;synchronized(this){copy=listeners.toArray(new Listener[0]);}for(Listener l:copy)try{l.changed();}catch(Exception ignored){}}
    }finally{applying=false;}}
    private String describe(Network n){if(n==null)return "unavailable";LinkProperties lp=cm.getLinkProperties(n);try{return (lp==null?"?":lp.getInterfaceName())+" "+ipv4(n);}catch(IOException e){return "unavailable";}}
    String summary(){return "HA/dashboard: "+describe(selected(false))+"\nIntercom: "+describe(selected(true));}
    private void record(boolean bound){try{JSONObject d=new JSONObject().put("ha_choice",policy.ha).put("intercom_choice",policy.intercom).put("ha_fallback",policy.haFallback).put("intercom_fallback",policy.intercomFallback).put("ha_selected",describe(selected(false))).put("intercom_selected",describe(selected(true))).put("ha_process_bound",bound&&selected(false)!=null);FileOutputStream out=context.openFileOutput("network-runtime-status.json",Context.MODE_PRIVATE);try{out.write(d.toString().getBytes("UTF-8"));}finally{out.close();}}catch(Exception ignored){}}
}
