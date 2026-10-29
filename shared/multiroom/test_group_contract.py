"""Exercise invalid source/format boundaries and the actual shipped payloads."""
from pathlib import Path
import subprocess,tempfile,sys,hashlib,json,zipfile
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'unified-app'))
from multiroom_payloads import group_payloads
from receiver_payloads import payloads
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
with tempfile.TemporaryDirectory() as folder:
 path=Path(folder);test=path/'GroupContract.java'
 test.write_text('''import com.wallpanel.multiroom.GroupPolicy;import com.wallpanel.multiroom.GroupPlan;import com.wallpanel.multiroom.MusicLibrary;import java.util.*;
public class GroupContract {
 interface Action {void run();}
 static void reject(Action action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Accepted invalid input");}
 public static void main(String[] args){
  GroupPolicy.source("https://example.com/music.mp3");GroupPolicy.source("content://media/audio/17");GroupPolicy.host("172.16.1.26");GroupPolicy.format(48000,2);
  for(String value:new String[]{"file:///system/secret","https://user:password@example.com/song","http:///no-host","javascript:bad","https://example.com/a#fragment"})reject(()->GroupPolicy.source(value));
  for(String value:new String[]{"","172.16.1.26:1704","host/name","bad..host","host;command"})reject(()->GroupPolicy.host(value));
  reject(()->GroupPolicy.format(96000,2));reject(()->GroupPolicy.format(48000,6));reject(()->GroupPolicy.role("unexpected"));
  GroupPolicy.buffer(2000);reject(()->GroupPolicy.buffer(999));reject(()->GroupPolicy.buffer(5001));
  if(!GroupPolicy.clientLatency(100).equals("-100")||!GroupPolicy.clientLatency(-100).equals("100"))throw new AssertionError("Room delay direction inverted");
  reject(()->GroupPolicy.roomDelay(-501));reject(()->GroupPolicy.roomDelay(501));
  String a="panel-aaaaaaaa",b="panel-bbbbbbbb";
  if(!GroupPlan.members(Arrays.asList(b,b),a).equals(Arrays.asList(a,b)))throw new AssertionError("Source inclusion/order or deduplication failed");
  if(!GroupPlan.label(" Ground Floor ").equals("Ground Floor"))throw new AssertionError();
  reject(()->GroupPlan.label("bad\\nname"));reject(()->GroupPlan.label("  "));reject(()->GroupPlan.members(Arrays.asList("foreign-player"),a));
  ArrayList<String> tooMany=new ArrayList<>();for(int i=0;i<33;i++)tooMany.add(a);reject(()->GroupPlan.members(tooMany,a));
  MusicLibrary.trackId("audio-42");MusicLibrary.folderId("all");MusicLibrary.folderId("folder-0123456789abcdef0123456789abcdef");
  for(String value:new String[]{"file:///sdcard/song.mp3","audio-../42","audio-0","audio-42?path=secret"})reject(()->MusicLibrary.trackId(value));
  reject(()->MusicLibrary.folderId("../../private"));
  System.out.println("PASS: invalid sources, host syntax and unsupported audio formats fail before process startup");
 }
}''',encoding='utf-8')
 sdk=str(root/'audio/tooling/android.jar')
 subprocess.run([java,'-jar',str(root/'audio/tooling/ecj.jar'),'-1.8','-nowarn','-classpath',sdk,'-d',folder,str(root/'shared/multiroom/src/com/wallpanel/multiroom/GroupPolicy.java'),str(root/'shared/multiroom/src/com/wallpanel/multiroom/GroupPlan.java'),str(root/'shared/multiroom/src/com/wallpanel/multiroom/MusicLibrary.java'),str(test)],check=True)
 subprocess.run([java,'-cp',folder+';'+sdk,'GroupContract'],check=True)
groups=group_payloads();airplay=payloads()
print('PASS: four grouping payloads have the pinned hashes and correct ARM ELF architecture')
for apk in (root/'unified-app'/json.loads((root/'unified-app/build-result.json').read_text())['apk'],root/'airplay-app/android/app/build/outputs/apk/debug/app-debug.apk'):
 with zipfile.ZipFile(apk) as archive:
  for (abi,name),path in groups.items():assert archive.read('lib/'+abi+'/'+name)==path.read_bytes()
  assert len(archive.read('assets/licenses/multiroom/COPYING'))>30000
  for (abi,name),canonical in airplay.items():
   if apk.name=='app-debug.apk' and name!='shairport-sync':continue # Dedicated product uses its existing root helper installation.
   destination='lib/'+abi+'/libwpar_shairport.so' if name=='shairport-sync' else 'assets/receiver/'+abi+'/'+name
   assert archive.read(destination)==canonical.read_bytes(),str(apk)+': AirPlay payload changed'
 print('PASS:',apk.name,'contains separate group processes, GPL notice and unchanged bundled AirPlay executables')
