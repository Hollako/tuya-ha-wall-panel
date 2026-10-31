from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[1]
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
with tempfile.TemporaryDirectory() as folder:
    test=Path(folder)/'CameraAddressTest.java'
    test.write_text('''import local.wallpanel.app.CameraAddress;
public class CameraAddressTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  String address="rtsp://172.16.1.30:554/path%2Fpart?q=a%2Fb",password="a@b:c/?%";
  String stored=CameraAddress.withLogin(address,"admin",password);
  check(CameraAddress.visible(stored).equals(address));check(CameraAddress.user(stored).equals("admin"));check(CameraAddress.password(stored).equals(password));
  String moved=CameraAddress.withLogin(CameraAddress.visible(stored).replace("172.16.1.30","172.16.1.31"),CameraAddress.user(stored),CameraAddress.password(stored));
  check(CameraAddress.password(moved).equals(password)&&CameraAddress.visible(moved).contains("172.16.1.31"));
  check(CameraAddress.visible(CameraAddress.withLogin(address,"","")).equals(address));check(CameraAddress.visible("").equals(""));
  for(String bad:new String[]{"not-a-url","http://camera/live","rtsp://camera/live#fragment"})try{CameraAddress.visible(bad);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS: editable RTSP address retains encrypted login on IP changes, preserves escaped paths/query and never exposes password in visible URL.");
 }
}''',encoding='utf-8')
    subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-d',folder,str(root/'shared/sip/src/local/wallpanel/app/CameraAddress.java'),str(test)],check=True)
    subprocess.run([java,'-cp',folder,'CameraAddressTest'],check=True)
