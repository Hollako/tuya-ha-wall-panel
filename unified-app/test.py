"""Run pure Java validation and fake local MQTT tests; never contacts the panel/HA."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
java=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe')
out=root/'test-classes';out.mkdir(exist_ok=True)
source=root/'src/local/wallpanel/app'
subprocess.run([str(java),'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(out),*[str(source/n) for n in ('PanelConfig.java','ModuleCatalog.java','MqttProbe.java','MqttSession.java','ManagementPolicy.java','HardwareTopics.java','NativeSettings.java','HomePolicy.java')],str(root/'tests/local/wallpanel/app/CoreTests.java')],check=True)
subprocess.run([str(java),'-cp',str(out),'local.wallpanel.app.CoreTests',str(out/'exported-native.conf')],check=True)
subprocess.run([__import__('sys').executable,str(root/'tests/test_launcher_manifest.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_store.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_runtime.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_audio.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_sip.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_sip_rtp.py')],check=True)
subprocess.run([__import__('sys').executable,str(root/'test_sip_migration.py')],check=True)

subprocess.run([__import__('sys').executable,str(root/'test_sip_presentation.py')],check=True)

subprocess.run([__import__('sys').executable,str(root/'test_door.py')],check=True)

subprocess.run([__import__('sys').executable,str(root/'test_network_routing.py')],check=True)

subprocess.run([__import__('sys').executable,str(root/'test_video_transport.py')],check=True)

subprocess.run([r'C:\Program Files\nodejs\node.exe',str(root/'tests/dashboard-theme-compat.test.js'),str(root/'assets/dashboard-theme-compat.js')],check=True)
