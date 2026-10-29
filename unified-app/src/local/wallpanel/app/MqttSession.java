package local.wallpanel.app;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.net.ssl.*;

/** Small bounded MQTT 3.1.1 session. No offline command/event queue. */
public final class MqttSession implements Closeable {
    public interface ConnectionProvider {Socket create()throws IOException;InetAddress resolve(String host)throws IOException;}
    private final ConnectionProvider connections;
    public MqttSession(){this(new ConnectionProvider(){public Socket create(){return new Socket();}public InetAddress resolve(String h)throws IOException{return InetAddress.getByName(h);}});}
    public MqttSession(ConnectionProvider provider){connections=provider;}
    public static final class Message {
        public final String topic,payload;public final boolean retained;
        Message(String t,String p,boolean r){topic=t;payload=p;retained=r;}
    }
    private volatile Socket socket;
    private InputStream in;
    private OutputStream out;
    private final Object writer=new Object();
    private volatile long writeStarted,lastSent,lastReceived;
    private long pingAt;
    private final LinkedHashMap<Integer,String> received=new LinkedHashMap<Integer,String>();
    private static long now(){return System.nanoTime()/1000000;}
    private static void utf(DataOutputStream d,String s)throws IOException {
        byte[] b=s.getBytes("UTF-8");if(s.indexOf('\0')>=0||b.length>65535)throw new IOException("Invalid MQTT field");d.writeShort(b.length);d.write(b);
    }
    private static byte[] connect(PanelConfig c,String id,String will)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        utf(d,"MQTT");d.writeByte(4);d.writeByte(2|(will!=null?4|32:0)|(c.username.length()>0?128:0)|(c.password.length()>0?64:0));d.writeShort(30);
        utf(d,id);if(will!=null){utf(d,will);utf(d,"offline");}if(c.username.length()>0)utf(d,c.username);if(c.password.length()>0)utf(d,c.password);return b.toByteArray();
    }
    public void open(PanelConfig c,String id,String will,String[] topics)throws Exception {
        c.validateBroker();Socket s=connections.create();socket=s;
        try {
            s.connect(new InetSocketAddress(connections.resolve(c.broker),c.port),5000);s.setSoTimeout(2000);
            if(c.tls){SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(s,c.broker,c.port,true);socket=tls;s=tls;
                tls.setSoTimeout(2000);SSLParameters p=tls.getSSLParameters();p.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(p);tls.startHandshake();}
            in=s.getInputStream();out=s.getOutputStream();packet(0x10,connect(c,id,will));
            byte[] ack=read(0x20);if(ack.length!=2||ack[0]!=0||ack[1]!=0)throw new IOException("MQTT login rejected");
            if(topics.length==0){lastReceived=lastSent=now();return;}
            ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeShort(1);
            for(String topic:topics){utf(d,topic);d.writeByte(0);}packet(0x82,b.toByteArray());
            byte[] sub=read(0x90);if(sub.length!=topics.length+2||sub[0]!=0||sub[1]!=1)throw new IOException("Invalid subscription acknowledgement");
            for(int i=2;i<sub.length;i++)if(sub[i]!=0)throw new IOException("Subscription rejected");
            lastReceived=lastSent=now();
        }catch(Exception e){close();throw e;}
    }
    private void packet(int header,byte[] body)throws IOException {
        packet(header,body,16384);
    }
    private void packet(int header,byte[] body,int limit)throws IOException {
        if(body.length>limit)throw new IOException("MQTT packet too large");
        synchronized(writer){
            if(socket==null||out==null)throw new IOException("MQTT disconnected");writeStarted=now();
            try{out.write(header);int n=body.length;do{int digit=n%128;n/=128;if(n>0)digit|=128;out.write(digit);}while(n>0);out.write(body);out.flush();lastSent=now();}
            finally{writeStarted=0;}
        }
    }
    public void publish(String topic,String payload,boolean retain)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);utf(d,topic);d.write(payload.getBytes("UTF-8"));packet(retain?0x31:0x30,b.toByteArray());
    }
    /** Artwork only: bounded retained thumbnail; ordinary commands keep their 16-KiB cap. */
    public void publishArtwork(String topic,String payload)throws IOException {
        byte[] data=payload.getBytes("UTF-8");
        if(!topic.endsWith("/audio/artwork")||data.length>100000)throw new IOException("Invalid artwork publication");
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);utf(d,topic);d.write(data);packet(0x31,b.toByteArray(),100512);
    }
    /** Dedicated screenshot connection only; normal input packets stay capped at 16 KiB. */
    public void publishImage(String topic,byte[] png)throws IOException {
        if(!ManagementPolicy.png(png))throw new IOException("Invalid screenshot");
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);utf(d,topic);d.writeShort(1);byte[] prefix=b.toByteArray();
        synchronized(writer){
            if(socket==null||out==null)throw new IOException("MQTT disconnected");writeStarted=now();
            try{out.write(0x32);int n=prefix.length+png.length;do{int digit=n%128;n/=128;if(n>0)digit|=128;out.write(digit);}while(n>0);out.write(prefix);out.write(png);out.flush();lastSent=now();}
            finally{writeStarted=0;}
        }
        byte[] ack=read(0x40);if(ack.length!=2||ack[0]!=0||ack[1]!=1)throw new IOException("Screenshot delivery not acknowledged");
    }
    private int byteIn()throws IOException {int b=in.read();if(b<0)throw new EOFException();return b;}
    private int header;
    private byte[] read(int wanted)throws IOException {
        header=byteIn();int length=0,m=1,n=0,digit;
        do{if(++n>4)throw new IOException("Invalid MQTT length");digit=byteIn();length+=(digit&127)*m;m*=128;}while((digit&128)!=0);
        if(length>16384||wanted>=0&&header!=wanted)throw new IOException("Unexpected MQTT packet");
        byte[] result=new byte[length];new DataInputStream(in).readFully(result);lastReceived=now();return result;
    }
    private static String text(byte[] data,int offset,int length)throws IOException {
        try{String value=Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,offset,length)).toString();
            if(value.indexOf('\0')>=0)throw new IOException("Invalid MQTT text");return value;
        }catch(CharacterCodingException e){throw new IOException("Invalid UTF-8");}
    }
    public Message next()throws IOException {
        if(socket==null)throw new IOException("MQTT disconnected");
        if(in.available()==0){
            if(pingAt>0&&now()-pingAt>45000)throw new IOException("MQTT ping timeout");
            if(now()-lastSent>15000){packet(0xc0,new byte[0]);if(pingAt==0)pingAt=now();}
            return null;
        }
        byte[] body=read(-1);int type=header>>4;
        if(type==13){if(header!=0xd0||body.length!=0)throw new IOException("Bad ping response");pingAt=0;return null;}
        if(type==14)throw new IOException("Broker disconnected");
        if(type!=3)throw new IOException("Unexpected MQTT control packet");
        if(body.length<2)throw new IOException("Invalid publication");int length=((body[0]&255)<<8)|(body[1]&255),offset=2+length;
        int qos=(header>>1)&3;if(length==0||length>255||offset>body.length||qos>1)throw new IOException("Invalid publication");
        String topic=text(body,2,length);int id=0;
        if(qos==1){if(offset+2>body.length)throw new IOException("Missing packet ID");id=((body[offset]&255)<<8)|(body[offset+1]&255);offset+=2;if(id==0)throw new IOException("Invalid packet ID");}
        String payload=text(body,offset,body.length-offset);
        if(qos==1){
            packet(0x40,new byte[]{(byte)(id>>8),(byte)id});String key=topic+"\0"+payload;
            if((header&8)!=0&&received.containsKey(Integer.valueOf(id))){if(!key.equals(received.get(Integer.valueOf(id))))throw new IOException("Conflicting duplicate");return null;}
            received.put(Integer.valueOf(id),key);if(received.size()>128)received.remove(received.keySet().iterator().next());
        }
        return new Message(topic,payload,(header&1)!=0);
    }
    public void checkWriteDeadline(){long start=writeStarted;if(start>0&&now()-start>5000)close();}
    public void disconnect(){try{packet(0xe0,new byte[0]);}catch(IOException ignored){}finally{close();}}
    @Override public void close(){Socket old=socket;socket=null;if(old!=null)try{old.close();}catch(IOException ignored){}}
}
