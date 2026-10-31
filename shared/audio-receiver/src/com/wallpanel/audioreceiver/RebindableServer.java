package com.wallpanel.audioreceiver;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;

/** Reopens only the listener after a network change; existing requests remain independent. */
final class RebindableServer implements AutoCloseable {
 private final int port;private ServerSocket listener;private boolean closed;
 RebindableServer(int port){this.port=port;}
 private synchronized ServerSocket current()throws IOException{
  if(closed)throw new IOException("Web service stopped");
  if(listener==null){ServerSocket next=new ServerSocket();try{next.setReuseAddress(true);next.bind(new InetSocketAddress(port));listener=next;}catch(IOException error){next.close();throw error;}}return listener;
 }
 Socket accept()throws IOException{
  for(;;){ServerSocket current=current();try{return current.accept();}catch(IOException error){synchronized(this){if(closed||listener==current)throw error;}}}
 }
 synchronized void rebind(){ServerSocket previous=listener;listener=null;if(previous!=null)try{previous.close();}catch(IOException ignored){}}
 public synchronized void close(){closed=true;rebind();}
}
