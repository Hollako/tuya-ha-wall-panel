"""Package the same verified receiver executables for both product builds."""
from pathlib import Path
import hashlib,json,struct

ROOT=Path(__file__).resolve().parent.parent
PINNED=ROOT/'shared/audio-receiver/native-payloads.json'

def payloads():
    manifest=json.loads(PINNED.read_text())
    result={}
    for abi,machine,elf_class in [('arm64-v8a',183,2),('armeabi-v7a',40,1)]:
        for name,record in manifest['abis'][abi].items():
            path=ROOT/record['path'];data=path.read_bytes()
            if hashlib.sha256(data).hexdigest()!=record['sha256']:
                raise RuntimeError('Receiver artifact changed: '+abi+'/'+name)
            if data[:4]!=b'\x7fELF' or data[4]!=elf_class or data[5]!=1 or struct.unpack_from('<H',data,18)[0]!=machine:
                raise RuntimeError('Receiver ELF architecture mismatch')
            result[(abi,name)]=path
    return result

def add_to_apk(archive):
    for (abi,name),path in payloads().items():
        destination=('lib/'+abi+'/libwpar_shairport.so') if name=='shairport-sync' else ('assets/receiver/'+abi+'/'+name)
        archive.write(path,destination)
    archive.write(PINNED,'assets/receiver/native-payloads.json')
    # Retain the notices from the exact configured upstream versions used to build.
    source=ROOT/'airplay-app/build/native-work'
    for project in ('shairport-sync-5.6.1','nqptp-1.2.9'):
        notice=source/project/'COPYING'
        if not notice.is_file():raise RuntimeError('Missing receiver upstream notice: '+project)
        archive.write(notice,'assets/licenses/receiver/'+project+'-COPYING')
