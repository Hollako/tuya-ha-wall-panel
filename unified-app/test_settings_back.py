"""Settings navigation scope and the accessibility capability boundary."""
from pathlib import Path
import subprocess,tempfile,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
android='{http://schemas.android.com/apk/res/android}'
config=ET.parse(root/'res/xml/settings_back_service.xml').getroot()
assert config.get(android+'accessibilityEventTypes')=='typeWindowStateChanged'
assert config.get(android+'canRetrieveWindowContent')=='false'
assert config.get(android+'canRequestFilterKeyEvents')=='false'
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='settings-back-') as folder:
    out=Path(folder);test=out/'SettingsBackTest.java'
    test.write_text('''package local.wallpanel.app;
public class SettingsBackTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  for(String name:new String[]{"MainActivity","PanelSetupActivity","NetworkSettingsActivity","SipSettingsActivity","ReceiverSettingsActivity","BluetoothSettingsActivity"})check(SettingsBackPolicy.setup("local.wallpanel.app."+name));
  for(String name:new String[]{"DashboardActivity","HomeActivity","IntercomCallActivity","SettingsBackService$1"})check(!SettingsBackPolicy.setup("local.wallpanel.app."+name));
  check(!SettingsBackPolicy.setup(null));check(!SettingsBackPolicy.setup("other.MainActivity"));
  check(SettingsBackPolicy.androidSettings("com.android.settings"));check(SettingsBackPolicy.androidSettings("com.android.tv.settings"));
  check(!SettingsBackPolicy.androidSettings("com.android.settings.fake"));check(!SettingsBackPolicy.androidSettings("com.spotify.music"));
  check(SettingsBackPolicy.keyboard("com.android.inputmethod.latin","Keyboard"));check(!SettingsBackPolicy.keyboard("com.spotify.music","Player"));
  System.out.println("PASS: settings scope excludes dashboard, calls, Home, unrelated apps and overlay windows; keyboard scope preserved");
 }
}''',encoding='utf-8')
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',str(out),str(root/'src/local/wallpanel/app/SettingsBackPolicy.java'),str(test)],check=True)
    subprocess.run([java,'-cp',str(out),'local.wallpanel.app.SettingsBackTest'],check=True)
