"""Real app volume/command policy on a fake Android backend; no ADB or audio."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parent;j=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe');out=r/'test-classes/audio';out.mkdir(parents=True,exist_ok=True)
t=(r.parent/'audio/tests/local/wallpanel/audio/VolumePolicyTest.java').read_text().replace('package local.wallpanel.audio;','package local.wallpanel.app;')
(out/'VolumePolicyTest.java').write_text(t)
files=[r/'src/local/wallpanel/app/VolumePolicy.java',r/'src/local/wallpanel/app/AudioPolicy.java',out/'VolumePolicyTest.java',r/'tests/local/wallpanel/app/AudioPolicyTests.java']
subprocess.run([str(j),'-jar',str(r.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(out),*[str(p) for p in files]],check=True)
for name in ('VolumePolicyTest','AudioPolicyTests'):subprocess.run([str(j),'-cp',str(out),'local.wallpanel.app.'+name],check=True)
