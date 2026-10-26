package local.wallpanel.app;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
public final class RuntimeTests {
    private static int checks;
    private static void check(boolean c){checks++;if(!c)throw new AssertionError("Runtime check "+checks);}
    private static byte[] read(InputStream in,int header)throws Exception{
        check(in.read()==header);int length=0,m=1,b;do{b=in.read();if(b<0)throw new EOFException();length+=(b&127)*m;m*=128;}while((b&128)!=0);
        byte[] data=new byte[length];new DataInputStream(in).readFully(data);return data;
    }
    private static void publish(OutputStream out,int header,String topic,String payload,int id)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);d.writeUTF(topic);
        if(((header>>1)&3)==1)d.writeShort(id);d.write(payload.getBytes("UTF-8"));byte[] body=bytes.toByteArray();out.write(header);out.write(body.length);out.write(body);out.flush();
    }
    private static MqttSession.Message next(MqttSession s)throws Exception{long until=System.currentTimeMillis()+3000;MqttSession.Message m;do{m=s.next();if(m!=null)return m;Thread.sleep(5);}while(System.currentTimeMillis()<until);throw new AssertionError("Message timeout");}
    public static void main(String[] args)throws Exception {
        PanelConfig c=new PanelConfig();c.broker="127.0.0.1";c.mqttBase="wallpanel/test";c.username="test";c.password="local fake";c.selected.add("relays");c.selected.add("buttons");
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/1/set","ON",true))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/1/set","ON",false)).equals("relay 1 1"));
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/3/set","ON",false))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/1/set","TOGGLE",false))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/1/set","ON;reboot",false))==null);
        check(HardwareTopics.command(c,new MqttSession.Message("wallpanel/another/relay/1/set","ON",false))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/button/4/binding/set","1,2",false)).equals("bind 4 1 2"));
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/button/4/binding/set","1,2",true))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/button/1/binding/set","1,9",false))==null);
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/app/command/set","OPEN_SETUP",false)).equals("OPEN_SETUP"));
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/app/command/set","OPEN_SETUP",true))==null);
        for(String action:new String[]{"RESTART_APP","RELOAD_DASHBOARD"}){
            check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/app/command/set",action,false)).equals(action));
            check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/app/command/set",action,true))==null);
            check(HardwareTopics.command(c,new MqttSession.Message("wallpanel/other/app/command/set",action,false))==null);
        }
        check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/app/command/set","RESTART_APP;reboot",false))==null);
        c.selected.remove("relays");check(HardwareTopics.command(c,new MqttSession.Message(c.mqttBase+"/relay/1/set","ON",false))==null);c.selected.add("relays");
        final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));c.port=server.getLocalPort();final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        Thread broker=new Thread(new Runnable(){public void run(){try{Socket s=server.accept();s.setSoTimeout(3000);try{
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();byte[] connect=read(in,0x10);check((connect[7]&255)==0xe6);
            DataInputStream fields=new DataInputStream(new ByteArrayInputStream(connect));check(fields.readUTF().equals("MQTT"));fields.readByte();fields.readByte();check(fields.readUnsignedShort()==30);
            check(fields.readUTF().equals("test-runtime"));check(fields.readUTF().equals("wallpanel/test/app/availability"));check(fields.readUTF().equals("offline"));check(fields.readUTF().equals("test"));check(fields.readUTF().equals("local fake"));
            out.write(new byte[]{0x20,2,0,0});out.flush();byte[] sub=read(in,0x82);check(sub[0]==0&&sub[1]==1);out.write(new byte[]{(byte)0x90,3,0,1,0});out.flush();
            publish(out,0x31,"wallpanel/test/relay/1/set","ON",0);
            publish(out,0x32,"wallpanel/test/relay/2/set","OFF",7);byte[] ack=read(in,0x40);check(ack[0]==0&&ack[1]==7);
            publish(out,0x3a,"wallpanel/test/relay/2/set","OFF",7);ack=read(in,0x40);check(ack[1]==7);
            publish(out,0x30,"wallpanel/test/app/command/set","OPEN_SETUP",0);
            byte[] state=read(in,0x31);DataInputStream stateFields=new DataInputStream(new ByteArrayInputStream(state));check(stateFields.readUTF().equals("wallpanel/test/relay/1/state"));check(stateFields.readByte()=='O');
            read(in,0xe0);
        }finally{s.close();}}catch(Throwable e){failure.set(e);}finally{try{server.close();}catch(Exception ignored){}}}});broker.start();
        MqttSession session=new MqttSession();session.open(c,"test-runtime",c.mqttBase+"/app/availability",new String[]{c.mqttBase+"/relay/+/set"});
        MqttSession.Message retained=next(session);check(retained.retained);check(HardwareTopics.command(c,retained)==null);
        MqttSession.Message command=next(session);check(!command.retained&&command.payload.equals("OFF"));
        MqttSession.Message following=next(session);check(following.payload.equals("OPEN_SETUP")); // Duplicate was ACKed but not delivered.
        session.publish(c.mqttBase+"/relay/1/state","ON",true);session.disconnect();broker.join(5000);check(!broker.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());
        System.out.println("PASS: "+checks+" runtime checks: private LWT, authenticated subscribe/publish, retained rejection, QoS1 duplicate suppression, exact topic routing, disabled modules and no shell/unknown actions.");
    }
}
