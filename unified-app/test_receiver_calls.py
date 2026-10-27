"""Real SIP audio lease + shared output gate; fake Android only, never plays sound."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
sources={
'android/content/Context.java':'''package android.content;public class Context {public static final String AUDIO_SERVICE="audio";public Object getSystemService(String s){return android.media.AudioManager.INSTANCE;}}''',
'android/media/AudioManager.java':'''package android.media;public class AudioManager {
public static final AudioManager INSTANCE=new AudioManager();
public static final int STREAM_VOICE_CALL=0,STREAM_RING=2,AUDIOFOCUS_GAIN_TRANSIENT=2,MODE_RINGTONE=1,MODE_IN_COMMUNICATION=3;
public int mode=0,volume=3;public boolean speaker;
public interface OnAudioFocusChangeListener{void onAudioFocusChange(int n);}
public int getMode(){return mode;}public boolean isSpeakerphoneOn(){return speaker;}public int getStreamVolume(int n){return volume;}
public int getStreamMaxVolume(int n){return 10;}public void setMode(int n){mode=n;}public void setSpeakerphoneOn(boolean b){speaker=b;}
public void setStreamVolume(int s,int n,int f){volume=n;}public int requestAudioFocus(OnAudioFocusChangeListener l,int s,int gain){return 1;}
public int abandonAudioFocus(OnAudioFocusChangeListener l){return 1;}}''',
'local/wallpanel/app/VendorAudio.java':'''package local.wallpanel.app;class VendorAudio {static boolean aux=true;static boolean aux(){return aux;}static void source(boolean b){aux=b;}}''',
'local/wallpanel/app/BridgeService.java':'''package local.wallpanel.app;class BridgeService {static boolean fail;static Boolean beginIntercom(){if(com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.audible())throw new AssertionError("Receiver audible before ringing");if(fail)throw new IllegalStateException("route failure");return Boolean.TRUE;}static void endIntercom(Boolean b){}}''',
'local/wallpanel/app/ReceiverRuntime.java':'''package local.wallpanel.app;class ReceiverRuntime {static int returns;static void afterCall(){returns++;}}''',
'local/wallpanel/app/SipConfig.java':'''package local.wallpanel.app;class SipConfig {int volume=100;}''',
'local/wallpanel/app/ReceiverCallHarness.java':'''package local.wallpanel.app;
import com.wallpanel.audioreceiver.AudioOutputGate;
public class ReceiverCallHarness {
static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
public static void main(String[] args)throws Exception{
final AudioOutputGate g=AudioOutputGate.INSTANCE;final boolean[] audible={true};
g.bind(new AudioOutputGate.Sink(){public void audible(boolean value){audible[0]=value;}});
SipAudioLease call=new SipAudioLease(new android.content.Context());
check(!audible[0]);check(IntercomSession.active);check(!VendorAudio.aux);
call.answer(new SipConfig());check(!audible[0]);check(android.media.AudioManager.INSTANCE.volume==10);
long session=g.beginSession();g.selectSession(session);check(!audible[0]);
call.close();check(audible[0]);check(!IntercomSession.active);check(VendorAudio.aux);check(android.media.AudioManager.INSTANCE.mode==0);check(android.media.AudioManager.INSTANCE.volume==3);
call.close();check(audible[0]);check(ReceiverRuntime.returns==1);
g.select(false);SipAudioLease paused=new SipAudioLease(new android.content.Context());paused.close();check(!audible[0]);
g.select(true);BridgeService.fail=true;boolean rejected=false;try{new SipAudioLease(new android.content.Context());}catch(IllegalStateException expected){rejected=true;}
check(rejected);check(audible[0]);check(!IntercomSession.active);check(VendorAudio.aux);
System.out.println("PASS: "+checks+" real SIP lease checks: silence before routing/ringing, answer, new stream during call, route restoration, preserved pause, idempotent close and failed-acquisition cleanup");
}}'''
}
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='receiver-call-') as folder:
    temp=Path(folder);files=[]
    for name,body in sources.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    production=[root/'src/local/wallpanel/app/SipAudioLease.java',root/'src/local/wallpanel/app/IntercomSession.java',root.parent/'shared/audio-receiver/src/com/wallpanel/audioreceiver/AudioOutputGate.java']
    out=temp/'classes';out.mkdir()
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',str(out),*[str(p) for p in production+files]],check=True)
    subprocess.run([java,'-cp',str(out),'local.wallpanel.app.ReceiverCallHarness'],check=True,timeout=10)
