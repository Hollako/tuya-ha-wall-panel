from pathlib import Path
import shutil,subprocess,json,os,signal
root=Path(__file__).resolve().parents[1]
meta=json.loads((root/'build/arm32-bootlin.json').read_text())
target=Path('/root/wpar/tools/bootlin-arm32')
if not target.exists():shutil.copytree(meta['directory'],target,symlinks=True)
subprocess.run([str(target/'relocate-sdk.sh')],cwd=target,check=True)
cc=target/'bin/arm-buildroot-linux-musleabihf-gcc'
subprocess.run([str(cc),'--version'],check=True)
# Stop only the superseded make job and descendants, never unrelated WSL tasks.
parents=[]
for proc in Path('/proc').glob('[0-9]*'):
 try:
  args=(proc/'cmdline').read_bytes().replace(b'\0',b' ')
  if args.startswith(b'make -C /root/wpar/musl-cross-make -j2 TARGET=armv7l-linux-musleabihf '):parents.append(int(proc.name))
 except OSError:pass
def descendants(pid):
 result=[]
 for proc in Path('/proc').glob('[0-9]*'):
  try:
   fields=(proc/'stat').read_text().rsplit(')',1)[1].split()
   if int(fields[1])==pid:result+=descendants(int(proc.name))+[int(proc.name)]
  except (OSError,ValueError):pass
 return result
for pid in parents:
 for child in descendants(pid)+[pid]:
  try:os.kill(child,signal.SIGTERM)
  except ProcessLookupError:pass
print('Verified Bootlin compiler activated; superseded build stopped.',flush=True)
