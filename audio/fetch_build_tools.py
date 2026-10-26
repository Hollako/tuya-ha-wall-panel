"""Fetch Java/Android compilation tools from official Maven and Google hosts."""
from pathlib import Path
import urllib.request, zipfile, hashlib
root=Path(__file__).resolve().parent/'tooling'
root.mkdir(exist_ok=True)
files={
 'ecj.jar':'https://repo.maven.apache.org/maven2/org/eclipse/jdt/core/compiler/ecj/4.6.1/ecj-4.6.1.jar',
 'dx.jar':'https://repo.maven.apache.org/maven2/com/google/android/tools/dx/1.7/dx-1.7.jar',
 'platform27.zip':'https://dl.google.com/android/repository/platform-27_r03.zip',
}
for name,url in files.items():
    target=root/name
    if not target.exists():
        with urllib.request.urlopen(url,timeout=60) as src,target.open('wb') as out:
            while data:=src.read(1024*1024):out.write(data)
    print(name,target.stat().st_size,hashlib.sha256(target.read_bytes()).hexdigest(),flush=True)
with zipfile.ZipFile(root/'platform27.zip') as src:
    name=next(n for n in src.namelist() if n.endswith('/android.jar'))
    (root/'android.jar').write_bytes(src.read(name))
