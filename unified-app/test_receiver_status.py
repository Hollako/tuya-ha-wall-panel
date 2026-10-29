"""Shared status output matches the dedicated app's embedded status fields."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='receiver-status-') as folder:
    temp=Path(folder);source=temp/'StatusHarness.java'
    source.write_text('''import com.wallpanel.audioreceiver.*;
public class StatusHarness {
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){ReceiverState s=new ReceiverState();String out=ReceiverStatusText.format(s);
check(out.contains("State: DISABLED"));check(out.contains("Uptime: 0s"));check(!out.contains("--- Streaming ---"));
s.setState(State.STREAMING);s.setPlaying(true);s.setRootAvailable(true);s.setMulticastLockHeld(true);s.setLocalIp("172.16.1.26");s.setTitle("Song");s.setArtist("Artist");s.setOutputUnderruns(2);s.incDriftCorrections();s.incRestarts();
java.util.Map<String,Object> timing=new java.util.HashMap<>();timing.put("clock","audio");timing.put("timestampValid",true);timing.put("underruns",3);s.setAudioTiming(timing);out=ReceiverStatusText.format(s);
for(String required:new String[]{"State: STREAMING","IP: 172.16.1.26","PTP lease: \\u2713","Multicast Lock: \\u2713","Title: Song","Artist: Artist","Underruns: 2","Drift Corrections: 1","Restarts: 1","Output underruns: 3","Playback timing: Audio timestamp available"})check(out.contains(required));
s.setErrorMessage("Network unavailable");check(ReceiverStatusText.format(s).contains("Error: Network unavailable"));
System.out.println("PASS: shared embedded status, PTP, IP, stream metadata, timing, uptime, counters and errors");}}
''',encoding='utf-8')
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';shared=root.parent/'shared/audio-receiver/src/com/wallpanel/audioreceiver'
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.8','-nowarn','-d',str(temp),*[str(shared/n) for n in ('State.java','ReceiverState.java','ReceiverStatusText.java')],str(source)],check=True)
    subprocess.run([java,'-cp',str(temp),'StatusHarness'],check=True,timeout=10)
