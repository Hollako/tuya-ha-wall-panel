"""Pinned official AARs: classes and Android resources, without modifying vendor code."""
from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET

def prepare(root,build,run,linux):
    vendor=root/'vendor/media3';jars=[];resources=[];packages=[];generated=build/'resource-java';generated.mkdir()
    folder=build/'media-deps';folder.mkdir()
    manifest=json.loads((vendor/'manifest.json').read_text())
    for item in manifest:
        source=vendor/'artifacts'/item['file']
        if source.parent!=vendor/'artifacts' or hashlib.sha256(source.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Video dependency hash mismatch')
        target=folder/source.stem;target.mkdir()
        if source.suffix=='.jar':jars.append(source);continue
        with zipfile.ZipFile(source) as archive:
            jar=target/'classes.jar';jar.write_bytes(archive.read('classes.jar'));jars.append(jar)
            package=ET.fromstring(archive.read('AndroidManifest.xml')).get('package')
            if package:packages.append(package)
            for name in archive.namelist():
                if not name.startswith('res/') or name.endswith('/'):continue
                dest=(target/name).resolve()
                if not dest.is_relative_to(target.resolve()):raise RuntimeError('Invalid vendor resource path')
                dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(archive.read(name))
        res=target/'res'
        if res.exists():
            compiled=target/'compiled.zip'
            run('wsl','-d','Ubuntu','--','/home/bacnet/android-sdk/build-tools/34.0.0/aapt2','compile','--dir',linux(res),'-o',linux(compiled))
            resources.append(compiled)
    if (root/'res').exists():
        compiled=build/'app-resources.zip'
        run('wsl','-d','Ubuntu','--','/home/bacnet/android-sdk/build-tools/34.0.0/aapt2','compile','--dir',linux(root/'res'),'-o',linux(compiled))
        resources.append(compiled)
    command=['wsl','-d','Ubuntu','--','/home/bacnet/android-sdk/build-tools/34.0.0/aapt2','link','--manifest',linux(root/'AndroidManifest.xml'),'-I','/home/bacnet/android-sdk/platforms/android-35/android.jar','--java',linux(generated),'--extra-packages',':'.join(packages),'--auto-add-overlay','-o',linux(build/'resources.apk')]
    for resource in resources:command.extend(['-R',linux(resource)])
    run(*command)
    return jars,generated
