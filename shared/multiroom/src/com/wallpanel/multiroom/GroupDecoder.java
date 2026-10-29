package com.wallpanel.multiroom;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

/** Independent local-file/URL decoder. Never reads the AirPlay output or a mic. */
final class GroupDecoder implements Runnable {
 interface Sink {Socket open(int rate,int channels)throws Exception;void complete();void error(Exception error);}
 private final Context context;private final String source;private final Sink sink;
 volatile boolean closed,paused;private volatile Socket socket;
 GroupDecoder(Context c,String url,Sink target){context=c;source=GroupPolicy.source(url);sink=target;}
 void close(){closed=true;Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
 @Override public void run(){
  MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
  try{
   if(source.startsWith("content:"))extractor.setDataSource(context,Uri.parse(source),null);else extractor.setDataSource(source);
   MediaFormat input=null;String mime=null;
   for(int index=0;index<extractor.getTrackCount();index++){MediaFormat candidate=extractor.getTrackFormat(index);String type=candidate.getString(MediaFormat.KEY_MIME);if(type!=null&&type.startsWith("audio/")){extractor.selectTrack(index);input=candidate;mime=type;break;}}
   if(input==null)throw new IOException("No audio track");
   int rate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=input.getInteger(MediaFormat.KEY_CHANNEL_COUNT);GroupPolicy.format(rate,channels);
   if(closed)return;input.setInteger("pcm-encoding",AudioFormat.ENCODING_PCM_16BIT);
   decoder=MediaCodec.createDecoderByType(mime);decoder.configure(input,null,null,0);decoder.start();
   socket=sink.open(rate,channels);if(closed)return;OutputStream out=socket.getOutputStream();
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnd=false,outputEnd=false;
   while(!closed&&!outputEnd){
    if(paused){Thread.sleep(20);continue;}
    if(!inputEnd){int index=decoder.dequeueInputBuffer(10000);if(index>=0){ByteBuffer data=decoder.getInputBuffer(index);int n=extractor.readSampleData(data,0);if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}else{decoder.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
    int index=decoder.dequeueOutputBuffer(info,10000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat actual=decoder.getOutputFormat();if(actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=rate||actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=channels||(actual.containsKey("pcm-encoding")&&actual.getInteger("pcm-encoding")!=AudioFormat.ENCODING_PCM_16BIT))throw new IOException("Unexpected decoded format");}
    else if(index>=0){
     try{if(info.size>0){if(info.size%(channels*2)!=0)throw new IOException("Incomplete audio frame");ByteBuffer data=decoder.getOutputBuffer(index);data.position(info.offset);data.limit(info.offset+info.size);byte[] block=new byte[Math.min(info.size,32768)];while(data.hasRemaining()&&!closed){int n=Math.min(block.length,data.remaining());data.get(block,0,n);out.write(block,0,n);}}outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;}
     finally{decoder.releaseOutputBuffer(index,false);}
    }
   }
   if(!closed)sink.complete();
  }catch(Exception error){if(!closed)sink.error(error);}
  finally{close();if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}extractor.release();}
 }
}
