package local.wallpanel.app;

import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

public final class CoreTests {
    private static int count;
    private static void check(boolean b){count++;if(!b)throw new AssertionError("Check "+count);}
    private static void badUrl(String url){boolean rejected=false;try{PanelConfig.webUrl(url,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
    private static void badConfig(PanelConfig c){boolean rejected=false;try{c.validate();}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
    private static String broker(final int code,final boolean malformed)throws Exception {
        final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
        final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        Thread peer=new Thread(new Runnable(){public void run(){
            try{Socket s=server.accept();s.setSoTimeout(3000);try{
                InputStream in=s.getInputStream();check(in.read()==0x10);int length=0,m=1,b;do{b=in.read();if(b<0)throw new EOFException();length+=(b&127)*m;m*=128;}while((b&128)!=0);
                byte[] body=new byte[length];new DataInputStream(in).readFully(body);
                check(body[0]==0&&body[1]==4&&body[6]==4);check(body[7]==(byte)0xc2); // MQTT 3.1.1, clean session, user/password.
                s.getOutputStream().write(new byte[]{0x20,(byte)(malformed?3:2),0,(byte)code});s.getOutputStream().flush();
                if(code==0&&!malformed){check(in.read()==0xe0);check(in.read()==0);check(in.read()==-1);}
            }finally{s.close();}}catch(Throwable e){failure.set(e);}finally{try{server.close();}catch(Exception ignored){}}
        }});peer.start();PanelConfig c=new PanelConfig();c.broker="127.0.0.1";c.port=server.getLocalPort();c.username="tester";c.password="test only";
        String result="";Exception problem=null;try{result=MqttProbe.test(c);}catch(Exception e){problem=e;}peer.join(5000);check(!peer.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());
        if(malformed)check(problem instanceof IOException);else if(problem!=null)throw problem;return result;
    }
    public static void main(String[] args)throws Exception {
        String original="http://172.16.1.22:8098/?device=tablet&fit=screen&kiosk&noedit#test";
        check(PanelConfig.webUrl(original,false).equals(original));check(PanelConfig.webUrl("https://ha.example:8123/dashboard",false).startsWith("https"));
        for(String url:new String[]{"","javascript:alert(1)","file:///etc/passwd","intent://test","http://user:password@host/","http://host:70000/","http://host:0/","http://host/a b","http://host\n/path"})badUrl(url);
        PanelConfig c=new PanelConfig();c.validate();c.port=0;badConfig(c);c.port=1883;c.profile="unverified-model";badConfig(c);c.profile="generic";
        c.broker="mqtt://host/path";badConfig(c);c.broker="host";c.password="password";badConfig(c);c.username="test";c.validate();
        c.selected.add("dashboard");check(!ModuleCatalog.find("dashboard").available(c,true,false));check(ModuleCatalog.find("dashboard").available(c,false,true));
        for(ModuleCatalog.Module m:ModuleCatalog.ALL)if(m.hardware){check(!m.supported("generic"));c.selected.add(m.id);c.profile="g10";check(m.available(c,true,true)==m.implemented);check(!m.available(c,false,true));check(!m.available(c,true,false));}
        c.selected.add("invented-module");badConfig(c);
        PanelConfig auth=new PanelConfig();auth.broker="localhost";auth.username="a";auth.password="b";
        byte[] packet=MqttProbe.connectPacket(auth,"test-client");check(packet[0]==0x10);check(packet[9]==(byte)0xc2);
        check(broker(0,false).contains("accepted"));check(broker(5,false).contains("rejected"));broker(0,true);
        PanelConfig hardware=new PanelConfig();hardware.deviceId="wp_example";hardware.profile="g10";hardware.selected.add("buttons");hardware.selected.add("relays");
        hardware.username="do-not-export-user";hardware.password="do-not-export-password";hardware.broker="do-not-export-host";
        int[][] bindings=new int[4][2];bindings[0][0]=1;bindings[3][1]=2;
        String nativeText=NativeSettings.render(hardware,bindings);
        check(nativeText.contains("device_id=wp_example\n"));check(nativeText.contains("buttons=1\nrelays=1\n"));
        check(nativeText.contains("button_1_single=1\n"));check(nativeText.contains("button_4_double=2\n"));check(!nativeText.contains("do-not-export"));
        if(args.length>0){FileOutputStream file=new FileOutputStream(args[0]);try{file.write(nativeText.getBytes("UTF-8"));}finally{file.close();}}
        hardware.deviceId="bad\ninjection";badConfig(hardware);
        check(HomePolicy.route("",true,null,false)==HomePolicy.SETUP);
        check(HomePolicy.route("javascript:alert(1)",true,null,false)==HomePolicy.SETUP);
        check(HomePolicy.route(original,false,null,false)==HomePolicy.SETUP);
        check(HomePolicy.route(original,true,null,false)==HomePolicy.LOAD);
        check(HomePolicy.route(original,true,original,true)==HomePolicy.KEEP);
        check(HomePolicy.route(original+"2",true,original,true)==HomePolicy.LOAD);
        check(HomePolicy.route(original,true,original,false)==HomePolicy.LOAD);
        check(HomePolicy.route(" "+original+" ",true,original,true)==HomePolicy.KEEP);
        System.out.println("PASS: "+count+" configuration, capability, MQTT encoding, fake-broker authentication/rejection/malformed-response and no-publish checks.");
    }
}
