"""Build the inactive G10 hardware worker; no installation or ADB use."""
from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).resolve().parent
compiler=root.parent/'panel-172.16.1.26/tooling/zig-windows-x86_64-0.13.0/zig.exe'
out=root/'assets/hardware/g10-arm64';out.mkdir(parents=True,exist_ok=True)
binary=out/'wallpanel-core'
capture=out/'wallpanel-capture'
privilege=out/'wallpanel-privilege'
subprocess.run([str(compiler),'cc','-target','aarch64-linux-musl','-static','-Os','-Wall','-Wextra','-Werror','-Wno-unused-function','-Wno-misleading-indentation',str(root/'native/wallpanel-privilege.c'),'-o',str(privilege)],check=True)
subprocess.run([str(compiler),'cc','-target','aarch64-linux-musl','-static','-Os','-Wall','-Wextra','-Werror','-Wno-unused-function','-Wno-misleading-indentation',str(root/'native/wallpanel-capture.c'),'-o',str(capture)],check=True)
subprocess.run([str(compiler),'cc','-target','aarch64-linux-musl','-static','-Os','-Wall','-Wextra','-Werror','-Wno-unused-function','-Wno-misleading-indentation',str(root/'native/wallpanel-core.c'),'-o',str(binary)],check=True)
(out/'manifest.json').write_text(json.dumps({'protocol':1,'profile':'g10','abi':'arm64','binary':'wallpanel-core','sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),'capture_sha256':hashlib.sha256(capture.read_bytes()).hexdigest(),'root_required':True,'automatic_activation':False,'network':False,'buttons':4,'relays':2,'button_hold':False,'implemented_modules':['buttons','relays','knob_input','knob_display','audio','amplifier','display','management'],'unimplemented_modules':['sip']},indent=2),encoding='utf-8')
print('Built opt-in hardware worker:',binary.stat().st_size,'bytes')
