"""Exercise actual retirement/recovery scripts against a fake Android filesystem."""
from pathlib import Path
import json,os,subprocess,tempfile,time,unittest
ROOT=Path(__file__).resolve().parent
FAKE='''#!/usr/bin/python3
import json,os,sys,time
from pathlib import Path
r=Path(os.environ['FAKE_PANEL']);p=r/'meta.json';meta=json.loads(p.read_text());name=Path(sys.argv[0]).name;a=sys.argv[1:]
with (r/'commands.log').open('a') as f:f.write(name+' '+' '.join(a)+'\\n')
if name=='getprop':print(meta['services'][a[0].split('.')[-1].replace('panalume_','')])
elif name=='setprop':
    if a[1]=='wallpanel_privilege':sys.exit(0)
    part=a[1].replace('panalume_','')
    if not (os.environ.get('FAIL_STOP')==part and a[0]=='ctl.stop'):meta['services'][part]='stopped' if a[0]=='ctl.stop' else 'running';p.write_text(json.dumps(meta))
elif name=='pm':
    meta['audio']=0 if a[0]=='default-state' else 1 if a[0]=='enable' else 3;p.write_text(json.dumps(meta))
elif name=='sleep':time.sleep(4 if a[0]=='120' else min(float(a[0]),.005))
elif name=='mount' and os.environ.get('FAIL_MOUNT'):sys.exit(1)
'''
class RetirementTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='wallpanel-retirement-');self.r=Path(self.temp.name);self.state=self.r/'state';self.stage=self.r/'stage';self.init=self.r/'system/etc/init'
        for p in (self.state,self.stage,self.init,self.r/'bin',self.r/'privilege'):p.mkdir(parents=True,exist_ok=True)
        (self.r/'privilege/privilege.pid').write_text('999999999\n');(self.stage/'wallpanel-privilege.rc').write_text('NEW APP BOOTSTRAP\n')
        self.env={**os.environ,'FAKE_PANEL':str(self.r),'PATH':str(self.r/'bin')+':'+os.environ['PATH']}
        self.old={'services':{'mqtt':'running','panel':'running','network':'stopped','compat':'stopped'},'audio':0};(self.r/'meta.json').write_text(json.dumps(self.old));(self.r/'commands.log').touch()
        fake=self.r/'bin/fake';fake.write_text(FAKE);fake.chmod(0o755)
        for name in ('getprop','setprop','pm','am','mount','sync','sleep','restorecon','chown'):(self.r/'bin'/name).symlink_to(fake)
        for part in ('mcu','knob','audio','display','management'):(self.state/(part+'-gate.version')).write_text('1\n')
        for part in ('panel','mqtt','network','compat'):(self.state/('start-'+part+'.sh')).write_text('ORIGINAL '+part+'\n')
        for name in ('panel-mqtt','panel-backlight','panel-screenshot'):
            p=self.state/name;p.write_text('#!/bin/sh\nexit 0\n');p.chmod(0o700)
        for name in ('panalume-panel.rc','panalume-mqtt.rc'):(self.init/name).write_text('ORIGINAL '+name+'\n')
        self.prefs='<map><boolean name="runtime_enabled" value="true"/><boolean name="runtime_verified" value="true"/></map>'
        (self.r/'prefs.xml').write_text(self.prefs);(self.stage/'audio-enabled').write_text('0\n')
        for name in ('retire-fallbacks.sh','recover-fallbacks.sh'):
            s=(ROOT/name).read_text().replace('/data/local/panalume',str(self.state)).replace('/data/local/wallpanel',str(self.r/'privilege')).replace('/data/local/tmp/wallpanel-app-only',str(self.stage)).replace('/data/user/0/local.wallpanel.app/shared_prefs/wall_panel.xml',str(self.r/'prefs.xml')).replace('/system/etc/init',str(self.init)).replace('/system/bin/setsid','/usr/bin/setsid').replace('/system/bin/sh','/bin/sh')
            (self.stage/name).write_text(s)
    def tearDown(self):
        for p in self.state.glob('before-app-only.*/watchdog.pid'):
            try:os.kill(int(p.read_text()),15)
            except (ProcessLookupError,ValueError):pass
        self.temp.cleanup()
    def call(self,*args):return subprocess.run(['sh',str(self.stage/'retire-fallbacks.sh'),*args],env=self.env,capture_output=True,text=True,timeout=15)
    def prepared(self):
        out=self.call('prepare');self.assertEqual(out.returncode,0,out.stdout+out.stderr);return Path((self.state/'app-only.pending').read_text().strip())
    def restored(self):
        self.assertEqual(json.loads((self.r/'meta.json').read_text()),self.old)
        self.assertTrue((self.init/'panalume-panel.rc').exists());self.assertTrue((self.init/'panalume-mqtt.rc').exists())
        self.assertFalse((self.init/'wallpanel-privilege.rc').exists())
        self.assertFalse((self.state/'app-only.version').exists());self.assertTrue((self.state/'panel-mqtt').exists())
        self.assertEqual((self.state/'start-panel.sh').read_text(),'ORIGINAL panel\n')
    def test_prepare_commit_removes_only_known_startup(self):
        backup=self.prepared();self.assertFalse((self.init/'panalume-panel.rc').exists());self.assertFalse((self.state/'panel-mqtt').exists());self.assertEqual(json.loads((self.r/'meta.json').read_text())['audio'],3)
        self.assertEqual(self.call('commit').returncode,0);self.assertTrue((backup/'committed').exists());self.assertTrue((backup/'retired/panel-mqtt').exists())
        self.assertEqual((self.r/'prefs.xml').read_text(),self.prefs);self.assertNotIn('reboot',(self.r/'commands.log').read_text())
    def test_explicit_recovery_restores_files_services_and_audio(self):
        backup=self.prepared();out=subprocess.run(['sh',str(backup/'recover.sh'),str(backup)],env=self.env,capture_output=True,text=True,timeout=15);self.assertEqual(out.returncode,0,out.stderr);self.restored()
        self.assertIn('name="runtime_enabled" value="false"',(self.r/'prefs.xml').read_text())
    def test_stop_failure_rolls_back(self):
        self.env['FAIL_STOP']='mqtt';out=self.call('prepare');self.assertNotEqual(out.returncode,0)
        self.assertIn('setprop ctl.stop panalume_mqtt',(self.r/'commands.log').read_text())
        until=time.monotonic()+8
        while not list(self.state.glob('before-app-only.*/recovered')) and time.monotonic()<until:time.sleep(.05)
        self.assertTrue(list(self.state.glob('before-app-only.*/recovered')));self.restored()
    def test_preflight_failure_never_stops_services(self):
        self.env['FAIL_MOUNT']='1';self.assertNotEqual(self.call('prepare').returncode,0);self.restored();self.assertNotIn('setprop',(self.r/'commands.log').read_text())
    def test_abandoned_transaction_watchdog_restores(self):
        backup=self.prepared();until=time.monotonic()+8
        while not (backup/'recovered').exists() and time.monotonic()<until:time.sleep(.05)
        self.assertTrue((backup/'recovered').exists());self.restored()
    def test_repeat_prepare_is_refused(self):
        self.prepared();self.assertNotEqual(self.call('prepare').returncode,0);self.assertTrue((self.state/'app-only.version').exists())
if __name__=='__main__':unittest.main(verbosity=2)
