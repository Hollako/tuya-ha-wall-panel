"""WSL/root-cache reader: matching receiver source, notices and relink materials.

Only whitelisted build inputs/outputs are read; no Android data, signing or logs.
"""
from pathlib import Path
import hashlib,io,json,subprocess,tarfile

root=Path(__file__).resolve().parent.parent
out=root/'.publish/v1.18.0';out.mkdir(parents=True,exist_ok=True)
destination=out/'receiver-native-source-and-relink-1.2.0.tar.gz'
records={}
def add(archive,path,name):
    if path.is_symlink() or not path.is_file():return
    data=path.read_bytes();item=tarfile.TarInfo(name);item.size=len(data);item.mode=0o644
    archive.addfile(item,io.BytesIO(data));records[name]=hashlib.sha256(data).hexdigest()
def memory(archive,name,data):
    item=tarfile.TarInfo(name);item.size=len(data);item.mode=0o644;archive.addfile(item,io.BytesIO(data))
allowed={'.c','.h','.cc','.cpp','.S','.s','.m4','.ac','.am','.in','.sh','.pc','.o','.a','.md','.rst','.txt','.man','.conf','.service','.rc','.py'}
names={'Makefile','configure','config.status','config.h','config.sub','config.guess','install-sh','missing','depcomp','compile','libtool'}
def source_tree(archive,folder,prefix):
    for path in sorted(folder.rglob('*')):
        relative=path.relative_to(folder)
        if any(part in ('.git','.deps','.libs') for part in relative.parts):continue
        if path.suffix in allowed or path.name in names or path.name.startswith(('COPYING','LICENSE','NOTICE','AUTHORS','ChangeLog','README')):
            add(archive,path,prefix+'/'+relative.as_posix())
sources=Path('/root/wpar/build/sources')
with tarfile.open(destination,'w:gz',compresslevel=5) as archive:
    for path in sorted(sources.iterdir()):
        if path.name=='openssl-3.5.4.tar.gz':continue # unused cache; linked version is 3.0.13
        if path.name.endswith(('.tar.gz','.tar.xz','.tar.bz2')):add(archive,path,'upstream-sources/'+path.name)
    for abi,work in [('arm64-v8a','native-work'),('armeabi-v7a','native-work32')]:
        for project in ('shairport-sync-5.6.1','nqptp-1.2.9'):
            source_tree(archive,root/'airplay-app/build'/work/project,'configured/'+abi+'/'+project)
        stage=Path('/root/wpar/build/staging')/abi
        for folder in ('include','lib'):
            for path in sorted((stage/folder).rglob('*')):
                if path.suffix in ('.a','.h','.pc','.la'):add(archive,path,'staging/'+abi+'/'+path.relative_to(stage).as_posix())
        native=root/'airplay-app/build/native'
        if abi=='armeabi-v7a':native=native/abi
        for name in ('wpar-shm.o','shairport-sync','nqptp','wpar-ptp-helper'):add(archive,native/name,'native-output/'+abi+'/'+name)
    source_tree(archive,root/'airplay-app/native','project/airplay-app/native')
    source_tree(archive,root/'airplay-app/scripts','project/airplay-app/scripts')
    source_tree(archive,root/'shared/audio-receiver','project/shared/audio-receiver')
    for name in ('shared/audio-receiver/native-payloads.json','airplay-app/docs/BUILD.md','THIRD_PARTY.md','BUILDING.md'):
        add(archive,root/name,'project/'+name)
    # Include the configured dependency build recipes/configuration alongside the
    # pristine versioned archives, without transient environment/configure logs.
    for abi in ('arm64-v8a','armeabi-v7a'):
        for project in sorted((Path('/root/wpar/build/build')/abi).iterdir()):
            if not project.is_dir():continue
            for name in ('config.h','config.mak','config.status','Makefile','ffbuild/config.mak','include/openssl/configuration.h'):
                add(archive,project/name,'dependency-config/'+abi+'/'+project.name+'/'+name)
            for pattern in ('COPYING*','LICENSE*','NOTICE*'):
                for path in project.glob(pattern):add(archive,path,'dependency-notices/'+project.name+'/'+path.name)
    readme='''Matching native source/relink materials for Audio Receiver 1.2.0 and unified 0.23.0.

upstream-sources/: exact downloaded archives used by the configured build.
configured/<ABI>/: patched Shairport/nqptp sources, headers, Makefiles, configure
state and relinkable object files used by the release native artifacts.
staging/<ABI>/: dependency headers, static libraries and pkg-config metadata.
dependency-config/ and dependency-notices/: actual dependency build settings and notices.
native-output/<ABI>/: exact release executables plus the shared-memory shim object.
project/: custom backend/helper/shm source, build scripts, shared Java engine and notices.

Retain each upstream license; inspect individual-file notices. See project/THIRD_PARTY.md.
Shairport Sync5.6.1 is MIT-style; nqptp1.2.9 is GPL-2.0. The FFmpeg/libconfig/
libplist/libgcrypt/libgpg-error libraries retain their LGPL terms. Source archives
contain full originals; configured trees include our modifications. The supplied
objects/libraries permit relinking the receiver against modified native libraries.

This preserves an existing, tested incremental developer build, not a portable
compiler or a clean-host build promise. Install the matching toolchain: ARM64
aarch64-linux-musl GCC9.4 or ARM32 Bootlin arm-buildroot-linux-musleabihf GCC13.3.
Preserve their runtime notices/exceptions. Install the platform build utilities
used by the scripts. Adjust absolute tool/cache/repository paths in configuration
and Makefiles, or recreate the original /root/wpar cache layout. Restore staging
to /root/wpar/build/staging/<ABI>, configured trees to the project's
airplay-app/build/native-work or native-work32, then follow project/BUILDING.md.
Use scripts/build-isolated-native.py to compile the backend/helper and relink;
it deliberately keeps the tested output-clock synchronization controller.

No app signing key, receiver/HA credentials, Android data or device backup is included.
Signing is intentionally separate: retain your own identity for APK updates.
FILE-SHA256.json records all included file hashes. Exact native binary checksums
also appear in project/shared/audio-receiver/native-payloads.json.
'''
    memory(archive,'README.txt',readme.encode())
    memory(archive,'FILE-SHA256.json',json.dumps(records,indent=2).encode())
print(json.dumps({'archive':destination.name,'bytes':destination.stat().st_size,'files':len(records),'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()}),flush=True)
