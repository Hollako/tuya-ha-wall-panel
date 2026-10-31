"""One-time setup for a verified G10 panel. Standard Python + Android platform-tools."""
import argparse,hashlib,json,re,shutil,subprocess,sys,tempfile,uuid,zipfile
from pathlib import Path
FILES={name:('assets/provision/'+name if name.endswith('.sh') else 'assets/hardware/g10-arm64/'+name) for name in ('wallpanel-core','wallpanel-capture','wallpanel-privilege','wallpanel-system-ui.jar','setup-panel.sh','bootstrap-panel.sh')}
def select_abi(supported):
    items=supported.replace(',', ' ').split() if isinstance(supported,str) else supported
    if 'arm64-v8a' in items:return 'arm64'
    if 'armeabi-v7a' in items:return 'arm32'
    raise ValueError('This APK requires ARM32 or ARM64 Android')

def payload(apk,abi='arm64',profile='g10'):
    if profile not in ('g10','a133'):raise ValueError('Unsupported profile')
    if abi not in ('arm64','arm32'):raise ValueError('Unsupported ABI')
    if profile=='a133' and abi!='arm32':raise ValueError('A133 hardware is verified for ARM32 only')
    with zipfile.ZipFile(apk) as archive:
        manifest=json.loads(archive.read('assets/provision/manifest.json'))
        if manifest.get('v')!=1 or manifest.get('profile')!='g10' or set(manifest.get('files',{}))!=set(FILES):raise ValueError('Unsupported setup package')
        selected=manifest if profile=='g10' else manifest.get('profiles',{}).get(profile,{})
        sums=selected['files'] if profile=='g10' and abi=='arm64' else selected.get('variants',{}).get(abi,{}).get('files',{})
        if set(sums)!=set(FILES):raise ValueError('Missing ABI-specific setup files')
        assets={name:('assets/provision/'+('setup-a133.sh' if profile=='a133' and name=='setup-panel.sh' else name) if name.endswith('.sh') else 'assets/hardware/'+profile+'-'+abi+'/'+name) for name in FILES}
        data={name:archive.read(asset) for name,asset in assets.items()}
        for name,content in data.items():
            if len(content)>1024*1024 or hashlib.sha256(content).hexdigest()!=sums[name]:raise ValueError('Setup integrity check failed: '+name)
            if name.endswith('.sh') and b'\r' in content:raise ValueError('Android scripts require LF line endings')
        return data
def main(argv=None):
    parser=argparse.ArgumentParser(description='Prepare root support, hardware setup and conflicting startup components on a verified G10 panel. Never reboots or tests outputs.')
    parser.add_argument('--profile',choices=('g10','a133'),default='g10',help='Explicit verified hardware profile; A133 is ARM32 only')
    parser.add_argument('--device',required=True,help='Explicit ADB serial or PANEL_IP:5555')
    parser.add_argument('--apk',type=Path,help='Tuya Wall Panel APK from this setup kit')
    parser.add_argument('--adb',default=shutil.which('adb') or (r'C:\adb\adb.exe' if Path(r'C:\adb\adb.exe').exists() else 'adb'))
    modes=parser.add_mutually_exclusive_group()
    modes.add_argument('--app-only',action='store_true',help='Install APK only; no root, vendor changes, launcher selection or hardware setup')
    modes.add_argument('--bootstrap-only',action='store_true',help='Upgrade root support without applying package/startup/configuration changes')
    args=parser.parse_args(argv)
    if args.apk is None:
        candidates=list(Path(__file__).resolve().parent.glob('tuya-wall-panel-*.apk'))
        if len(candidates)!=1:parser.error('Specify --apk when there is not exactly one APK beside the installer')
        args.apk=candidates[0]
    payload(args.apk) # Validate the signed-package contents before contacting a device.
    def adb(*command,check=True):
        result=subprocess.run([args.adb,'-s',args.device,*command],capture_output=True,timeout=180)
        if check and result.returncode:raise RuntimeError('ADB operation failed: '+command[0])
        return result
    if ':' in args.device:subprocess.run([args.adb,'connect',args.device],capture_output=True,timeout=20)
    adb('get-state')
    abi=select_abi(adb('shell','getprop','ro.product.cpu.abilist').stdout.decode())
    data=payload(args.apk,abi,args.profile)
    if args.app_only:
        adb('install','-r',str(args.apk.resolve()))
        print('APK installed for '+abi+'. No root setup, vendor changes or launcher selection performed.')
        return 0
    root=[]
    if adb('shell','id','-u').stdout.strip()!=b'0':
        candidate=adb('shell','su','0','id','-u',check=False)
        if candidate.returncode==0 and candidate.stdout.strip()==b'0':root=['su','0']
        else:
            adb('root',check=False)
            if ':' in args.device:subprocess.run([args.adb,'connect',args.device],capture_output=True,timeout=20)
            adb('wait-for-device')
            if adb('shell','id','-u').stdout.strip()!=b'0':raise RuntimeError('This panel has not granted ADB root. No system changes were made.')
    def shell(*command,check=True):return adb('shell',*root,*command,check=check)
    remote='/data/local/tmp/wall-panel-setup.'+uuid.uuid4().hex[:12]
    shell('mkdir','-p',remote)
    transport_uid=adb('shell','id','-u').stdout.decode().strip()
    if not transport_uid.isdigit():raise RuntimeError('Cannot determine ADB transfer owner')
    shell('chown',transport_uid,remote);shell('chmod','700',remote)
    print('Checking hardware; no output tests will run.',flush=True)
    with tempfile.TemporaryDirectory(prefix='wall-panel-setup-') as temp:
        for name,content in data.items():
            path=Path(temp)/name;path.write_bytes(content);adb('push',str(path),remote+'/'+name)
            if shell('sha256sum',remote+'/'+name).stdout.decode().split()[0]!=hashlib.sha256(content).hexdigest():raise RuntimeError('Transfer checksum mismatch')
            shell('chmod','700',remote+'/'+name)
    probe=shell(remote+'/wallpanel-core','--probe')
    if not json.loads(probe.stdout).get('layout_matched'):raise RuntimeError('Unsupported wiring. The APK and vendor setup were not changed.')
    if shell('getenforce').stdout.strip()!=b'Permissive':raise RuntimeError('Additional Android permission policy setup is required. Security settings were not weakened.')
    print('Verified '+args.profile+' layout. Installing APK while preserving app settings.',flush=True)
    adb('install','-r',str(args.apk.resolve()))
    mode='bootstrap-only' if args.bootstrap_only else 'apply'
    result=shell('/system/bin/sh',remote+'/bootstrap-panel.sh',remote,mode,check=False)
    lines=result.stdout.decode(errors='replace').strip().splitlines()
    try:status=json.loads(lines[-1])
    except (IndexError,ValueError):raise RuntimeError('Setup did not return a result; inspect the panel installation screen')
    if result.returncode or not status.get('ok'):raise RuntimeError('Setup needs attention: '+status.get('error','bootstrap_failed'))
    adb('shell','am','start','-W','-n','local.wallpanel.app/.MainActivity')
    print('Panel support installed.' if args.bootstrap_only else 'Panel prepared with backups; conflicting Tuya components disabled.')
    print('Review profile/modules and HA/MQTT settings in the app, then Start / apply hardware. Reboot and physical checks remain yours.')
    return 0
if __name__=='__main__':
    try:sys.exit(main())
    except (OSError,ValueError,RuntimeError,subprocess.SubprocessError) as error:print('Setup stopped:',error,file=sys.stderr);sys.exit(1)
