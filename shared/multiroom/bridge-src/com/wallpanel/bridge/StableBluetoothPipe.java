package com.wallpanel.bridge;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** One fixed Bluetooth capture route. Group clients subscribe without replacing it. */
public final class StableBluetoothPipe {
 public interface Input {int read(byte[] buffer)throws Exception;void close();}
 public interface Output {void write(byte[] buffer,int size)throws Exception;void pause();}
 public interface Factory {Input open()throws Exception;}
 private final Factory factory;private final Output local;private final CountDownLatch ready=new CountDownLatch(1);
 private volatile Subscriber subscriber;private volatile Exception failure;private volatile boolean started;
 private long frames;private int opens;
 public StableBluetoothPipe(Factory f,Output o){factory=f;local=o;}
 public synchronized void start(){if(started)return;started=true;Thread t=new Thread(new Runnable(){public void run(){pump();}},"bt-permanent-pcm");t.setDaemon(true);t.start();}
 private void pump(){Input source=null;try{
  source=factory.open();synchronized(this){opens++;}ready.countDown();byte[] pcm=new byte[3840];
  while(true){int n=source.read(pcm);if(n<0||n%4!=0)throw new IOException("Bluetooth record ended");if(n==0)continue;
   Subscriber current=subscriber;
   if(current!=null&&!current.closed){local.pause();byte[] copy=new byte[n];System.arraycopy(pcm,0,copy,0,n);if(!current.queue.offer(copy))current.fail(new IOException("Group consumer stalled"));}
   else try{local.write(pcm,n);}catch(Exception outputError){local.pause();}
   synchronized(this){frames+=n/4;}
  }
 }catch(Exception error){failure=error;Subscriber current=subscriber;if(current!=null)current.fail(error);System.err.println("Bluetooth persistent capture: "+error.getClass().getSimpleName());}
 finally{ready.countDown();local.pause();if(source!=null)source.close();}}
 public Subscriber attach()throws Exception{
  if(!ready.await(8,TimeUnit.SECONDS))throw new IOException("Bluetooth route is preparing");if(failure!=null)throw new IOException("Bluetooth route unavailable",failure);
  synchronized(this){if(subscriber!=null&&!subscriber.closed)throw new IOException("Bluetooth capture already in use");Subscriber value=new Subscriber();subscriber=value;return value;}
 }
 public synchronized boolean prepared(){return opens==1&&failure==null;}
 public synchronized long frames(){return frames;}
 public synchronized int opens(){return opens;}
 public final class Subscriber {
  private final ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<byte[]>(32);private volatile boolean closed;private volatile Exception error;
  public byte[] read()throws Exception{while(!closed){byte[] data=queue.poll(300,TimeUnit.MILLISECONDS);if(data!=null)return data;if(error!=null)throw error;if(failure!=null)throw failure;}if(error!=null)throw error;return null;}
  private void fail(Exception e){error=e;close();}
  public void close(){closed=true;queue.clear();synchronized(StableBluetoothPipe.this){if(subscriber==this)subscriber=null;}}
 }
}
