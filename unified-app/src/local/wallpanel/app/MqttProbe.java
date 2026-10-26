package local.wallpanel.app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Explicit connection test only. No entity discovery, subscriptions or actuator messages. */
public final class MqttProbe {
    static byte[] connectPacket(PanelConfig c,String client)throws Exception {
        c.validateBroker();ByteArrayOutputStream payload=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(payload);
        utf(d,"MQTT");d.writeByte(4);int flags=2;
        if(c.username.length()>0)flags|=128;if(c.password.length()>0)flags|=64;
        d.writeByte(flags);d.writeShort(15);utf(d,client);
        if(c.username.length()>0)utf(d,c.username);if(c.password.length()>0)utf(d,c.password);
        byte[] body=payload.toByteArray();ByteArrayOutputStream packet=new ByteArrayOutputStream();packet.write(0x10);
        int n=body.length;do{int digit=n%128;n/=128;if(n>0)digit|=128;packet.write(digit);}while(n>0);
        packet.write(body);return packet.toByteArray();
    }
    private static void utf(DataOutputStream d,String s)throws Exception {
        if(s.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid MQTT string");
        byte[] bytes=s.getBytes("UTF-8");if(bytes.length>65535)throw new IllegalArgumentException("MQTT field is too long");
        d.writeShort(bytes.length);d.write(bytes);
    }
    public static String test(PanelConfig c)throws Exception{return test(c,new MqttSession.ConnectionProvider(){public Socket create(){return new Socket();}public java.net.InetAddress resolve(String h)throws java.io.IOException{return java.net.InetAddress.getByName(h);}});}
    public static String test(PanelConfig c,MqttSession.ConnectionProvider connections)throws Exception {
        byte[] connect=connectPacket(c,"wallpanel-setup-"+UUID.randomUUID().toString());Socket socket=connections.create();
        try {
            socket.connect(new InetSocketAddress(connections.resolve(c.broker),c.port),8000);socket.setSoTimeout(8000);
            if(c.tls){
                SSLSocket secure=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,c.broker,c.port,true);
                socket=secure;secure.setSoTimeout(8000);SSLParameters p=secure.getSSLParameters();p.setEndpointIdentificationAlgorithm("HTTPS");secure.setSSLParameters(p);secure.startHandshake();
            }
            socket.getOutputStream().write(connect);socket.getOutputStream().flush();InputStream in=socket.getInputStream();
            int type=in.read(),length=in.read(),ack=in.read(),code=in.read();
            if(type!=0x20||length!=2||ack!=0||code<0||code>5)throw new java.io.IOException("Invalid MQTT CONNACK");
            if(code!=0)return "Broker rejected connection (MQTT code "+code+")";
            socket.getOutputStream().write(new byte[]{(byte)0xe0,0});socket.getOutputStream().flush();
            return "MQTT login accepted. No entities or hardware commands were sent.";
        } finally {socket.close();}
    }
    private MqttProbe(){}
}
