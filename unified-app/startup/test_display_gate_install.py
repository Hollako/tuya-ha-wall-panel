"""Dual-service display transaction using private fake Android paths only."""
import json,os,signal,subprocess,sys,time,unittest
from pathlib import Path
r=Path(__file__).resolve().parent
sys.path.insert(0,str(r.parents[1]/'wallpanel-hardware'))
from test_installer import InstallerTests,FAKE

class DisplayGateTests(unittest.TestCase):
    def setUp(self):
        self.f=InstallerTests();self.f.setUp();self.root=self.f.r;self.env=self.f.env
        source=(r/'install-display-gate.sh').read_text().replace('/data/local/panalume',str(self.root/'state')).replace('/data/local/tmp/wallpanel-display-gate',str(self.root/'stage')).replace('/dev/wallpanel-mcu-status.json',str(self.root/'status.json')).replace('/dev/wallpanel-display-state',str(self.root/'display.status')).replace('/system/bin/sh','/bin/sh').replace('/system/bin/setsid','/usr/bin/setsid')
        (self.root/'stage/install.sh').write_text(source)
        self.binary=self.root/'stage/wallpanel-bridge-lease';self.binary.write_text('#!/bin/sh\n# NATIVE_TEST\nif [ "$1" = --hardware-info ]; then echo \'{"layout_matched":true}\'; else echo \'{"held":false}\'; fi\n');self.binary.chmod(0o755)
        backlight=self.root/'stage/panel-backlight';backlight.write_text('#!/bin/sh\n# BACKLIGHT_TEST\necho \'{"display_gate":1}\'\n');backlight.chmod(0o755)
        (self.root/'state/panel-backlight').write_text('OLD BACKLIGHT\n');(self.root/'state/audio-gate.version').write_text('1\n')
        start=FAKE.index("elif name=='setprop':");end=FAKE.index("elif name=='sleep':",start)
        block='''elif name=='setprop':
    services=m.setdefault('services',{'panalume_mqtt':'running','panalume_panel':'running'})
    services[a[1]]='stopped' if a[0]=='ctl.stop' else 'running'
    m['service']=services['panalume_mqtt']
    if a[0]=='ctl.start':
        pid=os.getppid()
        if a[1]=='panalume_mqtt' and 'NATIVE_TEST' in (r/'state/panel-mqtt').read_text():
            (r/'state/mqtt.pid').write_text(str(pid)+'\\n')
            (r/'status.json').write_text(json.dumps({'pid':pid,'online':not m.get('fail_health')},separators=(',',':')))
        if a[1]=='panalume_panel' and 'BACKLIGHT_TEST' in (r/'state/panel-backlight').read_text() and not m.get('fail_display'):
            (r/'state/backlight.pid').write_text(str(pid)+'\\n')
            (r/'display.status').write_text('v1 '+str(pid)+' 120 120 0 60 300 26 0 1000\\n')
    if a[0]=='ctl.stop' and m.get('hold_stop'):
        m['hold_stop']=False;meta.write_text(json.dumps(m));(r/'stop-held').touch();time.sleep(30)
elif name=='getprop':print(m.get('services',{}).get(a[0].rsplit('.',1)[-1],m['service']))
'''
        fake=FAKE[:start]+block+FAKE[end:]
        for name in ('setprop','getprop'):(self.root/'bin'/name).write_text(fake)
    def tearDown(self):self.f.tearDown()
    def install(self):return subprocess.run(['sh',str(self.root/'stage/install.sh')],env=self.env,text=True,capture_output=True,timeout=25)
    def restored(self):
        self.assertEqual((self.root/'state/panel-mqtt').read_text(),'LEGACY panel-mqtt\n');self.assertEqual((self.root/'state/panel-backlight').read_text(),'OLD BACKLIGHT\n')
        self.assertFalse((self.root/'state/display-gate.version').exists())
        self.assertEqual(json.loads((self.root/'meta.json').read_text())['services'],{'panalume_mqtt':'running','panalume_panel':'running'})
    def test_success_backs_up_both_without_activating_app(self):
        result=self.install();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        backup=Path((self.root/'state/display-gate.backup').read_text().strip())
        self.assertEqual((backup/'panel-backlight').read_text(),'OLD BACKLIGHT\n');self.assertTrue((backup/'committed').exists())
        self.assertNotIn('start-foreground-service',(self.root/'commands.log').read_text())
    def test_mcu_failure_recovers_both(self):self.f.meta['fail_health']=True;self.f.save();self.assertNotEqual(self.install().returncode,0);self.restored()
    def test_display_failure_recovers_both(self):self.f.meta['fail_display']=True;self.f.save();self.assertNotEqual(self.install().returncode,0);self.restored()
    def test_manual_recovery(self):
        self.assertEqual(self.install().returncode,0);backup=Path((self.root/'state/display-gate.backup').read_text().strip())
        subprocess.run(['sh',str(backup/'recover.sh'),str(backup)],env=self.env,check=True,timeout=10);self.restored()
    def test_repeat_refused(self):self.assertEqual(self.install().returncode,0);self.assertNotEqual(self.install().returncode,0)
    def test_profile_refusal_before_service_stop(self):
        self.binary.write_text('#!/bin/sh\necho \'{"layout_matched":false}\'\n');self.assertNotEqual(self.install().returncode,0);self.assertNotIn('setprop',(self.root/'commands.log').read_text())
    def test_watchdog_recovers_after_installer_death(self):
        self.f.meta['hold_stop']=True;self.f.save();p=subprocess.Popen(['sh',str(self.root/'stage/install.sh')],env=self.env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
        try:
            end=time.monotonic()+5
            while not (self.root/'stop-held').exists() and time.monotonic()<end:time.sleep(.02)
            self.assertTrue((self.root/'stop-held').exists());os.killpg(p.pid,signal.SIGKILL);p.communicate(timeout=3)
            end=time.monotonic()+12
            while time.monotonic()<end:
                try:
                    if (self.root/'state/panel-backlight').read_text()=='OLD BACKLIGHT\n' and json.loads((self.root/'meta.json').read_text()).get('services',{}).get('panalume_mqtt')=='running':break
                except (ValueError,FileNotFoundError):pass
                time.sleep(.05)
            self.restored()
        finally:
            if p.poll() is None:os.killpg(p.pid,signal.SIGKILL);p.communicate(timeout=3)
if __name__=='__main__':unittest.main(defaultTest='DisplayGateTests',verbosity=2)
