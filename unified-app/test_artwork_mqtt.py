"""Actual MQTT encoder with an in-memory output, never a network connection."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='art-mqtt-') as folder:
    temp=Path(folder);source=temp/'ArtworkMqttHarness.java'
    source.write_text('''package local.wallpanel.app;
import java.io.*;import java.net.*;import java.lang.reflect.*;
public class ArtworkMqttHarness {
static void set(Object object,String name,Object value)throws Exception{Field field=MqttSession.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
public static void main(String[] args)throws Exception{MqttSession session=new MqttSession();ByteArrayOutputStream output=new ByteArrayOutputStream();set(session,"socket",new Socket());set(session,"out",output);
String image=new String(new char[20000]).replace((char)0,'a');boolean failed=false;try{session.publish("test/audio/state",image,true);}catch(IOException expected){failed=true;}if(!failed||output.size()!=0)throw new AssertionError("Control packet limit changed");
session.publishArtwork("test/audio/artwork",image);byte[] packet=output.toByteArray();if((packet[0]&255)!=0x31||packet.length<20000)throw new AssertionError("Missing retained image");
int at=1,length=0,m=1,n;do{n=packet[at++]&255;length+=(n&127)*m;m*=128;}while((n&128)!=0);if(length!=packet.length-at)throw new AssertionError("Wrong MQTT framing");
for(String topic:new String[]{"test/audio/state","test/audio/command/set"}){failed=false;try{session.publishArtwork(topic,image);}catch(IOException expected){failed=true;}if(!failed)throw new AssertionError("Wrong artwork channel accepted");}
failed=false;try{session.publishArtwork("test/audio/artwork",new String(new char[100001]));}catch(IOException expected){failed=true;}if(!failed)throw new AssertionError("Artwork limit not enforced");session.close();
System.out.println("PASS: retained thumbnail packet framing, large artwork path and unchanged command limits");}}
''',encoding='utf-8')
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';prod=root/'src/local/wallpanel/app'
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.8','-nowarn','-d',str(temp),*[str(prod/n) for n in ('MqttSession.java','PanelConfig.java','ModuleCatalog.java','ManagementPolicy.java','HardwareTopics.java')],str(source)],check=True)
    subprocess.run([java,'-cp',str(temp),'local.wallpanel.app.ArtworkMqttHarness'],check=True,timeout=10)
