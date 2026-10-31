"""Build ARM32/ARM64 native payloads. No installation or device access."""
from pathlib import Path
import hashlib,json,shutil,struct,subprocess
root=Path(__file__).resolve().parent
compiler=root.parent/'panel-172.16.1.26/tooling/zig-windows-x86_64-0.13.0/zig.exe'
variants={'arm64':('aarch64-linux-musl',None,2,183),'arm32':('arm-linux-musleabihf','cortex_a7',1,40)}
catalog={'v':1,'abis':{'arm64':'arm64-v8a','arm32':'armeabi-v7a'},'profiles':{}}
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def compile_native(source,target,abi):
    triple,cpu,elf_class,machine=variants[abi]
    args=[str(compiler),'cc','-target',triple]
    if cpu:args+=['-mcpu='+cpu]
    subprocess.run(args+['-static','-Os','-Wall','-Wextra','-Werror','-Wno-unused-function','-Wno-misleading-indentation',str(root/'native'/source),'-o',str(target)],check=True)
    data=target.read_bytes()
    if data[:4]!=b'\x7fELF' or data[4]!=elf_class or data[5]!=1 or struct.unpack_from('<H',data,18)[0]!=machine:raise ValueError('Wrong ELF architecture: '+str(target))
for abi in variants:
    out=root/'assets/hardware'/('g10-'+abi);out.mkdir(parents=True,exist_ok=True)
    for name in ('wallpanel-privilege','wallpanel-capture','wallpanel-core'):compile_native(name+'.c',out/name,abi)
    metadata={'protocol':1,'profile':'g10','abi':abi,'android_abi':catalog['abis'][abi],'binary':'wallpanel-core','sha256':digest(out/'wallpanel-core'),'capture_sha256':digest(out/'wallpanel-capture'),'privilege_sha256':digest(out/'wallpanel-privilege'),'root_required':True,'automatic_activation':False,'network':False,'runtime_ready':True,'buttons':4,'relays':2,'button_hold':False,'implemented_modules':['buttons','relays','knob_input','knob_display','audio','amplifier','display','management'],'unimplemented_modules':[]}
    (out/'manifest.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    candidate=root/'assets/hardware'/('a133-'+abi);candidate.mkdir(parents=True,exist_ok=True)
    compile_native('a133-core.c',candidate/'wallpanel-core',abi)
    compile_native('a133-capture.c',candidate/'wallpanel-capture',abi)
    shutil.copyfile(out/'wallpanel-privilege',candidate/'wallpanel-privilege')
    metadata={**metadata,'profile':'a133','sha256':digest(candidate/'wallpanel-core'),'runtime_ready':abi=='arm32','capture_sha256':digest(candidate/'wallpanel-capture'),'buttons':0,'relays':2,'knob_framebuffer':1,'knob_led':False,'implemented_modules':['relays','knob_input','knob_display','audio','amplifier','display','management'],'unimplemented_modules':['buttons','knob_led','hardware_gain']}
    (candidate/'manifest.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    print('Built and checked ELF payloads:',abi,flush=True)
for profile in ('g10','a133'):
    catalog['profiles'][profile]={'runtime_ready':True,'variants':{abi:profile+'-'+abi for abi in variants}}
(root/'assets/hardware/catalog.json').write_text(json.dumps(catalog,indent=2),encoding='utf-8')
