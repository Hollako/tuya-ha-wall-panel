"""Existing trusted-root receiver -> unified module, with explicit apply/rollback.

No APK installation, reboot, audio test, firmware remount or hardware mapping.
Default is a read-only plan. Keep the standalone APK/data until user acceptance.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,re,shlex,subprocess,tempfile,time,xml.etree.ElementTree as ET,zipfile

OLD='com.wallpanel.audioreceiver'
NEW='local.wallpanel.app'
BASE='/data/local/wpar-airplay'
OLD_DATA='/data/user/0/'+OLD
NEW_DATA='/data/user/0/'+NEW
SERVICE='wpar_airplay_ptp'

def import_settings(xml):
    """Allowlist only receiver identity/calibration. Never carry API/HA passwords."""
    root=ET.fromstring(xml)
    values={item.get('name'):item.text if item.tag=='string' else item.get('value') for item in root}
    name=values.get('receiver_name','Wall Panel')
    offset=int(values.get('latency_offset_ms','0'));trim=int(values.get('volume_trim','100'));port=int(values.get('rtsp_port','7000'))
    iface=values.get('network_interface','')
    if not (1<=len(name)<=63) or '\n' in name or '\r' in name or not -500<=offset<=500 or not 0<=trim<=100 or not 1024<=port<=65535 or not re.fullmatch(r'[A-Za-z0-9_.-]{0,32}',iface):
        raise ValueError('Invalid saved receiver settings; no migration performed')
    return dict(v=1,receiver_name=name,latency_offset_ms=offset,volume_trim=trim,rtsp_port=port,network_interface=iface)

class Device:
    def __init__(self,adb,serial):self.prefix=[adb,'-s',serial]
    def command(self,*args,check=True,binary=False):
        result=subprocess.run(self.prefix+list(args),stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=45)
        if check and result.returncode:raise RuntimeError('ADB operation failed: '+args[0]+' (private output withheld)')
        return result.stdout if binary else result.stdout.decode('utf-8','replace').strip()
    def shell(self,command,check=True):return self.command('shell',command,check=check)
    def read(self,path):return self.command('exec-out','cat',path,binary=True)
    def exists(self,path):return self.shell('if test -f '+shlex.quote(path)+'; then echo yes; fi')=='yes'
    def write(self,path,data,uid='0',mode='600'):
        with tempfile.TemporaryDirectory(prefix='wallpanel-receiver-') as folder:
            local=Path(folder)/'payload';local.write_bytes(data)
            staged=BASE+'/cutover-stage'
            self.command('push',str(local),staged)
        expected=hashlib.sha256(data).hexdigest()
        self.shell('chown '+uid+':'+uid+' '+staged+' && chmod '+mode+' '+staged)
        if self.shell('sha256sum '+staged).split()[0]!=expected:raise RuntimeError('Transfer checksum mismatch')
        self.shell('mv '+staged+' '+shlex.quote(path)+' && restorecon '+shlex.quote(path))
    def exact_pids(self,name):
        result=self.shell('for p in /proc/[0-9]*; do if [ "$(readlink "$p/exe")" = "'+BASE+'/'+name+'" ]; then echo "${p##*/}"; fi; done')
        return [int(value) for value in result.split() if value.isdigit()]
    def stop_helper(self):
        self.shell('setprop ctl.stop '+SERVICE)
        for pid in self.exact_pids('wpar-ptp-helper'):self.shell('kill -TERM '+str(pid))
        for _ in range(40):
            if not self.exact_pids('wpar-ptp-helper') and not self.exact_pids('nqptp'):return
            time.sleep(.25)
        raise RuntimeError('Existing timing processes did not stop; no UID transfer performed')
    def start_helper(self):
        self.shell('setprop ctl.start '+SERVICE)
        for _ in range(30):
            if self.exact_pids('wpar-ptp-helper'):return
            time.sleep(.2)
        raise RuntimeError('Timing helper did not start; use rollback')
    def launch(self):self.shell('am start -n '+NEW+'/.HomeActivity')

def check_idle(device):
    path=NEW_DATA+'/files/sip-runtime-status.json'
    if device.exists(path) and json.loads(device.read(path)).get('call_state')!='IDLE':
        raise RuntimeError('Intercom is busy; retry after the call ends')

def rollback(device,backup):
    if not re.fullmatch(re.escape(BASE)+r'/before-unified-[0-9]{8}-[0-9]{6}',backup):raise ValueError('Invalid backup directory')
    receipt=json.loads(device.read(backup+'/receipt.json'))
    if receipt.get('v')!=1 or any(type(receipt.get(k)) is not int or not 10000<=receipt[k]<=19999 for k in ('old_uid','new_uid')) or receipt.get('old_enabled_state') not in (0,1,3):
        raise ValueError('Invalid rollback receipt')
    check_idle(device)
    if not device.shell('pm path '+OLD,check=False).startswith('package:'):
        if not device.exists(backup+'/standalone.apk'):raise RuntimeError('Standalone APK was removed; restore its backed-up APK before rollback')
        if 'Success' not in device.shell('pm install -r '+backup+'/standalone.apk'):raise RuntimeError('Standalone APK restore failed')
    restored_uid=int(device.shell('stat -c %u '+OLD_DATA))
    if not 10000<=restored_uid<=19999:raise RuntimeError('Unexpected restored receiver UID')
    receipt['old_uid']=restored_uid
    device.shell('am force-stop '+OLD)
    device.shell('am force-stop '+NEW)
    device.stop_helper()
    # Retire the pending import before any component can cold-start the launcher.
    pending=NEW_DATA+'/files/receiver-migration.json'
    if device.exists(pending):device.shell('mv '+pending+' '+backup+'/unused-import.json')
    device.shell('mkdir -p '+OLD_DATA+'/shared_prefs && chown '+str(restored_uid)+':'+str(restored_uid)+' '+OLD_DATA+'/shared_prefs && chmod 700 '+OLD_DATA+'/shared_prefs')
    source_prefs=OLD_DATA+'/shared_prefs/wpar_prefs.xml'
    device.shell('cp '+backup+'/standalone-prefs.xml '+source_prefs+' && chown '+str(restored_uid)+':'+str(restored_uid)+' '+source_prefs+' && chmod 600 '+source_prefs+' && restorecon '+source_prefs)
    device.shell('cp '+backup+'/start-helper.sh '+BASE+'/start-helper.sh && chown 0:0 '+BASE+'/start-helper.sh && chmod 700 '+BASE+'/start-helper.sh')
    device.shell('chown -R '+str(receipt['old_uid'])+':'+str(receipt['old_uid'])+' '+BASE+'/shm')
    # Preserve all unrelated unified app settings. Restore only receiver preferences.
    target=NEW_DATA+'/shared_prefs/wpar_prefs.xml'
    if receipt['unified_preferences_existed']:
        device.shell('cp '+backup+'/unified-prefs.xml '+target+' && chown '+str(receipt['new_uid'])+':'+str(receipt['new_uid'])+' '+target+' && chmod 600 '+target+' && restorecon '+target)
    else:
        # Disable this module through its fixed intent; do not delete app storage.
        device.shell('am start-foreground-service -n '+NEW+'/com.wallpanel.audioreceiver.ReceiverService -a com.wallpanel.audioreceiver.STOP')
        device.shell('am force-stop '+NEW)
    device.shell('pm '+{0:'default-state',1:'enable',3:'disable-user'}[receipt['old_enabled_state']]+' --user 0 '+OLD)
    device.start_helper()
    device.launch()
    if receipt['old_receiver_enabled'] and receipt['old_enabled_state'] in (0,1):
        device.shell('am start-foreground-service -n '+OLD+'/.ReceiverService -a com.wallpanel.audioreceiver.ENSURE_STARTED')
    print('Restored standalone receiver ownership. No reboot or playback test performed.')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',required=True);parser.add_argument('--adb',default=r'C:\adb\adb.exe')
    parser.add_argument('--apk',type=Path);parser.add_argument('--apply',action='store_true');parser.add_argument('--rollback')
    args=parser.parse_args();device=Device(args.adb,args.device)
    if device.shell('id -u')!='0' or device.shell('getenforce')!='Permissive':raise RuntimeError('Existing trusted ADB root and Permissive policy required; nothing changed')
    if args.rollback:
        if not args.apply:raise ValueError('Rollback requires --apply')
        rollback(device,args.rollback);return
    root=Path(__file__).resolve().parent
    apk=args.apk or root/json.loads((root/'build-result.json').read_text())['apk']
    info=device.shell('dumpsys package '+NEW)
    version=re.search(r'versionCode=(\d+)',info)
    if not version or int(version.group(1))<43:raise RuntimeError('Install the merged unified APK first; this script never installs APKs')
    old_uid=int(device.shell('stat -c %u '+OLD_DATA));new_uid=int(device.shell('stat -c %u '+NEW_DATA))
    if not all(10000<=uid<=19999 for uid in (old_uid,new_uid)) or old_uid==new_uid:raise RuntimeError('Invalid or unexpected package UIDs')
    if device.shell('getprop init.svc.'+SERVICE) not in ('running','stopped','restarting'):raise RuntimeError('Expected existing PTP init service is missing; fresh-device provisioning is separate')
    # Explicitly reject symlinked helper/shm paths before any recursive ownership change.
    device.shell('test ! -L '+BASE+' && test ! -L '+BASE+'/shm && test -d '+BASE+'/shm && test -z "$(find '+BASE+'/shm -type l)"')
    device.shell('test ! -L '+NEW_DATA+'/files && test ! -L '+NEW_DATA+'/shared_prefs && test ! -L '+OLD_DATA+'/shared_prefs/wpar_prefs.xml')
    with zipfile.ZipFile(apk) as archive:manifest=json.loads(archive.read('assets/receiver/native-payloads.json'))
    abi='arm64-v8a' if 'arm64-v8a' in device.shell('getprop ro.product.cpu.abilist').split(',') else 'armeabi-v7a'
    for name in ('wpar-ptp-helper','nqptp'):
        if device.shell('sha256sum '+BASE+'/'+name).split()[0]!=manifest['abis'][abi][name]['sha256']:
            raise RuntimeError('Existing receiver helper differs from tested baseline; no automatic overwrite')
    wrapper=device.read(BASE+'/start-helper.sh')
    if NEW.encode() in wrapper and device.exists(NEW_DATA+'/shared_prefs/wpar_prefs.xml'):
        if device.shell('stat -c %u '+BASE+'/ptp.sock')==str(new_uid) and any(item.get('name')=='migration_id' for item in ET.fromstring(device.read(NEW_DATA+'/shared_prefs/wpar_prefs.xml'))):
            print('Unified receiver ownership is already prepared; nothing changed.');return
    if OLD.encode() not in wrapper:raise RuntimeError('Helper owner has already changed or is unrecognized; inspect before retrying')
    old_xml=device.read(OLD_DATA+'/shared_prefs/wpar_prefs.xml')
    settings=import_settings(old_xml)
    target=NEW_DATA+'/shared_prefs/wpar_prefs.xml';existing=device.exists(target)
    if existing:
        prior=ET.fromstring(device.read(target))
        migrated=any(item.get('name')=='migration_id' for item in prior)
        active=any(item.get('name')=='enabled' and item.get('value')=='true' for item in prior)
        if migrated and active:raise RuntimeError('Receiver was already migrated; use settings or recorded rollback')
    enabled_match=re.search(r'User 0:.*?enabled=(\d+)',device.shell('dumpsys package '+OLD))
    if not enabled_match or int(enabled_match.group(1)) not in (0,1,3):raise RuntimeError('Unrecognized standalone package state')
    check_idle(device)
    print('Ready: verified existing timing helper, package UIDs and receiver calibration.')
    if not args.apply:
        print('Read-only plan. --apply stops both apps briefly, disables the standalone, transfers only receiver ownership/settings, then opens unified Home. No reboot or hardware tests.');return
    stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S');backup=BASE+'/before-unified-'+stamp
    device.shell('mkdir '+backup+' && chmod 700 '+backup)
    for source,name in ((BASE+'/start-helper.sh','start-helper.sh'),(OLD_DATA+'/shared_prefs/wpar_prefs.xml','standalone-prefs.xml')):
        device.shell('cp '+source+' '+backup+'/'+name+' && chmod 600 '+backup+'/'+name)
    if existing:device.shell('cp '+target+' '+backup+'/unified-prefs.xml && chmod 600 '+backup+'/unified-prefs.xml')
    source_values={item.get('name'):item.get('value') for item in ET.fromstring(old_xml)}
    receipt=dict(v=1,old_uid=old_uid,new_uid=new_uid,old_enabled_state=int(enabled_match.group(1)),old_receiver_enabled=source_values.get('enabled')=='true',unified_preferences_existed=existing)
    device.write(backup+'/receipt.json',json.dumps(receipt).encode())
    print('Rollback backup:',backup)
    try:
        check_idle(device)
        device.shell('am force-stop '+OLD);device.shell('pm disable-user --user 0 '+OLD)
        device.shell('am force-stop '+NEW);device.stop_helper()
        device.shell('mkdir -p '+NEW_DATA+'/files && chown '+str(new_uid)+':'+str(new_uid)+' '+NEW_DATA+'/files && chmod 700 '+NEW_DATA+'/files && restorecon '+NEW_DATA+'/files')
        device.shell('chown -R '+str(new_uid)+':'+str(new_uid)+' '+BASE+'/shm')
        device.write(BASE+'/start-helper.sh',wrapper.replace(OLD.encode(),NEW.encode()),mode='700')
        settings['id']='unified_'+stamp.replace('-','_')
        device.write(NEW_DATA+'/files/receiver-migration.json',json.dumps(settings).encode(),uid=str(new_uid))
        device.start_helper()
        # Android may restart its default Home immediately after force-stop. Do
        # this again only after the import and authenticated helper are ready.
        device.shell('am force-stop '+NEW)
        device.launch()
        for _ in range(30):
            if not device.exists(NEW_DATA+'/files/receiver-migration.json'):
                loaded=ET.fromstring(device.read(target))
                if any(item.get('name')=='migration_id' and item.text==settings['id'] for item in loaded):
                    print('Migrated receiver settings and helper ownership; standalone app/data retained disabled. Please test audio and intercom.');return
            time.sleep(.5)
        raise RuntimeError('Unified receiver import did not confirm')
    except Exception:
        print('Cutover did not finish; restoring saved receiver ownership.')
        rollback(device,backup)
        raise

if __name__=='__main__':main()
