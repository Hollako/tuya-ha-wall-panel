"""Stage exact separate-process binaries and retain their corresponding source."""
from pathlib import Path
import hashlib,json,shutil,tarfile
root=Path(__file__).resolve().parents[1];work=root.parent/'multiroom-work';source=work/'snapcast'
manifest={'engine':'snapcast-0.34.0+oboe-1.9.3','revision':'ee0f896cd02c474bf18c6cd9f10b0b7c8b6dd009','group_patch':'timestamp-output-v3-fine-volume','oboe_revision':'b15f5e39c01a7ada306d959e5129620b145fb8b4','license':'GPL-3.0-or-later','abis':{}}
shutil.copyfile(source/'LICENSE',root/'COPYING')
for abi in ('arm64-v8a','armeabi-v7a'):
 manifest['abis'][abi]={};dest=root/'native'/abi;dest.mkdir(parents=True,exist_ok=True)
 for name in ('libwpar_groupserver.so','libwpar_groupclient.so'):
  path=dest/name;shutil.copyfile(work/'native'/abi/name,path)
  manifest['abis'][abi][name]={'path':path.relative_to(root.parents[1]).as_posix(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
  android=root.parents[1]/'airplay-app/android/app/src/main';target=android/'jniLibs'/abi/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
(root/'native-payloads.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
assets=android/'assets/licenses/multiroom';assets.mkdir(parents=True,exist_ok=True)
shutil.copyfile(work/'oboe/LICENSE',root/'OBOE-LICENSE')
for name in ('COPYING','SOURCE.md','OBOE-LICENSE'):shutil.copyfile(root/name,assets/name)
asset_manifest=android/'assets/multiroom/native-payloads.json';asset_manifest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/'native-payloads.json',asset_manifest)
vendor=root/'vendor';vendor.mkdir(exist_ok=True)
with tarfile.open(vendor/'snapcast-ee0f896-source.tar.gz','w:gz') as archive:
 for path in source.rglob('*'):
  if path.is_file() and '.git' not in path.relative_to(source).parts:archive.add(path,'snapcast/'+path.relative_to(source).as_posix(),recursive=False)
 archive.add(root/'scripts/build_native.py','build_native.py')
 archive.add(root/'scripts/patch_group_timing.py','patch_group_timing.py')
 archive.add(root/'native-src','native-src')
 for path in (work/'oboe').rglob('*'):
  if path.is_file() and '.git' not in path.relative_to(work/'oboe').parts:archive.add(path,'oboe/'+path.relative_to(work/'oboe').as_posix(),recursive=False)
print('Prepared ARM32/64 group processes, licenses, pinned hashes and corresponding source archive.')
