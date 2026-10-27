"""One-time source-only extraction; keeps a verified local rollback archive."""
from pathlib import Path
import datetime,hashlib,shutil,zipfile
root=Path(__file__).resolve().parent.parent
source=root/'unified-app/src/local/wallpanel/app'
shared=root/'shared/sip/src/local/wallpanel/app'
if shared.exists():raise SystemExit('Shared SIP core already exists')
names=['SipConfig.java','SipEngine.java','SipG711.java','SipRtpPacket.java','SipSdp.java','SipDigest.java']
backup=root/'unified-app/merge-backups'/('sip-core-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.zip')
backup.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(backup,'x',zipfile.ZIP_DEFLATED) as archive:
    for name in names:archive.write(source/name,name)
with zipfile.ZipFile(backup) as archive:
    for name in names:assert hashlib.sha256(archive.read(name)).digest()==hashlib.sha256((source/name).read_bytes()).digest()
shared.mkdir(parents=True)
for name in names:shutil.move(str(source/name),str(shared/name))
for name in ('test_sip.py','test_sip_rtp.py','test_sip_presentation.py','test_door.py','test_video_transport.py'):
    path=root/'unified-app'/name
    text=path.read_text()
    assert 'str(src/n)' in text
    text=text.replace('from pathlib import Path','from pathlib import Path\nfrom engine_sources import sip_source').replace('str(src/n)','str(sip_source(n))')
    path.write_text(text,encoding='utf-8')
print('Extracted single SIP protocol core; Android adapters remain product-specific')
