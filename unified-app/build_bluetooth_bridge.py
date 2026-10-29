"""Build the isolated Bluetooth-only routing helper, not any AirPlay engine."""
from pathlib import Path
import subprocess,zipfile,shutil,hashlib,json
r=Path(__file__).resolve().parents[1];out=r/'shared/multiroom/build/bluetooth';out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True)
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';tools=r/'audio/tooling'
subprocess.run([java,'-jar',str(tools/'ecj.jar'),'-1.6','-nowarn','-bootclasspath',str(tools/'android.jar'),'-d',str(classes),*map(str,(r/'shared/multiroom/bridge-src').rglob('*.java'))],check=True)
subprocess.run([java,'-cp',str(tools/'dx.jar'),'com.android.dx.command.Main','--dex','--output='+str(out/'classes.dex'),str(classes)],check=True)
jar=r/'airplay-app/android/app/src/main/assets/bluetooth-group-bridge.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:archive.write(out/'classes.dex','classes.dex')
zig=r/'panel-172.16.1.26/tooling/zig-windows-x86_64-0.13.0/zig.exe';manifest={}
for abi,target,cpu in [('arm64-v8a','aarch64-linux-musl',None),('armeabi-v7a','arm-linux-musleabihf','cortex_a7')]:
 output=jar.parent/'bluetooth-group'/abi/'bootstrap';output.parent.mkdir(parents=True,exist_ok=True)
 args=[str(zig),'cc','-target',target]+(['-mcpu='+cpu] if cpu else [])+['-static','-Os','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',str(r/'shared/multiroom/bridge-src/bootstrap.c'),'-o',str(output)]
 subprocess.run(args,check=True);manifest[abi]=hashlib.sha256(output.read_bytes()).hexdigest()
(jar.parent/'bluetooth-group/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Built Bluetooth-only bridge asset; no AirPlay payloads changed.')
