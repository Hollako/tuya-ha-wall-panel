"""Production native-track parser and reconnect policy, using real firmware dump fixtures."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
test='''package com.wallpanel.bridge;import java.nio.file.*;
public class HandoffTest {
static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
static BluetoothRecoveryPolicy guard(){return new BluetoothRecoveryPolicy(1000);}
static boolean poll(BluetoothRecoveryPolicy p,long t,boolean current,boolean peer,boolean play,boolean hold,boolean paused,boolean known,boolean active,boolean signal){return p.observe(t,current,peer,play,hold,paused,known,active,signal);}
public static void main(String[] args)throws Exception{
String g10=new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"),a133=new String(Files.readAllBytes(Paths.get(args[1])),"UTF-8");
ok(PlaybackTracks.inspect(g10,new int[]{2119}).known);ok(PlaybackTracks.inspect(g10,new int[]{2119}).active);
ok(PlaybackTracks.inspect(a133,new int[]{596}).known);ok(!PlaybackTracks.inspect(a133,new int[]{596}).active);
ok(!PlaybackTracks.inspect(g10,new int[]{394}).active);ok(!PlaybackTracks.inspect("permission denied",new int[]{2119}).known);ok(!PlaybackTracks.inspect(g10,new int[0]).known);
ok(!PlaybackTracks.inspect("Output thread X\\n Active Client Session\\n Local log:\\n 12 yes 2119 123 A \\n",new int[]{2119}).active);
ok(!PlaybackTracks.inspect("Output thread X\\n Active Client Session\\n 2 yes 2119 409 A 0x001 00000001 00000003 44100 0 0 0\\n",new int[]{2119}).active);
ok(!PlaybackTracks.inspect("Output thread X\\n Active Client Session\\n 2 yes 2119 unknown vendor format\\n",new int[]{2119}).known);
BluetoothRecoveryPolicy p=guard();ok(!poll(p,1100,true,true,true,false,false,true,false,false));ok(!poll(p,2800,true,true,true,false,false,true,false,false));ok(poll(p,3100,true,true,true,false,false,true,false,false));
for(int i=0;i<100;i++)ok(!poll(p,3200+i,true,true,true,false,false,true,false,false));ok(p.attempted());
// No reconnect from silence alone, unknown native status, paused phone, intercom,
// another phone, stale lease, user pause, or after the startup window expires.
for(int mode=0;mode<9;mode++){p=guard();if(mode==8)p.cancel();for(long now=1100;now<17000;now+=500)ok(!poll(p,now,mode!=0,mode!=1,mode!=2,mode==3,mode==4,mode!=5,mode==6,mode==7));}
p=guard();ok(!poll(p,18000,true,true,true,false,false,true,false,false));
p=guard();ok(!poll(p,2500,true,true,true,false,false,true,false,false));ok(!poll(p,3000,true,true,true,false,false,true,true,false));ok(!poll(p,3500,true,true,true,false,false,true,false,false));ok(poll(p,4300,true,true,true,false,false,true,false,false));
BluetoothRecoveryPolicy.Budget b=new BluetoothRecoveryPolicy.Budget();ok(b.claim("phone-a","group",1000));ok(!b.claim("phone-a","group",2000));ok(b.claim("phone-a","local",2000));ok(b.claim("phone-a","group",31000));
System.out.println("PASS: "+checks+" handoff checks: real G10/A133 track evidence, bounded recovery, silence/mute, pause/call/cancel guards and per-direction cooldown");
}}'''
with tempfile.TemporaryDirectory(prefix='bt-handoff-') as folder:
    temp=Path(folder);source=temp/'HandoffTest.java';source.write_text(test)
    production=root/'shared/multiroom/bridge-src/com/wallpanel/bridge'
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,str(production/'PlaybackTracks.java'),str(production/'BluetoothRecoveryPolicy.java'),str(source)],check=True)
    fixtures=root/'shared/multiroom/tests'
    subprocess.run([java,'-cp',folder,'com.wallpanel.bridge.HandoffTest',str(fixtures/'g10-output-tracks.txt'),str(fixtures/'a133-output-tracks.txt')],check=True,timeout=10)
