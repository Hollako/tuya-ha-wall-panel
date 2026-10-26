from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent;java=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe');out=root/'test-classes/runtime';out.mkdir(parents=True,exist_ok=True);src=root/'src/local/wallpanel/app'
files=[src/n for n in ('PanelConfig.java','ModuleCatalog.java','HardwareTopics.java','MqttSession.java','ManagementPolicy.java','ActivationPolicy.java','KnobTopics.java','DashboardConfig.java','DisplayTopics.java')]+[root/'tests/local/wallpanel/app/RuntimeTests.java',root/'tests/local/wallpanel/app/ActivationTests.java',root/'tests/local/wallpanel/app/KnobURLTests.java',root/'tests/local/wallpanel/app/DisplayTests.java',root/'tests/local/wallpanel/app/ManagementTests.java']
subprocess.run([str(java),'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(out),*[str(p) for p in files]],check=True)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.RuntimeTests'],check=True,timeout=20)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.ActivationTests'],check=True)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.KnobURLTests'],check=True)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.DisplayTests'],check=True)

subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.ManagementTests'],check=True,timeout=20)
