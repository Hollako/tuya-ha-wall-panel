package local.wallpanel.app;
import android.content.Context;
import java.net.*;
import java.io.*;
import org.json.JSONObject;
/** Explicit private setup probe: no SIP calls, MQTT messages, media PLAY or door writes. */
final class NetworkDiagnostics {
    static void run(final Context c){new Thread(new Runnable(){public void run(){JSONObject d=new JSONObject();try{
        NetworkRouter r=NetworkRouter.get(c);PanelConfig ha=new ConfigStore(c).load();SipConfig sip=new SipStore(c).load();
        android.net.Network hn=r.require(false),sn=r.require(true);d.put("ha_local",r.ipv4(hn)).put("intercom_local",r.ipv4(sn));
        try{MqttSession.ConnectionProvider factory=r.haSockets();Socket s=factory.create();try{s.connect(new InetSocketAddress(factory.resolve(ha.broker),ha.port),3000);d.put("mqtt_tcp",true);}finally{s.close();}}catch(Exception e){d.put("mqtt_tcp",false);}
        try{HttpURLConnection h=(HttpURLConnection)hn.openConnection(new URL(ha.dashboard));h.setConnectTimeout(3000);h.setReadTimeout(3000);h.setInstanceFollowRedirects(false);h.setRequestMethod("HEAD");try{d.put("dashboard_http",h.getResponseCode());}finally{h.disconnect();}}catch(Exception e){d.put("dashboard_http",0);}
        if(sip.videoEnabled)try{URI uri=new URI(sip.videoUrl);int port=uri.getPort()>0?uri.getPort():554;Socket s=r.intercomSockets().createSocket();try{s.connect(new InetSocketAddress(uri.getHost(),port),3000);s.setSoTimeout(3000);String target=new URI(uri.getScheme(),null,uri.getHost(),port,uri.getPath(),uri.getQuery(),null).toASCIIString();s.getOutputStream().write(("OPTIONS "+target+" RTSP/1.0\r\nCSeq: 1\r\n\r\n").getBytes("UTF-8"));String line=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8")).readLine();d.put("rtsp_options",line!=null&&line.matches("RTSP/1\\.[01] [0-9]{3}.*")?line.substring(0,12):"unavailable");d.put("rtsp_local",s.getLocalAddress().getHostAddress());}finally{s.close();}}catch(Exception e){d.put("rtsp_options","unavailable");}
        d.put("complete",true);
    }catch(Exception e){try{d.put("complete",false).put("error",e.getClass().getSimpleName());}catch(Exception ignored){}}try{FileOutputStream out=c.openFileOutput("network-probe-result.json",Context.MODE_PRIVATE);try{out.write(d.toString().getBytes("UTF-8"));}finally{out.close();}}catch(Exception ignored){}}},"wallpanel-network-probe").start();}
}
