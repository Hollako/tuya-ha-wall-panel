"""Pure loopback camera regression. Never contacts the panel or real door station."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parent
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
with tempfile.TemporaryDirectory(dir=r/'test-classes',prefix='video-transport-') as d:
    src=r/'src/local/wallpanel/app'
    subprocess.run([java,'-jar',str(r.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',d,*[str(src/n) for n in ('SipRtpPacket.java','SipDigest.java','H264RtpAssembler.java','RtspVideoSession.java')],str(r/'tests/local/wallpanel/app/VideoTransportTests.java')],check=True)
    subprocess.run([java,'-cp',d,'local.wallpanel.app.VideoTransportTests'],check=True,timeout=15)
