package com.wallpanel.audioreceiver;

/** One output owner, with nested, idempotent call leases. No Android dependencies. */
public final class AudioOutputGate {
    public static final AudioOutputGate INSTANCE=new AudioOutputGate();
    public interface Sink {void audible(boolean value);}
    public interface Sessions {void started(long generation);void ended(long generation);}
    private Sink sink;
    private Sessions sessions;
    private boolean selected=true,stream;
    private int holds;
    private long generation;
    public synchronized void sessions(Sessions listener){sessions=listener;if(listener!=null)selected=false;apply();}
    public synchronized void bind(Sink next){sink=next;apply();}
    public synchronized void unbind(Sink previous){if(sink==previous)sink=null;}
    public synchronized void select(boolean value){selected=value;apply();}
    public synchronized boolean audible(){return selected&&holds==0;}
    public synchronized boolean active(long token){return stream&&token==generation;}
    public synchronized boolean streaming(){return stream;}
    public synchronized boolean selectSession(long token){if(!active(token))return false;selected=true;apply();return true;}
    public long beginSession(){
        Sessions listener;long token;
        synchronized(this){stream=true;token=++generation;listener=sessions;if(listener!=null)selected=false;apply();}
        if(listener!=null)listener.started(token);
        return token;
    }
    public void endSession(long token){
        Sessions listener;
        synchronized(this){if(!active(token))return;stream=false;listener=sessions;if(listener!=null)selected=false;apply();}
        if(listener!=null)listener.ended(token);
    }
    public synchronized Lease hold(){
        holds++;
        try{apply();return new Lease();}catch(RuntimeException failed){holds--;throw failed;}
    }
    private void apply(){if(sink!=null)sink.audible(audible());}
    public final class Lease implements java.io.Closeable {
        private boolean closed;
        public void close(){synchronized(AudioOutputGate.this){if(closed)return;closed=true;holds--;apply();}}
    }
}
