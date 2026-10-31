"""Exercise the actual standalone Bluetooth output with a simulated AudioTrack."""
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[2]
sources={
'android/media/AudioManager.java':'''package android.media;public class AudioManager {public static final int MODE_NORMAL=0,STREAM_MUSIC=3;public int mode;public int getMode(){return mode;}}''',
'android/media/AudioFormat.java':'''package android.media;public class AudioFormat {public static final int CHANNEL_OUT_STEREO=12,ENCODING_PCM_16BIT=2;}''',
'android/media/AudioTrack.java':'''package android.media;import java.util.*;public class AudioTrack {public static final int MODE_STREAM=1,STATE_INITIALIZED=1,SUCCESS=0;public static int created,sets,plays,pauses;public static float volume=-1;public static boolean fail;public static byte[] pcm;public AudioTrack(int a,int b,int c,int d,int e,int f){created++;}public static int getMinBufferSize(int a,int b,int c){return 4096;}public int getState(){return 1;}public int setVolume(float v){sets++;volume=v;return 0;}public void play(){plays++;}public void pause(){pauses++;}public void flush(){}public void release(){}public int write(byte[] b,int offset,int count){if(fail){fail=false;return -1;}pcm=Arrays.copyOfRange(b,offset,offset+count);return count;}}''',
'com/wallpanel/bridge/LocalVolumeTest.java':'''package com.wallpanel.bridge;import android.media.*;import java.util.*;public class LocalVolumeTest {static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}static void close(double a,double b){ok(Math.abs(a-b)<.00001);}public static void main(String[] args)throws Exception{AudioManager m=new AudioManager();BluetoothLocalOutput out=new BluetoothLocalOutput(m);byte[] pcm={0,64,0,-64};byte[] original=pcm.clone();out.write(pcm,4);close(AudioTrack.volume,0);ok(AudioTrack.created==1);for(double gain:new double[]{.2,.4,.6,.8,1,0}){BluetoothLocalOutput.gain(gain);out.write(pcm,4);close(AudioTrack.volume,gain);ok(AudioTrack.created==1);ok(Arrays.equals(pcm,original));ok(Arrays.equals(AudioTrack.pcm,original));}int sets=AudioTrack.sets;out.write(pcm,4);ok(AudioTrack.sets==sets);BluetoothLocalOutput.gain(.4);for(double invalid:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY})BluetoothLocalOutput.gain(invalid);out.write(pcm,4);close(AudioTrack.volume,.4);m.mode=2;int plays=AudioTrack.plays;out.write(pcm,4);ok(AudioTrack.plays==plays&&AudioTrack.pauses>0);m.mode=0;out.write(pcm,4);ok(AudioTrack.plays==plays+1);AudioTrack.fail=true;try{out.write(pcm,4);throw new AssertionError("write failure accepted");}catch(java.io.IOException expected){}out.write(pcm,4);ok(AudioTrack.created==2);close(AudioTrack.volume,.4);System.out.println("PASS: "+checks+" Bluetooth local-volume checks: fine gain, mute, invalid values, no PCM mutation/group attenuation, call hold and output recreation");}}'''
}
with tempfile.TemporaryDirectory(prefix='bt-local-volume-') as folder:
    files=[]
    for name,body in sources.items():
        path=Path(folder)/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(str(path))
    for name in ('BluetoothLocalOutput.java','StableBluetoothPipe.java'):files.append(str(root/'shared/multiroom/bridge-src/com/wallpanel/bridge'/name))
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,*files],check=True)
    subprocess.run([java,'-cp',folder,'com.wallpanel.bridge.LocalVolumeTest'],check=True,timeout=10)
