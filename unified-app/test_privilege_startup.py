"""Execute the actual generated wrapper using a fake helper; no panel access."""
import os,subprocess,tempfile,unittest
from pathlib import Path
from privilege_startup import definitions,TARGET,CONFIG
class StartupTests(unittest.TestCase):
    def test_boot_uses_system_shell_and_preserves_fixed_helper_arguments(self):
        for uid in (10065,10101):
            rc,wrapper=definitions(uid)
            service=next(line for line in rc.splitlines() if line.startswith('service '))
            self.assertEqual(service.split()[2:],['/system/bin/sh',TARGET+'/start-privilege.sh'])
            self.assertIn('start wallpanel_privilege',rc)
            with tempfile.TemporaryDirectory(prefix='wp-boot-wrapper-') as temp:
                root=Path(temp);helper=root/'wallpanel-privilege';result=root/'arguments'
                helper.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "'+str(result)+'"\n');helper.chmod(0o700)
                path=root/'start.sh';path.write_text(wrapper.replace(TARGET,str(root)))
                subprocess.run(['sh',str(path)],check=True)
                self.assertEqual(result.read_text().splitlines(),[str(uid),CONFIG,str(root)])
                self.assertEqual(os.stat(root/'privilege.log').st_mode&0o777,0o600)
    def test_invalid_uid_cannot_enter_generated_root_command(self):
        for uid in (0,9999,20000,'10065;id','10065\n'):
            with self.assertRaises(ValueError):definitions(uid)
    def test_pending_setup_recovers_before_worker_service_starts(self):
        rc,wrapper=definitions(10065)
        with tempfile.TemporaryDirectory(prefix='wp-boot-journal-') as temp:
            root=Path(temp);(root/'setup').mkdir();(root/'setup/apply-active').write_text('old-backup\n')
            helper=root/'wallpanel-privilege';helper.write_text('#!/bin/sh\ntest ! -f "'+str(root/'setup/apply-active')+'"\necho started > "'+str(root/'started')+'"\n');helper.chmod(0o700)
            (root/'setup-panel.sh').write_text('#!/bin/sh\ntest "$1" = recover\ntest "$2" = old-backup\nrm "'+str(root/'setup/apply-active')+'"\n')
            script=root/'start.sh';script.write_text(wrapper.replace(TARGET,str(root)).replace('/system/bin/sh','/bin/sh'))
            subprocess.run(['sh',str(script)],check=True);self.assertTrue((root/'started').exists())
if __name__=='__main__':unittest.main(verbosity=2)
