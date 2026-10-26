"""Real local-socket bootstrap with fake executables, no panel or hardware access."""
from pathlib import Path
import os,socket,subprocess,tempfile,time,unittest,json
ROOT=Path(__file__).resolve().parent
class PrivilegeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(prefix='wp-privilege-tests-');cls.binary=Path(cls.tmp.name)/'server'
        runner=Path(cls.tmp.name)/'app_process';runner.write_text('#!/bin/sh\ntest "$1" = /system/bin && test "$2" = local.wallpanel.system.SystemUiLease || exit 2\nprintf \'{"v":1,"ready":true}\\n\'\ncat >/dev/null\n');runner.chmod(0o700)
        subprocess.run(['gcc','-DWP_TESTING','-DWP_SETUP_SHELL="/bin/sh"','-DWP_UI_PROCESS="'+str(runner)+'"','-O1','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',str(ROOT/'wallpanel-privilege.c'),'-o',str(cls.binary)],check=True)
    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()
    def setUp(self):
        self.tmpdir=tempfile.TemporaryDirectory(prefix='wp-p-');self.r=Path(self.tmpdir.name);self.source=self.r/'app.conf';self.source.write_text('version=1\n');self.source.chmod(0o600)
        (self.r/'wallpanel-core').write_text('#!/bin/sh\nprintf \'{"ready":true,"pid":%s}\\n\' $$\nwhile IFS= read -r line;do printf "%s\\n" "$line";done\n')
        (self.r/'wallpanel-capture').write_text('#!/bin/sh\nprintf "synthetic capture"\n')
        (self.r/'setup-panel.sh').write_text('#!/bin/sh\nprintf \'{"v":1,"ok":true,"mode":"%s"}\\n\' "$1"\n')
        for name in ('wallpanel-core','wallpanel-capture'):(self.r/name).chmod(0o700)
        self.process=None;self.clients=[]
    def start(self,uid=None):
        self.process=subprocess.Popen([str(self.binary),str(os.getuid() if uid is None else uid),str(self.source),str(self.r)])
        until=time.monotonic()+3
        while not (self.r/'control.sock').exists() and time.monotonic()<until:time.sleep(.01)
        self.assertTrue((self.r/'control.sock').exists())
    def connect(self,line):
        client=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);client.settimeout(2);client.connect(str(self.r/'control.sock'));self.clients.append(client);client.sendall(line.encode());return client
    def tearDown(self):
        for client in self.clients:client.close()
        if self.process:self.process.terminate();self.process.wait(timeout=4)
        self.tmpdir.cleanup()
    def test_fixed_worker_bidirectional_pipe_and_duplicate_refusal(self):
        self.start();client=self.connect('WORKER\n');state=json.loads(client.recv(1024));self.assertTrue(state['ready']);client.sendall(b'hello\n');self.assertEqual(client.recv(1024),b'hello\n')
        self.assertIn(b'rejected',self.connect('WORKER\n').recv(1024));self.assertEqual((self.r/'worker.conf').read_text(),'version=1\n')
    def test_wrong_uid_cannot_start_any_child(self):
        self.start(os.getuid()+1);client=self.connect('WORKER\n')
        try:result=client.recv(1024)
        except ConnectionResetError:result=b''
        self.assertEqual(result,b'');self.assertFalse((self.r/'worker.conf').exists())
    def test_capture_only_matches_own_running_worker(self):
        self.start();self.assertIn(b'rejected',self.connect('CAPTURE 1234\n').recv(1024));client=self.connect('WORKER\n');pid=json.loads(client.recv(1024))['pid']
        self.assertEqual(self.connect(f'CAPTURE {pid}\n').recv(1024),b'synthetic capture')
        self.assertIn(b'rejected',self.connect(f'CAPTURE 0{pid}\n').recv(1024))
    def test_arbitrary_commands_and_symlink_config_rejected(self):
        self.start();self.assertIn(b'rejected',self.connect('REBOOT\n').recv(1024));self.source.unlink();self.source.symlink_to('/etc/passwd');self.assertIn(b'rejected',self.connect('WORKER\n').recv(1024))
    def test_setup_check_is_readonly_and_mutations_require_stopped_worker(self):
        self.start();worker=self.connect('WORKER\n');json.loads(worker.recv(1024))
        self.assertEqual(json.loads(self.connect('SETUP CHECK\n').recv(1024))['mode'],'check')
        self.assertIn(b'rejected',self.connect('SETUP APPLY\n').recv(1024));self.assertIn(b'rejected',self.connect('SETUP RESTORE\n').recv(1024))
        self.assertIn(b'rejected',self.connect('SETUP APPLY /tmp/anything\n').recv(1024))
    def test_stopped_setup_uses_only_fixed_script_and_modes(self):
        self.start();self.assertEqual(json.loads(self.connect('SETUP APPLY\n').recv(1024))['mode'],'apply');time.sleep(.25)
        self.assertEqual(json.loads(self.connect('SETUP RESTORE\n').recv(1024))['mode'],'restore')
    def test_recovery_journal_blocks_hardware_until_recovery_finishes(self):
        (self.r/'setup').mkdir();(self.r/'setup/apply-active').write_text('snapshot\n');self.start()
        self.assertIn(b'rejected',self.connect('WORKER\n').recv(1024))
        self.assertEqual(json.loads(self.connect('SETUP CHECK\n').recv(1024))['mode'],'check')
    def test_ui_guard_lease_is_independent_and_released_on_disconnect(self):
        self.start();guard=self.connect('UI_GUARD\n');self.assertTrue(json.loads(guard.recv(1024))['ready'])
        self.assertIn(b'rejected',self.connect('UI_GUARD\n').recv(1024))
        self.assertIn(b'rejected',self.connect('UI_GUARD /tmp/arbitrary.jar\n').recv(1024))
        worker=self.connect('WORKER\n');self.assertTrue(json.loads(worker.recv(1024))['ready'])
        guard.shutdown(socket.SHUT_WR);self.assertEqual(guard.recv(1024),b'');time.sleep(.3)
        self.assertTrue(json.loads(self.connect('UI_GUARD\n').recv(1024))['ready'])
        worker.sendall(b'still-running\n');self.assertEqual(worker.recv(1024),b'still-running\n')
if __name__=='__main__':unittest.main(verbosity=2)
