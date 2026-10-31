package com.wallpanel.bridge;
import android.media.*;
/** Ordinary music output, UID1000: explicitly excluded from Bluetooth and app capture. */
final class BluetoothLocalOutput implements StableBluetoothPipe.Output {
 private static volatile float gain;
 private final AudioManager manager;private AudioTrack track;private boolean playing;private int silent;private float applied=-1;
 static void gain(double value){if(!Double.isNaN(value)&&!Double.isInfinite(value)&&value>=0&&value<=1)gain=(float)value;}
 static float gain(){return gain;}
 BluetoothLocalOutput(AudioManager m){manager=m;}
 public synchronized void write(byte[] pcm,int count)throws Exception{
  try{render(pcm,count);}catch(Exception failure){if(track!=null){try{track.release();}catch(Exception ignored){}track=null;}playing=false;applied=-1;throw failure;}
 }
 private void render(byte[] pcm,int count)throws Exception{
  if(manager.getMode()!=AudioManager.MODE_NORMAL){pause();return;}
  boolean sound=false;for(int i=0;i+1<count;i+=2)if(Math.abs((short)((pcm[i]&255)|(pcm[i+1]<<8)))>16){sound=true;break;}
  if(sound)silent=0;else silent++;
  if(silent>25){pause();return;}if(!sound&&!playing)return;
  if(track==null){int size=Math.max(15360,AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT));track=new AudioTrack(AudioManager.STREAM_MUSIC,48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,size,AudioTrack.MODE_STREAM);if(track.getState()!=AudioTrack.STATE_INITIALIZED){track.release();track=null;throw new java.io.IOException("Local Bluetooth output unavailable");}}
  float wanted=gain;if(wanted!=applied){if(track.setVolume(wanted)!=AudioTrack.SUCCESS)throw new java.io.IOException("Local Bluetooth volume unavailable");applied=wanted;}
  if(!playing){track.play();playing=true;}
  int offset=0;while(offset<count){int n=track.write(pcm,offset,count-offset);if(n<=0)throw new java.io.IOException("Local Bluetooth output stopped");offset+=n;}
 }
 public synchronized void pause(){if(track!=null&&playing){try{track.pause();track.flush();}catch(Exception ignored){}playing=false;}}
}
