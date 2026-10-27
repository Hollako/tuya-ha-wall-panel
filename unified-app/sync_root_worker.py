"""Backed-up upgrade of the fixed root worker; no hardware test commands."""
from pathlib import Path
import argparse,datetime,hashlib,json,re,subprocess,time

def sync(adb,device,binary):
    binary=Path(binary);expected=hashlib.sha256(binary.read_bytes()).hexdigest()
    target='/data/local/wallpanel/wallpanel-core'
    def run(*args):
        readonly=len(args)>1 and args[0]=='shell' and args[1] in ('id','cat','readlink','sha256sum')
        for attempt in range(3 if readonly else 1):
            try:
                p=subprocess.run([str(adb),'-s',device,*args],capture_output=True,timeout=120 if args[0]=='push' else 30)
                if not p.returncode:return p.stdout.decode().strip()
            except subprocess.TimeoutExpired:
                if not readonly:raise
            if readonly and attempt<2:
                subprocess.run([str(adb),'disconnect',device],capture_output=True,timeout=30)
                subprocess.run([str(adb),'connect',device],capture_output=True,timeout=40)
                time.sleep(1)
            else:raise RuntimeError('ADB operation failed: '+args[0])
    def status():return json.loads(run('shell','cat','/dev/wallpanel-app-status.json'))
    def running(data):
        pid=str(data.get('worker_pid',0))
        if not re.fullmatch(r'[1-9][0-9]*',pid):raise RuntimeError('Invalid worker PID')
        exe=run('shell','readlink','/proc/'+pid+'/exe')
        if exe not in (target,target+' (deleted)'):raise RuntimeError('Unexpected worker executable')
        return pid,run('shell','sha256sum','/proc/'+pid+'/exe').split()[0]
    assert run('shell','id','-u')=='0','Trusted ADB root is required'
    call=json.loads(run('shell','cat','/data/user/0/local.wallpanel.app/files/sip-runtime-status.json'))
    assert call.get('call_state')=='IDLE','Call active: update deferred'
    pid,old_hash=running(status())
    if old_hash==expected:return {'worker_updated':False,'running_hash_verified':True,'worker_pid':int(pid)}
    assert run('shell','sha256sum',target).split()[0]==old_hash,'Installed and running worker already differ'
    backup='/data/local/wallpanel/before-native-update.'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    run('shell','mkdir','-m','700',backup);run('shell','cp','-p',target,backup+'/wallpanel-core')
    staged=target+'.update';run('push',str(binary.resolve()),staged)
    assert run('shell','sha256sum',staged).split()[0]==expected,'Transfer checksum failed'
    run('shell','chown','0:0',staged);run('shell','chmod','700',staged)
    assert json.loads(run('shell',staged,'--probe')).get('layout_matched'),'Hardware layout mismatch'
    run('shell','mv',staged,target)
    try:
        current_pid,current_hash=running(status())
        assert current_pid==pid and current_hash==old_hash,'Worker identity changed'
        # Existing app recovery relaunches this exact worker. Broker/SIP stay up.
        run('shell','kill','-TERM',pid)
        until=time.monotonic()+40
        while time.monotonic()<until:
            time.sleep(1)
            try:
                data=status();new_pid,new_hash=running(data)
                if new_pid!=pid and new_hash==expected and all(data.get(k) for k in ('online','knob_input_ready','knob_display_ready')):
                    return {'worker_updated':True,'backup':backup,'running_hash_verified':True,'worker_pid':int(new_pid),'physical_tests':False,'reboot':False}
            except (OSError,ValueError,RuntimeError):pass
        raise RuntimeError('Updated worker did not become ready')
    except Exception:
        run('shell','cp','-p',backup+'/wallpanel-core',target+'.restore');run('shell','mv',target+'.restore',target)
        try:
            current_pid,current_hash=running(status())
            if current_hash==expected:run('shell','kill','-TERM',current_pid)
        except (OSError,ValueError,RuntimeError):pass
        raise

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',required=True);parser.add_argument('--adb',default=r'C:\adb\adb.exe')
    parser.add_argument('--binary',type=Path,default=Path(__file__).resolve().parent/'assets/hardware/g10-arm64/wallpanel-core')
    args=parser.parse_args();result=sync(args.adb,args.device,args.binary)
    (Path(__file__).resolve().parent/'root-worker-sync-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
