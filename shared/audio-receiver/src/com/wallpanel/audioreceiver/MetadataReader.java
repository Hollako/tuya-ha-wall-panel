package com.wallpanel.audioreceiver;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.*;
import java.util.regex.*;

public class MetadataReader implements Runnable {
  private static final String TAG = "MetadataReader";
  private final File metadataFifo;
  private final ReceiverState state;
  private volatile boolean running = true;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public MetadataReader(File metadataFifo, ReceiverState state) {
    this.metadataFifo = metadataFifo;
    this.state = state;
  }

  public void stop() {
    running = false;
  }

  @Override
  public void run() {
    Log.i(TAG, "MetadataReader started");
    while (running) {
      java.io.FileDescriptor fd=null;
      try {
        fd=android.system.Os.open(metadataFifo.toString(),android.system.OsConstants.O_RDONLY|android.system.OsConstants.O_NONBLOCK,0);
        byte[] buf=new byte[4096];StringBuilder pending=new StringBuilder();
        while(running){
          int count;
          try{count=android.system.Os.read(fd,buf,0,buf.length);}catch(android.system.ErrnoException e){if(e.errno!=android.system.OsConstants.EAGAIN)throw e;Thread.sleep(20);continue;}
          if(count<=0){Thread.sleep(20);continue;}
          pending.append(new String(buf,0,count,java.nio.charset.StandardCharsets.US_ASCII));
          int end;while((end=pending.indexOf("</item>"))>=0){String xml=pending.substring(0,end+7);pending.delete(0,end+7);try{processItem(xml);}catch(Exception e){Log.w(TAG,"Invalid metadata",e);}}
          if(pending.length()>8*1024*1024)pending.setLength(0);
        }
      }catch(Exception e){if(running)Log.w(TAG,"Metadata reader",e);}
      finally{if(fd!=null)try{android.system.Os.close(fd);}catch(Exception ignored){}}
      if(running)try{Thread.sleep(100);}catch(InterruptedException e){break;}
    }
    Log.i(TAG, "MetadataReader stopped");
  }

  private void post(Runnable update){mainHandler.post(()->{if(running)update.run();});}

  private void processItem(String xml) {
    Pattern pType = Pattern.compile("<type>([0-9a-fA-F]+)</type>");
    Pattern pCode = Pattern.compile("<code>([0-9a-fA-F]+)</code>");
    Pattern pData = Pattern.compile("<data encoding=\"base64\">(.*?)</data>", Pattern.DOTALL);

    Matcher mType = pType.matcher(xml);
    Matcher mCode = pCode.matcher(xml);
    Matcher mData = pData.matcher(xml);

    if (!mType.find() || !mCode.find()) return;

    long typeVal = Long.parseLong(mType.group(1), 16);
    long codeVal = Long.parseLong(mCode.group(1), 16);
    String typeStr = new String(new byte[]{(byte)(typeVal>>24), (byte)(typeVal>>16), (byte)(typeVal>>8), (byte)typeVal});
    String codeStr = new String(new byte[]{(byte)(codeVal>>24), (byte)(codeVal>>16), (byte)(codeVal>>8), (byte)codeVal});

    byte[] binary = null;
    String data = null;
    if (mData.find()) {
      String b64 = mData.group(1).replaceAll("\\s+", "");
      binary=Base64.decode(b64, Base64.DEFAULT);
      data = new String(binary, java.nio.charset.StandardCharsets.UTF_8);
    }

    if ("PICT".equals(codeStr)) { final byte[] art=binary; post(()->state.setArtwork(art)); return; }
    dispatch(typeStr, codeStr, data);
  }

  private void dispatch(String type, String code, String data) {
    if ("ssnc".equals(type)) {
      switch (code) {
        case "prsm":
        case "pbeg":
          post(() -> state.setPlaying(true));
          break;
        case "paus":
        case "pend":
          post(() -> state.setPlaying(false));
          break;
        case "pfls":
          post(() -> state.clearStreamingInfo());
          break;
        case "clip":
          post(() -> state.setClientIp(data));
          break;
        case "cmod":
          post(() -> state.setClientModel(data));
          break;
        case "PICT":
          if (data != null) {
            byte[] img = Base64.decode(data.replaceAll("\\s+", ""), Base64.DEFAULT);
            post(() -> state.setArtwork(img));
          }
          break;
        case "pcen":
          // pcen marks completion of an artwork transfer; keep the picture.
          break;
        case "odsc":
          post(() -> state.setOutputDescription(data));
          break;
      }
    } else if ("core".equals(type)) {
      switch (code) {
        case "minm":
          post(() -> state.setTitle(data));
          break;
        case "asar":
          post(() -> state.setArtist(data));
          break;
        case "albn":
          post(() -> state.setAlbum(data));
          break;
      }
    }
  }
}
