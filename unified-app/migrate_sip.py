"""One explicit panel SIP handoff. Passwords stay in memory or protected device files."""
from pathlib import Path
import argparse,datetime,json,re,shutil,subprocess,time,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent
def legacy_preferences(raw):
    recovered=False
    try:root=ET.fromstring(raw)
    except ET.ParseError:
        # Earlier manual ADB edits removed XML attribute quotes. Recover only the
        # known preference-map syntax in memory; leave the original file untouched.
        body=raw[raw.index('<map'):]
        def quoted(match):return re.sub(r'\b(name|value)=([^\s\"\x27<>/=]+)',lambda value:value.group(1)+'="'+value.group(2)+'"',match.group(0))
        body=re.sub(r'<[^>]+>',quoted,body);root=ET.fromstring(body);recovered=True
    if root.tag!='map' or any(e.tag not in ('string','int','boolean','long','float','set') for e in root):raise ValueError('Unexpected legacy preference format')
    return {e.get('name'):e.text if e.tag=='string' else e.get('value') for e in root},recovered
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--device',required=True);parser.add_argument('--rollback',action='store_true');parser.add_argument('--adb',default=shutil.which('adb') or (r'C:\adb\adb.exe' if Path(r'C:\adb\adb.exe').exists() else 'adb'));args=parser.parse_args()
    def adb(*command,check=True,data=None):
        result=subprocess.run([args.adb,'-s',args.device,*command],input=data,capture_output=True,timeout=30)
        if check and result.returncode:raise RuntimeError('ADB operation failed: '+command[0])
        return result
    def shell(*command,check=True):return adb('shell',*command,check=check).stdout.decode(errors='replace').strip()
    if shell('id','-u')!='0':raise RuntimeError('This handoff requires authorized ADB root')
    if args.device.split(':')[0]=='172.16.1.11':raise RuntimeError('The TV is not a SIP migration target')
    app='/data/user/0/local.wallpanel.app';legacy='/data/user/0/com.panel.intercom';pending=app+'/files/sip-migration-import.json';pointer='/data/local/wallpanel/last-sip-migration'
    uid=shell('stat','-c','%u',app)
    if not uid.isdigit():raise RuntimeError('Unified app is not installed')
    def import_data(payload):
        adb('shell','umask 077; cat > '+pending,data=json.dumps(payload).encode())
        shell('chown',uid+':'+uid,pending);shell('chmod','600',pending)
        shell('am','start','-W','-n','local.wallpanel.app/.SipSettingsActivity')
        until=time.monotonic()+10
        while time.monotonic()<until:
            if adb('shell','test','-f',pending,check=False).returncode!=0:return
            time.sleep(.25)
        raise RuntimeError('The app did not finish the protected settings import')
    def status():
        try:return json.loads(shell('cat',app+'/files/sip-runtime-status.json'))
        except (ValueError,RuntimeError):return {}
    def restore(old):
        if status().get('call_state') in ('RINGING','IN_CALL','ENDING'):raise RuntimeError('End the current intercom call before rollback')
        import_data({'disable':True});shell('am','stopservice','-n','local.wallpanel.app/.SipService')
        state=old['legacy_enabled_state'];commands={0:'default-state',1:'enable',2:'disable',3:'disable-user',4:'disable-until-used'}
        shell('pm',commands[state],'--user','0','com.panel.intercom')
        if state in (0,1):shell('am','startservice','-n','com.panel.intercom/.SipService')
    if args.rollback:
        backup=shell('cat',pointer)
        if not re.fullmatch(r'/data/local/wallpanel/before-sip-migration\.[0-9TZ-]+',backup):raise RuntimeError('Invalid backup location')
        old=json.loads(shell('cat',backup+'/migration.json'));restore(old);print('Standalone SIP restored; unified SIP disabled. No test calls placed.');return
    version=re.search(r'versionName=([^\s]+)',shell('dumpsys','package','local.wallpanel.app')).group(1)
    if tuple(int(x) for x in version.split('.'))<(0,12,0):raise RuntimeError('Install unified app 0.12.0 first')
    # RTP exists only during a live media session in the old app. Do not interrupt it.
    raw=shell('cat',legacy+'/shared_prefs/intercom_config.xml');saved,recovered=legacy_preferences(raw)
    required=('sip_user','sip_password','sip_server')
    if any(k not in saved for k in required):raise RuntimeError('The standalone app has no complete saved SIP account; configure it privately in Intercom settings')
    sip_port=int(saved.get('sip_port',5060));rtp_port=int(saved.get('rtp_port',10000))
    for table in ('/proc/net/udp','/proc/net/udp6'):
        udp=shell('cat',table)
        if any(len(line.split())>1 and line.split()[1].endswith(':'+format(rtp_port,'04X')) for line in udp.splitlines()[1:]):raise RuntimeError('An RTP session is active; end the call before migration')
    logs=adb('logcat','-d','-t','500','-s','SipService:I',check=False).stdout.decode(errors='replace')
    events=re.findall(r'(Call ringing, waiting for answer|Call answered, 200 OK sent|Hanging up call|BYE received, call ended|Received SIP request: CANCEL|Received SIP request: BYE)',logs)
    if events and events[-1] in ('Call ringing, waiting for answer','Call answered, 200 OK sent'):raise RuntimeError('The old SIP log indicates an active call; end it before migration')
    package=shell('dumpsys','package','com.panel.intercom');state=re.search(r'User 0:.*?\benabled=([0-4])',package)
    if not state:raise RuntimeError('Cannot establish the standalone package state')
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');backup='/data/local/wallpanel/before-sip-migration.'+stamp
    shell('mkdir','-p',backup);shell('chmod','700',backup)
    shell('cp',legacy+'/shared_prefs/intercom_config.xml',backup+'/intercom_config.xml');shell('chmod','600',backup+'/intercom_config.xml')
    shell('cp',app+'/shared_prefs/wall_panel.xml',backup+'/wall_panel.xml');shell('chmod','600',backup+'/wall_panel.xml')
    old={'device':args.device,'legacy_enabled_state':int(state.group(1)),'backup':backup,'unified_version':version,'legacy_xml_recovered_in_memory':recovered,'physical_tests_performed':False}
    adb('shell','umask 077; cat > '+backup+'/migration.json',data=json.dumps(old).encode());adb('shell','umask 077; cat > '+pointer,data=(backup+'\n').encode())
    output=r/'sip-migration-backups'/stamp;output.mkdir(parents=True)
    apk=shell('pm','path','com.panel.intercom').splitlines()[0].removeprefix('package:');adb('pull',apk,str(output/'PanelIntercom-original.apk'))
    config={'v':1,'server':saved['sip_server'],'server_port':5060,'user':saved['sip_user'],'auth_user':'','password':saved['sip_password'],'local_address':saved.get('local_ip',''),'sip_port':sip_port,'rtp_port':rtp_port,'auto_answer':saved.get('auto_answer','true')=='true','ring_seconds':45,'volume':75,'aec':True,'noise_suppression':True,'gain_control':True}
    print('Backups saved. Moving SIP ownership; no calls, tones or microphone tests will run.',flush=True)
    try:
        shell('pm','disable-user','--user','0','com.panel.intercom');shell('am','force-stop','com.panel.intercom');shell('pm','grant','local.wallpanel.app','android.permission.RECORD_AUDIO')
        import_data({'configuration':config,'enable':True});until=time.monotonic()+40;current={}
        while time.monotonic()<until:
            current=status()
            if current.get('registered'):break
            time.sleep(1)
        if not current.get('registered'):raise RuntimeError('Integrated SIP did not register: '+str(current.get('detail','no status')))
        # Check storage confidentiality without displaying any original account values.
        xml=shell('cat',app+'/shared_prefs/sip_configuration.xml');prefs=ET.fromstring(xml);cipher=prefs.find("string[@name='configuration']")
        if cipher is None or ':' not in (cipher.text or '') or not (cipher.text or '').split(':',1)[0]:raise RuntimeError('SIP encrypted storage check failed')
        old.update(registered=True,encrypted_settings=True,legacy_disabled=True,import_file_removed=True,call_state=current.get('call_state'),tests='registration/status only; physical call tests remain user tests')
        (r/'sip-migration-result.json').write_text(json.dumps(old,indent=2)+'\n')
        if current.get('call_state')=='IDLE':shell('am','start','-W','-n','local.wallpanel.app/.HomeActivity')
        print(json.dumps(old,indent=2))
    except Exception:
        restore(old);print('Standalone fallback restored after migration failure.',flush=True);raise
if __name__=='__main__':
    try:main()
    except Exception as error:raise SystemExit('SIP migration stopped: '+str(error))
