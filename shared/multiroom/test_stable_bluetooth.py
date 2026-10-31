"""Production persistent PCM pipe: no Android device or real audio output."""
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[2]
source=r'''package com.wallpanel.bridge;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class StablePipeTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static final class Source implements StableBluetoothPipe.Input {final BlockingQueue<byte[]> frames=new LinkedBlockingQueue<byte[]>();final AtomicInteger closed=new AtomicInteger();public int read(byte[] data)throws Exception{byte[] v=frames.take();System.arraycopy(v,0,data,0,v.length);return v.length;}public void close(){closed.incrementAndGet();}void send(int value){frames.add(new byte[]{(byte)value,0,(byte)value,0});}}
 static final class Output implements StableBluetoothPipe.Output {final BlockingQueue<Integer> samples=new LinkedBlockingQueue<Integer>();public void write(byte[] b,int n){samples.add((int)b[0]);}public void pause(){}}
 public static void main(String[] args)throws Exception{
  final Source source=new Source();final Output out=new Output();final AtomicInteger opens=new AtomicInteger();StableBluetoothPipe pipe=new StableBluetoothPipe(new StableBluetoothPipe.Factory(){public StableBluetoothPipe.Input open(){opens.incrementAndGet();return source;}},out);pipe.start();pipe.start();
  source.send(1);ok(out.samples.poll(2,TimeUnit.SECONDS)==1);
  for(int n=0;n<10;n++){StableBluetoothPipe.Subscriber s=pipe.attach();source.send(2);ok(s.read()[0]==2);ok(out.samples.isEmpty());boolean busy=false;try{pipe.attach();}catch(Exception expected){busy=true;}ok(busy);s.close();ok(s.read()==null);source.send(3);ok(out.samples.poll(2,TimeUnit.SECONDS)==3);ok(opens.get()==1&&source.closed.get()==0&&pipe.prepared());}
  StableBluetoothPipe.Subscriber slow=pipe.attach();for(int i=0;i<34;i++)source.send(4);ok(out.samples.poll(2,TimeUnit.SECONDS)==4);boolean failed=false;try{slow.read();}catch(Exception expected){failed=true;}ok(failed);ok(opens.get()==1&&source.closed.get()==0);ok(pipe.opens()==1&&pipe.frames()>=55);
  System.out.println("PASS: "+checks+" persistent Bluetooth checks: one policy lifetime, local/group handover, no duplicate local output, repeated detach/attach and bounded stalled consumers");
 }
}'''
with tempfile.TemporaryDirectory(prefix='stable-bt-') as folder:
 test=Path(folder)/'StablePipeTest.java';test.write_text(source)
 java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,str(root/'shared/multiroom/bridge-src/com/wallpanel/bridge/StableBluetoothPipe.java'),str(test)],check=True)
 subprocess.run([java,'-cp',folder,'com.wallpanel.bridge.StablePipeTest'],check=True,timeout=15)
