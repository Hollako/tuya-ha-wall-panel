"""Record an existing, validated receiver build; never rebuilds or deploys it."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parent.parent
destination=root/'shared/audio-receiver/native-payloads.json'
if destination.exists():raise SystemExit('Baseline already pinned; review changes explicitly')
manifest={'v':1,'engine':'shairport-sync-5.6.1+nqptp-1.2.9','abis':{}}
for abi in ('arm64-v8a','armeabi-v7a'):
    folder=root/'airplay-app/build/native'
    if abi=='armeabi-v7a':folder=folder/abi
    manifest['abis'][abi]={}
    for name in ('shairport-sync','nqptp','wpar-ptp-helper'):
        path=folder/name
        manifest['abis'][abi][name]={'path':path.relative_to(root).as_posix(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
destination.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print('Pinned existing native payloads for both ABIs; no native rebuild or device change')
