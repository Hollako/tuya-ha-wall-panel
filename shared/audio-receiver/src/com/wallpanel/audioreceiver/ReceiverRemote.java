package com.wallpanel.audioreceiver;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared source controller. No root, TCP server, automatic command replay or local mute. */
public final class ReceiverRemote {
    private static Endpoint current;
    private static RemoteProtocol.Status status;
    private static long seen;
    private static String lastCommand="",result="",error="";
    private static long sequence;
    private static final class Endpoint {
        final String socketName;
        final ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(1);
        final AtomicInteger pending=new AtomicInteger();
        volatile boolean closed;volatile LocalSocket peer;
        Endpoint(String name){socketName=name;executor.setRemoveOnCancelPolicy(true);}
        void close(){closed=true;LocalSocket socket=peer;if(socket!=null)try{socket.close();}catch(IOException ignored){}executor.shutdownNow();}
        String exchange(String request,int timeout)throws IOException{
            if(closed)throw new IOException("Receiver stopped");
            LocalSocket socket=new LocalSocket();peer=socket;
            try{
                socket.connect(new LocalSocketAddress(socketName,LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(timeout);
                if(closed||socket.getPeerCredentials().getUid()!=android.os.Process.myUid())throw new IOException("Receiver peer unavailable");
                socket.getOutputStream().write((request+"\n").getBytes("US-ASCII"));socket.getOutputStream().flush();
                StringBuilder reply=new StringBuilder();InputStream in=socket.getInputStream();
                for(int i=0;i<96;i++){int value=in.read();if(value<0)throw new IOException("Receiver control closed");if(value=='\n')return reply.toString();if(value<32||value>126)throw new IOException("Invalid control response");reply.append((char)value);}
                throw new IOException("Control response too long");
            }finally{peer=null;socket.close();}
        }
    }
    public static synchronized void attach(String name){
        if(current!=null)current.close();final Endpoint endpoint=new Endpoint(name);current=endpoint;status=null;seen=0;lastCommand="";result="";error="";
        endpoint.executor.scheduleWithFixedDelay(()->poll(endpoint),0,750,TimeUnit.MILLISECONDS);
    }
    public static synchronized void detach(String name){
        if(current!=null&&current.socketName.equals(name)){current.close();current=null;status=null;seen=0;}
    }
    private static void poll(Endpoint endpoint){
        try{
            RemoteProtocol.Status next=RemoteProtocol.status(endpoint.exchange("STATUS",1000));
            synchronized(ReceiverRemote.class){if(current!=endpoint)return;if(next!=null&&(status==null||next.session!=status.session)){lastCommand="";result="";error="";}status=next;seen=System.nanoTime();}
        }catch(IOException failed){synchronized(ReceiverRemote.class){if(current==endpoint){status=null;seen=0;}}}
    }
    public static synchronized boolean available(){return current!=null&&status!=null&&System.nanoTime()-seen<3_000_000_000L;}
    public static synchronized String playback(){return available()?status.playback:"unknown";}
    public static synchronized String lastError(){return error;}
    public static boolean request(String command){
        final String verb=RemoteProtocol.verb(command);final Endpoint endpoint;final long session,id,at=System.nanoTime();
        synchronized(ReceiverRemote.class){
            if(!available()){error="No controllable AirPlay 2 source is connected";result="unavailable";return false;}
            endpoint=current;session=status.session;
            if(endpoint.pending.incrementAndGet()>4){endpoint.pending.decrementAndGet();error="Too many source control commands";result="busy";return false;}
            id=++sequence;lastCommand=command;result="pending";error="";
        }
        try{endpoint.executor.execute(()->{
            try{
                synchronized(ReceiverRemote.class){if(current!=endpoint||endpoint.closed)return;}
                if(System.nanoTime()-at>1_500_000_000L)throw new IOException("Source control command expired");
                String reply=endpoint.exchange(verb+" "+session,7500);
                synchronized(ReceiverRemote.class){if(current==endpoint&&sequence==id){boolean ack=RemoteProtocol.acknowledged(reply,session);result=ack?"acknowledged":"failed";error=ack?"":RemoteProtocol.error(reply);}}
            }catch(IOException failed){synchronized(ReceiverRemote.class){if(current==endpoint&&sequence==id){result="failed";error="AirPlay source control failed or timed out";}}}
            finally{endpoint.pending.decrementAndGet();}
            poll(endpoint); // Read real source state; ACK alone is not playback feedback.
        });return true;}
        catch(RejectedExecutionException stopped){endpoint.pending.decrementAndGet();synchronized(ReceiverRemote.class){if(current==endpoint&&sequence==id){result="unavailable";error="AirPlay receiver stopped";}}return false;}
    }
    public static synchronized Map<String,Object> snapshot(){
        Map<String,Object> data=new LinkedHashMap<>();data.put("available",available());data.put("playback",playback());data.put("session",available()?status.session:null);
        data.put("command",lastCommand);data.put("result",result);data.put("error",error);data.put("scope","source_group");return data;
    }
    private ReceiverRemote(){}
}
