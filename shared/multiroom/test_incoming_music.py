"""Pure production policy checks. No Bluetooth or device operations."""
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='incoming-music-') as out:
 java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',out,str(root/'shared/audio-receiver/src/com/wallpanel/audioreceiver/IncomingMusicPolicy.java'),str(root/'shared/audio-receiver/tests/IncomingMusicPolicyTest.java')],check=True)
 subprocess.run([java,'-cp',out,'com.wallpanel.audioreceiver.IncomingMusicPolicyTest'],check=True,timeout=10)
