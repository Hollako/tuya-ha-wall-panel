"""Independent group executables. Existing AirPlay payloads are never replaced."""
from pathlib import Path
import hashlib,json,struct
ROOT=Path(__file__).resolve().parent.parent
def group_payloads():
 manifest=json.loads((ROOT/'shared/multiroom/native-payloads.json').read_text())
 result={}
 for abi,items in manifest['abis'].items():
  for name,item in items.items():
   path=ROOT/item['path'];data=path.read_bytes()
   expected=(2,183) if abi=='arm64-v8a' else (1,40)
   if data[:4]!=b'\x7fELF' or (data[4],struct.unpack_from('<H',data,18)[0])!=expected or hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Invalid grouping executable: '+abi+'/'+name)
   result[(abi,name)]=path
 return result
def add_group_payloads(archive):
 for (abi,name),path in group_payloads().items():archive.write(path,'lib/'+abi+'/'+name)
 for name in ('COPYING','SOURCE.md','OBOE-LICENSE'):archive.write(ROOT/'shared/multiroom'/name,'assets/licenses/multiroom/'+name)
 archive.write(ROOT/'shared/multiroom/native-payloads.json','assets/multiroom/native-payloads.json')
