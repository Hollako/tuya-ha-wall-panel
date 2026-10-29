"""Real receiver artwork identity/lifecycle checks; no Android or panel required."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='receiver-artwork-') as folder:
    temp=Path(folder);source=temp/'ArtworkHarness.java'
    source.write_text('''import com.wallpanel.audioreceiver.*;
public class ArtworkHarness {
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){ReceiverState s=new ReceiverState();check(s.getArtworkId().isEmpty());
s.setTitle("First");byte[] jpeg={-1,-40,1,2,-1,-39},rgb=new byte[19200];s.setArtwork(jpeg,rgb);String id=s.getArtworkId();
check(id.matches("[0-9a-f]{64}"));check(s.isArtworkAvailable());check(s.getKnobArtwork().length==19200);
s.setArtwork(jpeg,rgb);check(s.getArtworkId().equals(id));s.setTitle("First");check(s.isArtworkAvailable());
s.setTitle("Next without artwork");check(!s.isArtworkAvailable()&&s.getArtwork()==null&&s.getKnobArtwork()==null&&s.getArtworkId().isEmpty());
s.setAlbum("Same album");s.setArtwork(jpeg,rgb);s.setTitle("Next track, same album");check(s.isArtworkAvailable());s.setAlbum("Different album");check(!s.isArtworkAvailable());
s.setArtwork(jpeg,rgb);s.clearStreamingInfo();check(s.getArtworkId().isEmpty()&&s.getKnobArtwork()==null);s.setArtwork(jpeg,rgb);s.clearArtwork();check(!s.isArtworkAvailable());
System.out.println("PASS: receiver artwork hash, duplicate, title change, missing-art fallback and session clearing");}}
''',encoding='utf-8')
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';shared=root.parent/'shared/audio-receiver/src/com/wallpanel/audioreceiver'
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.8','-nowarn','-d',str(temp),*[str(shared/n) for n in ('State.java','ReceiverState.java')],str(source)],check=True)
    subprocess.run([java,'-cp',str(temp),'ArtworkHarness'],check=True,timeout=10)
