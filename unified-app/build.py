"""Offline Android 8-compatible foundation APK build. Does not install anything."""
from pathlib import Path
import base64,datetime,hashlib,json,shutil,subprocess,sys,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
subprocess.run([sys.executable,str(ROOT/'build_native.py')],check=True)
PANEL=ROOT.parent/'panel-172.16.1.26'
TOOLS=ROOT.parent/'audio/tooling'
JAVA=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe')
BUILD=ROOT/'build'
if BUILD.resolve()!=ROOT/'build' or BUILD.is_symlink() or BUILD.is_junction():
    raise RuntimeError('Build output resolves outside its expected project directory')
if BUILD.exists():shutil.rmtree(BUILD) # Fixed build output directory within this project only.
BUILD.mkdir();classes=BUILD/'classes';classes.mkdir()
metadata=ET.parse(ROOT/'AndroidManifest.xml').getroot().attrib;android='{http://schemas.android.com/apk/res/android}'
version=metadata[android+'versionName'];version_code=int(metadata[android+'versionCode'])
if not all(c in '0123456789.' for c in version):raise ValueError('Unexpected APK version format')
def run(*args):subprocess.run([str(x) for x in args],check=True)
def linux_path(path):
    p=Path(path).resolve();return '/mnt/'+p.drive[0].lower()+p.as_posix()[2:]
from media_dependencies import prepare
media_jars,resource_java=prepare(ROOT,BUILD,run,linux_path)
vendor=ROOT/'vendor/sip';dependencies=[]
for item in json.loads((vendor/'manifest.json').read_text()):
    jar=vendor/item['file']
    if jar.parent!=vendor or hashlib.sha256(jar.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('SIP dependency integrity mismatch')
    dependencies.append(jar)
dependencies.extend(media_jars)
receiver_sources=ROOT.parent/'shared/audio-receiver/src'
sip_sources=ROOT.parent/'shared/sip/src'
group_sources=ROOT.parent/'shared/multiroom/src'
bootclasspath=str(TOOLS/'android.jar')+';'+str(JAVA.parent.parent/'lib/rt.jar')
run(JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-1.8','-nowarn','-bootclasspath',bootclasspath,'-classpath',';'.join(str(p) for p in dependencies),'-d',classes,*sorted((ROOT/'src').rglob('*.java')),*sorted(receiver_sources.rglob('*.java')),*sorted(group_sources.rglob('*.java')),*sorted(sip_sources.rglob('*.java')),*sorted(resource_java.rglob('*.java')))
compiled=BUILD/'app-classes.jar'
with zipfile.ZipFile(compiled,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in classes.rglob('*.class'):archive.write(path,path.relative_to(classes).as_posix())
def linux_path(path):
    p=Path(path).resolve();return '/mnt/'+p.drive[0].lower()+p.as_posix()[2:]
dex_folder=BUILD/'dex';dex_folder.mkdir()
run('wsl','-d','Ubuntu','--','/home/bacnet/android-sdk/build-tools/34.0.0/d8','--min-api','23','--lib','/home/bacnet/android-sdk/platforms/android-35/android.jar','--output',linux_path(dex_folder),linux_path(compiled),*[linux_path(p) for p in dependencies])
if not (dex_folder/'classes.dex').exists():raise RuntimeError('Missing DEX output')
shutil.copyfile(dex_folder/'classes.dex',BUILD/'classes.dex')
all_dex=b''.join(p.read_bytes() for p in dex_folder.glob('classes*.dex'))
for descriptor in (b'Llocal/wallpanel/app/SipNetworkLayer;',b'Landroidx/media3/exoplayer/rtsp/RtspMediaSource;',b'Llocal/wallpanel/app/SipService;',b'Landroid/javax/sip/SipFactory;',b'Landroid/gov/nist/javax/sip/SipStackImpl;',b'Lorg/slf4j/impl/StaticLoggerBinder;'):
    if descriptor not in all_dex:raise RuntimeError('Missing SIP classes in APK')
ui_classes=BUILD/'system-ui-classes';ui_classes.mkdir()
run(JAVA,'-jar',TOOLS/'ecj.jar','-encoding','UTF-8','-1.6','-nowarn','-bootclasspath',TOOLS/'android.jar','-d',ui_classes,*sorted((ROOT/'system-ui/src').rglob('*.java')))
run(JAVA,'-cp',TOOLS/'dx.jar','com.android.dx.command.Main','--dex','--output='+str(BUILD/'system-ui.dex'),ui_classes)
ui_jar=ROOT/'assets/hardware/g10-arm64/wallpanel-system-ui.jar'
with zipfile.ZipFile(ui_jar,'w',zipfile.ZIP_DEFLATED) as out:out.write(BUILD/'system-ui.dex','classes.dex')
for profile in ('g10','a133'):
    for abi in ('arm64','arm32'):
        destination=ROOT/'assets/hardware'/(profile+'-'+abi)/'wallpanel-system-ui.jar'
        if destination!=ui_jar:shutil.copyfile(ui_jar,destination)
provision=ROOT/'assets/provision'
variants={}
for abi in ('arm64','arm32'):
    hardware=ROOT/'assets/hardware'/('g10-'+abi)
    paths={name:(provision if name.endswith('.sh') else hardware)/name for name in ('wallpanel-core','wallpanel-capture','wallpanel-privilege','wallpanel-system-ui.jar','setup-panel.sh','bootstrap-panel.sh')}
    variants[abi]={'files':{name:hashlib.sha256(path.read_bytes()).hexdigest() for name,path in paths.items()}}
(provision/'manifest.json').write_text(json.dumps({'v':1,'profile':'g10','files':variants['arm64']['files'],'variants':variants},indent=2),encoding='utf-8')
apk=BUILD/'apk';apk.mkdir();shutil.copyfile(ROOT/'AndroidManifest.xml',apk/'AndroidManifest.xml');shutil.copyfile(BUILD/'classes.dex',apk/'classes.dex')
if (ROOT/'assets').exists():shutil.copytree(ROOT/'assets',apk/'assets')
licenses=apk/'assets/licenses/sip';licenses.mkdir(parents=True,exist_ok=True)
for jar in dependencies:
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith('/pom.xml'):(licenses/(jar.stem+'-pom.xml')).write_bytes(archive.read(name))
            elif 'LICENSE' in name.upper() or 'NOTICE' in name.upper():
                if not name.endswith('/'):(licenses/(jar.stem+'-'+Path(name).name)).write_bytes(archive.read(name))
(apk/'apktool.yml').write_text('version: 2.9.3\napkFileName: tuya-wall-panel.apk\nusesFramework:\n  ids:\n  - 1\nsdkInfo:\n  minSdkVersion: 23\n  targetSdkVersion: 27\npackageInfo:\n  forcedPackageId: 127\nversionInfo:\n  versionCode: '+str(version_code)+'\n  versionName: '+version+'\n',encoding='utf-8')
with zipfile.ZipFile(BUILD/'resources.apk') as source,zipfile.ZipFile(BUILD/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as out:
    from receiver_payloads import add_to_apk
    add_to_apk(out)
    from multiroom_payloads import add_group_payloads
    add_group_payloads(out)
    for name in source.namelist():out.writestr(name,source.read(name))
    for dex in sorted(dex_folder.glob('classes*.dex')):out.write(dex,dex.name)
    for path in (apk/'assets').rglob('*'):
        if path.is_file():out.write(path,path.relative_to(apk).as_posix())

sys.path.insert(0,str(PANEL/'python-deps'))
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs7
from cryptography.x509.oid import NameOID
keyfile=ROOT/'app-signing-key.pem';certfile=ROOT/'app-signing-cert.pem'
if keyfile.exists()!=certfile.exists():raise RuntimeError('Signing identity incomplete; refusing to regenerate it')
if not keyfile.exists():
    key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Tuya Wall Panel development')])
    now=datetime.datetime.now(datetime.timezone.utc)
    cert=x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(days=1)).not_valid_after(now+datetime.timedelta(days=3650)).sign(key,hashes.SHA256())
    with keyfile.open('xb') as out:out.write(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    with certfile.open('xb') as out:out.write(cert.public_bytes(serialization.Encoding.PEM))
key=serialization.load_pem_private_key(keyfile.read_bytes(),password=None);cert=x509.load_pem_x509_certificate(certfile.read_bytes())
def digest(data):return base64.b64encode(hashlib.sha256(data).digest()).decode('ascii')
with zipfile.ZipFile(BUILD/'unsigned.apk') as src:files=[(n,src.read(n)) for n in src.namelist() if not n.startswith('META-INF/')]
manifest=b'Manifest-Version: 1.0\r\nCreated-By: Tuya Wall Panel\r\n\r\n';sections=[]
for name,data in files:
    section=f'Name: {name}\r\nSHA-256-Digest: {digest(data)}\r\n\r\n'.encode();sections.append(section);manifest+=section
sf=f'Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: {digest(manifest)}\r\n\r\n'.encode()
for section in sections:
    name=section.split(b'\r\n',1)[0];sf+=name+b'\r\nSHA-256-Digest: '+digest(section).encode()+b'\r\n\r\n'
signature=pkcs7.PKCS7SignatureBuilder().set_data(sf).add_signer(cert,key,hashes.SHA256()).sign(serialization.Encoding.DER,[pkcs7.PKCS7Options.DetachedSignature,pkcs7.PKCS7Options.Binary,pkcs7.PKCS7Options.NoCapabilities])
output=ROOT/('tuya-wall-panel-'+version+'.apk');temporary=BUILD/'signed.apk'
with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as out:
    out.writestr('META-INF/MANIFEST.MF',manifest);out.writestr('META-INF/WALLPANEL.SF',sf);out.writestr('META-INF/WALLPANEL.RSA',signature)
    for name,data in files:out.writestr(name,data,compress_type=zipfile.ZIP_STORED if name=="assets/intercom-chime.wav" else zipfile.ZIP_DEFLATED)
shutil.copyfile(temporary,output)
(ROOT/'build-result.json').write_text(json.dumps({'version':version,'package':'local.wallpanel.app','apk':output.name,'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'installed':False,'hardware_takeover':False,'supported_abis':['armeabi-v7a','arm64-v8a'],'a133_hardware_ready':False},indent=2),encoding='utf-8')
print('BUILT',output.name,output.stat().st_size,'bytes. Not installed.')
run(sys.executable,ROOT/'build_setup_package.py')
