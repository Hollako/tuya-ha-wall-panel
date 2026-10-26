package local.wallpanel.app;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
public final class ManagementTests {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static byte[] packet(InputStream in,int type)throws Exception{
        check(in.read()==type);int n=0,m=1,d;do{d=in.read();check(d>=0);n+=(d&127)*m;m*=128;}while((d&128)!=0);
        byte[] result=new byte[n];new DataInputStream(in).readFully(result);return result;
    }
    static byte[] png(int length){byte[] result=new byte[length];int[] a={137,80,78,71,13,10,26,10},b={0,0,0,0,73,69,78,68,174,66,96,130};for(int i=0;i<a.length;i++)result[i]=(byte)a[i];for(int i=0;i<b.length;i++)result[length-b.length+i]=(byte)b[i];return result;}
    public static void main(String[] args)throws Exception{
        PanelConfig c=new PanelConfig();c.broker="127.0.0.1";c.username="local";c.password="test only";c.mqttBase="wallpanel/test";
        check(ManagementPolicy.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set","REBOOT",false)).equals("management reboot 0"));
        for(String action:new String[]{"REBOOT","SCREENSHOT"})check(ManagementPolicy.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set",action,true))==null);
        check(ManagementPolicy.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set","REBOOT;id",false))==null);
        check(!ManagementPolicy.due(299999,0,true,true,false));check(ManagementPolicy.due(300000,0,true,true,false));
        check(!ManagementPolicy.due(300000,0,false,true,false));check(!ManagementPolicy.due(300000,0,true,false,false));check(!ManagementPolicy.due(300000,0,true,true,true));
        check(!ManagementPolicy.png(null));check(!ManagementPolicy.png(new byte[40]));check(!ManagementPolicy.png(png(31)));check(!ManagementPolicy.png(png(ManagementPolicy.LIMIT+1)));
        final byte[] image=png(100000);check(ManagementPolicy.png(image));check(!ManagementPolicy.png(Arrays.copyOf(image,image.length-1)));
        for(final boolean wrongAck:new boolean[]{false,true}){
            final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));c.port=server.getLocalPort();final AtomicReference<Throwable> error=new AtomicReference<Throwable>();
            Thread broker=new Thread(new Runnable(){public void run(){try{Socket s=server.accept();try{s.setSoTimeout(3000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                byte[] connect=packet(in,0x10);check((connect[7]&255)==0xc2);out.write(new byte[]{0x20,2,0,0});out.flush();
                byte[] publication=packet(in,0x32);DataInputStream fields=new DataInputStream(new ByteArrayInputStream(publication));check(fields.readUTF().equals("wallpanel/test/device/screenshot"));check(fields.readUnsignedShort()==1);
                byte[] bytes=new byte[fields.available()];fields.readFully(bytes);check(Arrays.equals(image,bytes));out.write(new byte[]{0x40,2,0,(byte)(wrongAck?2:1)});out.flush();
                if(!wrongAck)packet(in,0xe0);
            }finally{s.close();}}catch(Throwable e){error.set(e);}finally{try{server.close();}catch(Exception ignored){}}}});broker.start();
            MqttSession session=new MqttSession();session.open(c,"ws-test",null,new String[0]);boolean failed=false;try{session.publishImage(c.mqttBase+"/device/screenshot",image);}catch(IOException expected){failed=true;}
            check(failed==wrongAck);if(!failed)session.disconnect();else session.close();broker.join(5000);check(!broker.isAlive());if(error.get()!=null)throw new AssertionError(error.get());
        }
        System.out.println("PASS: screenshot timing/ownership, strict PNG bounds, retained reboot rejection, isolated large binary QoS1 upload, no LWT/subscription and PUBACK validation. Local fake broker only.");
    }
}
