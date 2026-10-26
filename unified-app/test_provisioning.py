"""Factory-like fake Android setup; no live panel, root changes or hardware access."""
import json,os,signal,subprocess,tempfile,time,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent
FAKE='''#!/usr/bin/python3
import json,os,sys,time
from pathlib import Path
r=Path(os.environ['FAKE_SETUP']);path=r/'meta.json';m=json.loads(path.read_text());name=Path(sys.argv[0]).name;a=sys.argv[1:]
with (r/'commands').open('a') as f:f.write(name+' '+' '.join(a)+'\\n')
def save():path.write_text(json.dumps(m))
if name=='id':print(0)
elif name=='stat':print(10065)
elif name=='getenforce':print('Enforcing' if os.environ.get('ENFORCING') else 'Permissive')
elif name=='dumpsys':
    value=m['packages'].get(a[-1]);print('User 0: installed=true enabled='+str(value) if value is not None else 'Unknown package')
elif name=='pm':
    package=a[-1]
    if os.environ.get('HOLD_PACKAGE')==package and a[0]=='disable-user':(r/'holding').touch();time.sleep(30)
    if os.environ.get('FAIL_PACKAGE')==package and a[0]=='disable-user' and not m.get('failure_used'):m['failure_used']=True;save();sys.exit(1)
    m['packages'][package]={'default-state':0,'enable':1,'disable':2,'disable-user':3,'disable-until-used':4}[a[0]];save()
elif name=='getprop':print(m['services'].get(a[-1].replace('init.svc.',''),''))
elif name=='setprop':m['services'][a[1]]='running' if a[0]=='ctl.start' else 'stopped';save()
elif name=='settings':
    if a[0]=='get':print(m['awake'])
    else:m['awake']='null' if a[0]=='delete' else a[-1];save()
elif name=='cmd':
    if a[1]=='resolve-activity':print(m['home'])
    else:m['home']=a[-1];save()
elif name=='mount' and os.environ.get('READONLY'):sys.exit(1)
elif name=='sleep':time.sleep(3 if a[0]=='120' else min(float(a[0]),.005))
'''
class ProvisioningTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='wp-factory-');self.r=Path(self.temp.name);self.target=self.r/'support';self.state=self.r/'hardware';self.init=self.r/'system/etc/init';self.bin=self.r/'bin'
        for folder in (self.target,self.state,self.init,self.bin,self.r/'app'):folder.mkdir(parents=True,exist_ok=True)
        self.old={'packages':{'com.sznaner.bgmz9':0,'local.wallpanel.audio':1,'com.sznaner.pulldownmenu':0,'com.spotify.music':0},'services':{'panalume_panel':'running','panalume_mqtt':'running','panalume_network':'stopped','panalume_compat':'stopped'},'awake':'0','home':'com.sznaner.bgmz9/.MainActivity'}
        (self.r/'meta.json').write_text(json.dumps(self.old));(self.r/'commands').touch()
        fake=self.bin/'fake';fake.write_text(FAKE);fake.chmod(0o755)
        for name in ('id','stat','getenforce','dumpsys','pm','getprop','setprop','settings','cmd','mount','sleep','chown','restorecon','am','sync'):(self.bin/name).symlink_to(fake)
        self.env={**os.environ,'FAKE_SETUP':str(self.r),'PATH':str(self.bin)+':'+os.environ['PATH']}
        core=self.target/'wallpanel-core';core.write_text('#!/bin/sh\nif [ -n "$UNSUPPORTED" ];then echo \'{"layout_matched":false}\';else echo \'{"layout_matched":true}\';fi\n');core.chmod(0o700)
        for name in ('wallpanel-capture','wallpanel-privilege','wallpanel-system-ui.jar','start-privilege.sh'):(self.target/name).write_text('EXISTING\n')
        (self.state/'display-brightness').write_text('77\n');(self.state/'display-policy').write_text('v1 90 600 15\n');(self.state/'button-local-bindings').write_text('1 0\n0 2\n0 0\n0 0\n')
        (self.state/'panel-mqtt').write_text('OLD CONTROLLER\n');(self.state/'start-panel.sh').write_text('OLD STARTUP\n')
        (self.init/'panalume-panel.rc').write_text('service panalume_panel /bin/sh old-panel\n')
        (self.init/'panalume-mqtt.rc').write_text('service panalume_mqtt /bin/sh old-mqtt\n')
        script=(ROOT/'assets/provision/setup-panel.sh').read_text().replace('/data/local/wallpanel',str(self.target)).replace('/data/local/panalume',str(self.state)).replace('/data/user/0/local.wallpanel.app',str(self.r/'app')).replace('/system/etc/init',str(self.init)).replace('/system/bin/setsid','/usr/bin/setsid').replace('/system/bin/sh','/bin/sh')
        self.script=self.target/'setup-panel.sh';self.script.write_text(script);self.script.chmod(0o700)
    def tearDown(self):
        for path in self.target.glob('setup/backups/*/watchdog.pid'):
            try:os.kill(int(path.read_text()),15)
            except (ProcessLookupError,ValueError):pass
        self.temp.cleanup()
    def call(self,mode):return subprocess.run(['sh',str(self.script),mode],env=self.env,capture_output=True,text=True,timeout=15,start_new_session=True)
    def result(self,mode):
        response=self.call(mode);return response,json.loads(response.stdout.strip())
    def meta(self):return json.loads((self.r/'meta.json').read_text())
    def test_check_has_no_configuration_or_package_changes(self):
        result,data=self.result('check');self.assertEqual(result.returncode,0);self.assertTrue(data['supported']);self.assertTrue(data['conflicts']);self.assertEqual(self.meta(),self.old);self.assertFalse((self.target/'setup').exists())
    def test_fresh_apply_preserves_values_and_retires_only_known_components(self):
        result,data=self.result('apply');self.assertEqual(result.returncode,0,result.stderr);self.assertTrue(data['ok']);m=self.meta()
        self.assertEqual(m['packages']['com.sznaner.bgmz9'],3);self.assertEqual(m['packages']['local.wallpanel.audio'],3);self.assertEqual(m['packages']['com.spotify.music'],0)
        self.assertEqual(m['packages']['com.sznaner.pulldownmenu'],3)
        self.assertEqual((self.state/'display-brightness').read_text(),'77\n');self.assertEqual((self.state/'display-policy').read_text(),'v1 90 600 15\n')
        self.assertFalse((self.init/'panalume-mqtt.rc').exists());self.assertTrue((self.init/'wallpanel-privilege.rc').exists());self.assertEqual((self.state/'display-policy').stat().st_mode&0o777,0o600)
        self.assertFalse((self.state/'panel-mqtt').exists());self.assertFalse((self.state/'start-panel.sh').exists())
        for bad in ('screencap','tinymix','svc power','keyevent','setenforce'):self.assertNotIn(bad,(self.r/'commands').read_text())
    def test_restore_exact_package_home_timing_and_legacy_service_state(self):
        self.assertEqual(self.call('apply').returncode,0);result,data=self.result('restore');self.assertEqual(result.returncode,0,result.stderr);self.assertTrue(data['hardware_stopped']);self.assertEqual(self.meta(),self.old)
        self.assertTrue((self.init/'panalume-panel.rc').exists());self.assertFalse((self.init/'wallpanel-privilege.rc').exists());self.assertEqual((self.state/'display-policy').read_text(),'v1 90 600 15\n')
        self.assertEqual((self.state/'panel-mqtt').read_text(),'OLD CONTROLLER\n');self.assertEqual((self.state/'start-panel.sh').read_text(),'OLD STARTUP\n')
    def test_missing_hardware_defaults_created_without_legacy_gate_files(self):
        for path in self.state.iterdir():path.unlink()
        self.assertEqual(self.call('apply').returncode,0);self.assertEqual((self.state/'display-brightness').read_text(),'120\n');self.assertEqual((self.state/'display-policy').read_text(),'v1 60 300 26\n')
        self.assertEqual((self.state/'button-local-bindings').read_text(),'0 0\n0 0\n0 0\n0 0\n')
    def test_failed_restore_keeps_recovery_journal_and_does_not_report_success(self):
        self.assertEqual(self.call('apply').returncode,0);self.env['READONLY']='1'
        result=self.call('restore');self.assertNotEqual(result.returncode,0);self.assertNotIn('"ok":true',result.stdout)
        self.assertTrue((self.target/'setup/apply-active').exists());self.assertFalse(list(self.target.glob('setup/backups/*/restored')))
    def test_unsupported_layout_and_policy_refuse_before_mutation(self):
        for flag in ('UNSUPPORTED','ENFORCING'):
            self.env[flag]='1';result,data=self.result('apply');self.assertNotEqual(result.returncode,0);self.assertFalse(data['ok']);self.assertEqual(self.meta(),self.old);self.env.pop(flag)
    def test_readonly_system_refuses_without_disabling_vendor(self):
        self.env['READONLY']='1';result,data=self.result('apply');self.assertFalse(data['ok']);self.assertEqual(self.meta(),self.old)
    def test_partial_package_failure_rolls_back_previous_setup(self):
        self.env['FAIL_PACKAGE']='local.wallpanel.audio';result=self.call('apply');self.assertNotEqual(result.returncode,0)
        m=self.meta();m.pop('failure_used',None);self.assertEqual(m,self.old);self.assertTrue((self.init/'panalume-mqtt.rc').exists());self.assertEqual((self.state/'display-brightness').read_text(),'77\n')
    def test_unknown_init_service_is_not_removed(self):
        (self.init/'panalume-panel.rc').write_text('service unrelated /system/bin/unrelated\n');result,data=self.result('apply');self.assertEqual(data['error'],'unknown_startup_definition');self.assertEqual(self.meta(),self.old)
    def test_known_service_does_not_allow_unrelated_boot_actions(self):
        with (self.init/'panalume-panel.rc').open('a') as out:out.write('on boot\n    write /some/other/device 1\n')
        result,data=self.result('apply');self.assertEqual(data['error'],'unknown_startup_definition');self.assertEqual(self.meta(),self.old)
    def test_process_death_is_recovered_by_detached_watchdog(self):
        self.env['HOLD_PACKAGE']='com.sznaner.bgmz9'
        process=subprocess.Popen(['sh',str(self.script),'apply'],env=self.env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
        try:
            until=time.monotonic()+5
            while not (self.r/'holding').exists() and time.monotonic()<until:time.sleep(.01)
            self.assertTrue((self.r/'holding').exists());os.killpg(process.pid,signal.SIGKILL);process.communicate(timeout=3)
            until=time.monotonic()+8
            while (not list(self.target.glob('setup/backups/*/restored')) or (self.target/'setup/operation.lock').exists()) and time.monotonic()<until:time.sleep(.05)
            self.assertTrue(list(self.target.glob('setup/backups/*/restored')));self.assertEqual(self.meta(),self.old);self.assertFalse((self.target/'setup/operation.lock').exists())
        finally:
            if process.poll() is None:os.killpg(process.pid,signal.SIGKILL);process.communicate(timeout=3)
if __name__=='__main__':unittest.main(verbosity=2)
