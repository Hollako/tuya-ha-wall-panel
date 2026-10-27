"""Run under WSL as root; reuse existing dependency staging, edit only this copy."""
from pathlib import Path
import shutil,subprocess,os,shlex,sys
root=Path(__file__).resolve().parents[1]
abi=sys.argv[1] if len(sys.argv)>1 else 'arm64-v8a'
if abi not in ['arm64-v8a','armeabi-v7a']:raise SystemExit('Unsupported ABI')
triple='aarch64-linux-musl' if abi=='arm64-v8a' else 'armv7l-linux-musleabihf'
work=root/('build/native-work' if abi=='arm64-v8a' else 'build/native-work32')
out=root/('build/native' if abi=='arm64-v8a' else 'build/native/armeabi-v7a');out.mkdir(parents=True,exist_ok=True)
cc=f'/root/wpar/build/toolchains/{triple}-cross/bin/{triple}-gcc'
stage=f'/root/wpar/build/staging/{abi}'
if abi=='armeabi-v7a':cc='/root/wpar/tools/bootlin-arm32/bin/arm-buildroot-linux-musleabihf-gcc'
def run(*a,**kw):subprocess.run([str(x) for x in a],check=True,**kw)
run(cc,'-static','-Os','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',root/'native/wpar-ptp-helper.c','-o',out/'wpar-ptp-helper')
run(cc,'-Os','-c',root/'native/wpar-shm.c','-o',out/'wpar-shm.o')
for name in ['nqptp-1.2.9','shairport-sync-5.6.1']:
 d=work/name
 if not d.exists():shutil.copytree(Path('/root/wpar/build/build')/abi/name,d)
 if name.startswith('nqptp'):
  f=d/'nqptp.c';s=f.read_text()
  marker='  open_sockets_at_port(NULL, 320, &sockets_open_stuff);'
  if 'wpar_drop_privileges();' not in s:
   assert marker in s;s=s.replace(marker,marker+'\n  wpar_drop_privileges();');s='#include "'+str(root/'native/wpar-drop.h')+'"\n'+s;f.write_text(s)
  f=d/'nqptp.c';s=f.read_text();s=s.replace('int s = pthread_setschedparam(pthread_self(), policy, &param);','int s = 0; /* WPAR: no privileged real-time scheduling. */');f.write_text(s)
  f=d/'nqptp-clock-sources.c';s=f.read_text();s=s.replace('MAP_LOCKED | MAP_SHARED','MAP_SHARED');f.write_text(s)
  # Android app UID cannot lock the full PTP mapping; no real-time guarantee claimed.
  run('make','-o','config.status','-o','Makefile','-o','config.h','nqptp','LDFLAGS=-static -Wl,--wrap=shm_open,--wrap=shm_unlink','LIBS='+shlex.quote(str(out/'wpar-shm.o'))+' -lpthread -lm',cwd=d)
  shutil.copy2(d/'nqptp',out/'nqptp')
 else:
  # Android timing backend occupies the compiled pipe backend slot.
  shutil.copy2(root/'native/wpar-android-output.c',d/'audio_pipe.c')
  f=d/'rtp.c';s=f.read_text()
  if 'extern double wpar_android_latency_offset' not in s:
   s='extern double wpar_android_latency_offset(double);\n'+s
  s=s.replace('config.audio_backend_latency_offset * conn->input_rate','wpar_android_latency_offset(config.audio_backend_latency_offset) * conn->input_rate')
  f.write_text(s)
  run('python3',root/'scripts/patch-mdns.py',d/'mdns_tinysvcmdns.c')
  run('python3',root/'scripts/patch-mdns-txt.py',d/'tinysvcmdns.c')
  libs=shlex.quote(str(out/'wpar-shm.o'))+' -lgcrypt -lgpg-error -lsodium -lssl -lcrypto -lplist-2.0 -lxml2 -lconfig -lpopt -lavcodec -lavformat -lavutil -lswresample -luuid -lz -lm -lpthread'
  (d/'shairport-sync').unlink(missing_ok=True)
  run('make','-o','config.status','-o','Makefile','-o','config.h','shairport-sync','LDFLAGS=-static -L'+stage+'/lib -Wl,--wrap=shm_open,--wrap=shm_unlink','LIBS='+libs,cwd=d)
  shutil.copy2(d/'shairport-sync',out/'shairport-sync')
for f in ['wpar-ptp-helper','nqptp','shairport-sync']:
 run(cc.replace('-gcc','-strip'),out/f)
 run('file',out/f);run('readelf','-d',out/f)
libs=root/'android/app/src/main/jniLibs'/abi
libs.mkdir(parents=True,exist_ok=True)
shutil.copy2(out/'shairport-sync',libs/'libwpar_shairport.so')
# nqptp is deliberately not bundled as an app-launched executable.
(libs/'libwpar_nqptp.so').unlink(missing_ok=True)
