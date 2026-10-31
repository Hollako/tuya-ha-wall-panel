"""A133 preparation/rollback against a fake Android filesystem only (run in WSL)."""
import json,unittest
from pathlib import Path
import test_provisioning as fixture
ROOT=fixture.ROOT

class A133SetupTests(unittest.TestCase):
    def setUp(self):
        self.f=fixture.ProvisioningTests();self.f.setUp();f=self.f
        packages={'com.sznaner.bgmz9':0,'com.sznaner.rotaryknob':1,'com.sznaner.relay':0,
                  'com.sznaner.pulldownmenu':0,'com.sznaner.backbtn':0,'com.sznaner.volumedialog':0,
                  'com.sznaner.powermenu':0,'com.android.bluetooth':0,'com.sznaner.bluetoothplayer':0,
                  'com.wallpanel.audioreceiver':0,'com.spotify.music':0}
        f.old['packages']=packages;(f.r/'meta.json').write_text(json.dumps(f.old))
        core=f.target/'wallpanel-core';core.write_text('#!/bin/sh\nif [ -n "$UNSUPPORTED" ];then echo \'{"profile":"a133","layout_matched":false}\';else echo \'{"profile":"a133","layout_matched":true}\';fi\n');core.chmod(0o700)
        script=(ROOT/'assets/provision/setup-a133.sh').read_text()
        script=script.replace('/data/local/wallpanel/a133-state',str(f.state)).replace('/data/local/wallpanel',str(f.target)).replace('/data/user/0/local.wallpanel.app',str(f.r/'app'))
        script=script.replace('init_root=/vendor\ntest -d "$init_root/etc/init" || init_root=/system','init_root='+str(f.r/'system'))
        script=script.replace('/system/bin/setsid','/usr/bin/setsid').replace('/system/bin/sh','/bin/sh')
        f.script.write_text(script)
    def tearDown(self):self.f.tearDown()
    def test_check_is_read_only_and_reports_a133(self):
        result,data=self.f.result('check');self.assertEqual(result.returncode,0);self.assertEqual(data['profile'],'a133');self.assertTrue(data['supported']);self.assertEqual(self.f.meta(),self.f.old)
    def test_apply_and_restore_preserve_audio_apps_and_unrelated_startup(self):
        f=self.f;result,data=f.result('apply');self.assertEqual(result.returncode,0,result.stderr);self.assertTrue(data['ok']);now=f.meta()
        self.assertEqual(now['packages']['com.sznaner.bgmz9'],3);self.assertEqual(now['packages']['com.sznaner.rotaryknob'],3);self.assertEqual(now['packages']['com.sznaner.relay'],3)
        for package in ('com.android.bluetooth','com.sznaner.bluetoothplayer','com.wallpanel.audioreceiver','com.spotify.music'):self.assertEqual(now['packages'][package],0)
        self.assertEqual(now['services'],f.old['services']);self.assertTrue((f.init/'panalume-panel.rc').exists())
        self.assertTrue((f.init/'wallpanel-privilege.rc').exists());self.assertIn('/bin/sh', (f.init/'wallpanel-privilege.rc').read_text())
        result,data=f.result('restore');self.assertEqual(result.returncode,0,result.stderr);self.assertTrue(data['hardware_stopped']);self.assertEqual(f.meta(),f.old)
    def test_failed_vendor_disable_rolls_back_prior_changes(self):
        f=self.f;f.env['FAIL_PACKAGE']='com.sznaner.relay';result=f.call('apply');self.assertNotEqual(result.returncode,0);self.assertEqual(f.meta()['packages'],f.old['packages']);self.assertEqual(f.meta()['services'],f.old['services']);self.assertFalse((f.target/'setup/apply-active').exists())
    def test_wrong_layout_and_enforcing_policy_never_disable_packages(self):
        for key in ('UNSUPPORTED','ENFORCING'):
            self.f.env[key]='1';result=self.f.call('apply');self.assertNotEqual(result.returncode,0);self.assertEqual(self.f.meta(),self.f.old);del self.f.env[key]

if __name__=='__main__':unittest.main(verbosity=2)
