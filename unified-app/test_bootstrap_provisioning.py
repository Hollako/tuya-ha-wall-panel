"""Portable initial-root bootstrap against the same factory filesystem fixture."""
import os,signal,subprocess,time,unittest
from test_provisioning import ProvisioningTests,ROOT
class BootstrapTests(ProvisioningTests):
    def setUp(self):
        super().setUp();self.source=self.r/'staging/wall-panel-setup.fixture';self.source.mkdir(parents=True)
        for name in ('wallpanel-core','wallpanel-capture','wallpanel-system-ui.jar'):(self.source/name).write_bytes((self.target/name).read_bytes());(self.source/name).chmod(0o700)
        program='#!/usr/bin/python3\nimport os,signal,socket,time\nfrom pathlib import Path\nr=Path(__file__).parent\ns=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)\ns.bind(str(r/"control.sock"))\ns.listen(1)\n(r/"privilege.pid").write_text(str(os.getpid())+"\\n")\ndef stop(*args):\n (r/"privilege.pid").unlink(missing_ok=True)\n (r/"control.sock").unlink(missing_ok=True)\n raise SystemExit(0)\nsignal.signal(signal.SIGTERM,stop)\nwhile True:time.sleep(.1)\n'
        (self.source/'wallpanel-privilege').write_text(program);(self.source/'wallpanel-privilege').chmod(0o700)
        (self.source/'setup-panel.sh').write_bytes(self.script.read_bytes())
        body=(ROOT/'assets/provision/bootstrap-panel.sh').read_text().replace('/data/local/wallpanel',str(self.target)).replace('/data/local/tmp',str(self.r/'staging')).replace('/data/user/0/local.wallpanel.app',str(self.r/'app')).replace('/system/bin/setsid','/usr/bin/setsid').replace('/system/bin/sh','/bin/sh')
        self.bootstrap=self.source/'bootstrap-panel.sh';self.bootstrap.write_text(body)
    def tearDown(self):
        p=self.target/'privilege.pid'
        if p.exists():
            try:os.kill(int(p.read_text()),signal.SIGTERM)
            except (ProcessLookupError,ValueError):pass
            until=time.monotonic()+2
            while p.exists() and time.monotonic()<until:time.sleep(.01)
        super().tearDown()
    def bootstrap_call(self,mode='apply'):
        return subprocess.run(['sh',str(self.bootstrap),str(self.source),mode],env=self.env,capture_output=True,text=True,timeout=20,start_new_session=True)
    def test_initial_bootstrap_can_prepare_without_old_gate_installation(self):
        result=self.bootstrap_call();self.assertEqual(result.returncode,0,result.stderr+result.stdout)
        self.assertEqual(self.meta()['packages']['com.sznaner.bgmz9'],3);self.assertTrue((self.init/'wallpanel-privilege.rc').exists());self.assertIn('10065',(self.target/'start-privilege.sh').read_text())
    def test_support_upgrade_does_not_apply_vendor_or_hardware_policy(self):
        result=self.bootstrap_call('bootstrap-only');self.assertEqual(result.returncode,0,result.stderr);self.assertEqual(self.meta()['packages'],self.old['packages']);self.assertEqual(self.meta()['home'],self.old['home'])
        self.assertTrue((self.init/'panalume-panel.rc').exists());self.assertEqual((self.state/'display-policy').read_text(),'v1 90 600 15\n')
    def test_unsupported_hardware_is_refused_before_support_replacement(self):
        self.env['UNSUPPORTED']='1';old=(self.target/'wallpanel-capture').read_bytes();result=self.bootstrap_call();self.assertNotEqual(result.returncode,0);self.assertEqual((self.target/'wallpanel-capture').read_bytes(),old);self.assertEqual(self.meta(),self.old)
    def test_missing_component_restores_previous_root_files(self):
        old=(self.target/'wallpanel-capture').read_bytes();(self.source/'setup-panel.sh').unlink();result=self.bootstrap_call('bootstrap-only');self.assertNotEqual(result.returncode,0);self.assertEqual((self.target/'wallpanel-capture').read_bytes(),old);self.assertEqual(self.meta()['packages'],self.old['packages'])
if __name__=='__main__':unittest.main(defaultTest='BootstrapTests',verbosity=2)
