"""Host checks for app capture isolation; no microphone or device accessed."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
with tempfile.TemporaryDirectory() as folder:
 test=Path(folder)/'AppCaptureTest.java'
 test.write_text('''import com.wallpanel.bridge.AppCapturePolicy;
public class AppCaptureTest {
 public static void main(String[] args){
  for(int owner:new int[]{10065,10092}){
   for(int uid:new int[]{0,1000,1001,1002,9999,20000,110053,owner})if(AppCapturePolicy.allowed(uid,owner))throw new AssertionError("System, cross-user or feedback UID captured");
   for(int uid:new int[]{10000,10053,10077,19999})if(!AppCapturePolicy.allowed(uid,owner))throw new AssertionError("Ordinary media app excluded");
  }
  int[] installed=new int[120];for(int i=0;i<installed.length;i++)installed[i]=10000+i;
  int[] excluded=AppCapturePolicy.exclusions(10092,installed);if(excluded.length+2>20)throw new AssertionError("Firmware rule limit exceeded");
  for(int uid:new int[]{10053,10073})for(int e:excluded)if(e==uid)throw new AssertionError("YouTube/other ordinary app excluded");
  for(int uid:new int[]{10092,1000,1002,1041}){boolean found=false;for(int e:excluded)if(e==uid)found=true;if(!found)throw new AssertionError("Feedback/system UID missing");}
  if(AppCapturePolicy.exclusions(10092,new int[]{1000,1002,10053,110053}).length!=9)throw new AssertionError("Cross-user UID not excluded");
  boolean rejected=false;try{AppCapturePolicy.exclusions(10092,new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15});}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new AssertionError("Oversized rules silently truncated");
  System.out.println("PASS: app capture excludes system/call/Bluetooth/cross-user UIDs and its own feedback player, while including ordinary app UIDs");
 }
}''')
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.6','-nowarn','-d',folder,str(root/'shared/multiroom/bridge-src/com/wallpanel/bridge/AppCapturePolicy.java'),str(test)],check=True)
 subprocess.run([java,'-cp',folder,'AppCaptureTest'],check=True)
