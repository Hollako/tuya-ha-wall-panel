"""Reuse pinned source archives; isolated ABI staging. Run in WSL as root."""
from pathlib import Path
import subprocess,shutil,os
root=Path(__file__).resolve().parents[1]
scripts=root/'build/arm32-scripts';scripts.mkdir(parents=True,exist_ok=True)
for p in (root/'native/build').glob('*.sh'):
 s=p.read_text().replace('source "$SCRIPT_DIR/../versions.env"','source "'+str(root/'native/versions.env')+'"')
 s=s.replace('make -j$(nproc)','make -j4')
 # Isolate target pkg-config lookups from host/ARM64 libraries.
 if p.name=='00-env.sh':
  s=s.replace('local TOOLCHAIN_DIR="$BUILD_ROOT/toolchains"','local TOOLCHAIN_DIR="/root/wpar/tools"').replace('export CROSS_TRIPLE=armv7l-linux-musleabihf','export CROSS_TRIPLE=arm-buildroot-linux-musleabihf').replace('export TOOLCHAIN_NAME=armv7l-linux-musleabihf-cross','export TOOLCHAIN_NAME=bootlin-arm32')
  s+='\nexport PKG_CONFIG_LIBDIR="$STAGING_DIR/armeabi-v7a/lib/pkgconfig"\n'
 (scripts/p.name).write_text(s)
steps=['10-openssl.sh','11-libsodium.sh','12-libgpg-error.sh','13-libgcrypt.sh','14-libxml2.sh','15-libplist.sh','16-libconfig.sh','17-popt.sh','21-zlib.sh','19a-libuuid.sh','18-ffmpeg.sh','19-nqptp.sh','20-shairport-sync.sh']
for step in steps:
 print('BUILD ARM32: '+step,flush=True)
 subprocess.run(['bash',str(scripts/step),'armeabi-v7a'],check=True)
