"""Run the production settings store against isolated fake Android/Keystore facades."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
java=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe')
out=root/'test-classes/store';out.mkdir(parents=True,exist_ok=True)
source=root/'src/local/wallpanel/app'
files=[source/n for n in ('ConfigStore.java','PanelConfig.java','ModuleCatalog.java','HardwareTopics.java','MqttSession.java','ManagementPolicy.java','DashboardConfig.java')]
files+=list((root/'tests/store-stubs').rglob('*.java'))+[root/'tests/local/wallpanel/app/StoreTests.java']
subprocess.run([str(java),'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(out),*[str(p) for p in files]],check=True)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.StoreTests'],check=True)
