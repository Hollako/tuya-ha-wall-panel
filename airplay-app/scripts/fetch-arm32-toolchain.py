from pathlib import Path
import urllib.request,hashlib,tarfile,subprocess,json
root=Path(__file__).resolve().parents[1]
base='https://toolchains.bootlin.com/downloads/releases/toolchains/armv7-eabihf/tarballs/'
name='armv7-eabihf--musl--stable-2024.05-1'
folder=root/'build/toolchains';folder.mkdir(parents=True,exist_ok=True)
archive=folder/(name+'.tar.xz')
checksum=urllib.request.urlopen(base+name+'.sha256',timeout=30).read().decode().split()[0]
if not archive.exists():
 print('Downloading Bootlin ARMv7 musl toolchain',flush=True)
 urllib.request.urlretrieve(base+name+'.tar.xz',archive)
assert hashlib.sha256(archive.read_bytes()).hexdigest()==checksum,'Checksum mismatch'
print('Checksum verified: '+checksum,flush=True)
with tarfile.open(archive) as tar:tar.extractall(folder,filter='data')
tool=folder/name
if not tool.exists():raise RuntimeError('Unexpected extraction directory')
subprocess.run([str(tool/'relocate-sdk.sh')],cwd=tool,check=True)
gcc=next((tool/'bin').glob('*-gcc'))
print('GCC='+str(gcc),flush=True)
subprocess.run([str(gcc),'--version'],check=True)
(root/'build/arm32-bootlin.json').write_text(json.dumps({'directory':str(tool),'gcc':str(gcc),'checksum':checksum}))
