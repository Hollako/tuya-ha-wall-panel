"""Cooperative-gate installer tested in the existing fake Android harness only."""
import json,os,signal,subprocess,sys,time,unittest
from pathlib import Path
root=Path(__file__).resolve().parent
sys.path.insert(0,str(root.parents[1]/'wallpanel-hardware'))
from test_installer import InstallerTests

class GateInstallTests(unittest.TestCase):
    def setUp(self):
        self.f=InstallerTests();self.f.setUp();self.r=self.f.r;self.env=self.f.env
        source=(root/'install-knob-gate.sh').read_text().replace('/data/local/panalume',str(self.r/'state')).replace('/data/local/tmp/wallpanel-knob-gate',str(self.r/'stage')).replace('/dev/wallpanel-mcu-status.json',str(self.r/'status.json')).replace('/system/bin/sh','/bin/sh').replace('/system/bin/setsid','/usr/bin/setsid')
        (self.r/'stage/install-knob-gate.sh').write_text(source)
        self.binary=self.r/'stage/wallpanel-bridge-lease'
        self.binary.write_text('#!/bin/sh\n# NATIVE_TEST\nif [ "$1" = --hardware-info ]; then echo \'{"layout_matched":true,"cooperative_knob_lease":1,"cooperative_mcu_lease":1}\'; else echo \'{"held":false}\'; fi\n');self.binary.chmod(0o755)
        (self.r/'state/backlight.pid').write_text('111\n')
        (self.r/'state/mcu-gate.version').write_text('1\n')
    def tearDown(self):self.f.tearDown()
    def run_install(self):return subprocess.run(['sh',str(self.r/'stage/install-knob-gate.sh')],env=self.env,text=True,capture_output=True,timeout=20)
    def assert_restored(self):
        self.assertEqual((self.r/'state/panel-mqtt').read_text(),'LEGACY panel-mqtt\n')
        self.assertFalse((self.r/'state/knob-gate.version').exists())
        self.assertEqual((self.r/'state/backlight.pid').read_text(),'111\n')
        self.assertEqual(json.loads((self.r/'meta.json').read_text())['service'],'running')
    def test_success_gate_is_not_activation(self):
        result=self.run_install();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual((self.r/'state/knob-gate.version').read_text(),'1\n')
        backup=Path((self.r/'state/knob-gate.backup').read_text().strip());self.assertTrue((backup/'committed').exists())
        self.assertEqual((backup/'panel-mqtt').read_text(),'LEGACY panel-mqtt\n')
        self.assertNotIn('panalume_panel',(self.r/'commands.log').read_text())
    def test_failed_health_restores_original_bridge(self):
        self.f.meta['fail_health']=True;self.f.save();result=self.run_install();self.assertNotEqual(result.returncode,0);self.assert_restored()
    def test_recovery_restores_old_bridge(self):
        self.assertEqual(self.run_install().returncode,0);backup=Path((self.r/'state/knob-gate.backup').read_text().strip())
        result=subprocess.run(['sh',str(backup/'recover.sh'),str(backup)],env=self.env,text=True,capture_output=True,timeout=10)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr);self.assert_restored()
    def test_repeat_is_refused(self):
        self.assertEqual(self.run_install().returncode,0);original=(self.r/'state/knob-gate.backup').read_text();self.assertNotEqual(self.run_install().returncode,0);self.assertEqual((self.r/'state/knob-gate.backup').read_text(),original)
    def test_wrong_profile_never_stops_bridge(self):
        self.binary.write_text('#!/bin/sh\necho \'{"layout_matched":false,"cooperative_knob_lease":1,"cooperative_mcu_lease":1}\'\n');self.binary.chmod(0o755)
        self.assertNotEqual(self.run_install().returncode,0);self.assertNotIn('setprop',(self.r/'commands.log').read_text());self.assert_restored()
    def test_watchdog_recovers_after_installer_process_dies(self):
        self.f.meta['hold_stop']=True;self.f.save()
        proc=subprocess.Popen(['sh',str(self.r/'stage/install-knob-gate.sh')],env=self.env,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
        try:
            until=time.monotonic()+5
            while not (self.r/'stop-held').exists() and time.monotonic()<until:time.sleep(.02)
            self.assertTrue((self.r/'stop-held').exists());os.killpg(proc.pid,signal.SIGKILL);proc.communicate(timeout=3)
            until=time.monotonic()+12
            while time.monotonic()<until:
                try:
                    if json.loads((self.r/'meta.json').read_text())['service']=='running':break
                except ValueError:pass
                time.sleep(.05)
            self.assert_restored()
        finally:
            if proc.poll() is None:os.killpg(proc.pid,signal.SIGKILL);proc.communicate(timeout=3)
if __name__=='__main__':unittest.main(defaultTest='GateInstallTests',verbosity=2)
