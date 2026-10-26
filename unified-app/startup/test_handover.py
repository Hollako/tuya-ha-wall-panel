"""Actual startup scripts against a fake Android filesystem/services. No ADB."""
import json,os,signal,subprocess,tempfile,time,unittest
from pathlib import Path
root=Path(__file__).resolve().parent
FAKE=r'''#!/usr/bin/env python3
import json,os,sys,time
from pathlib import Path
r=Path(os.environ['TEST_ROOT']);file=r/'services.json';meta=json.loads(file.read_text());name=Path(sys.argv[0]).name;a=sys.argv[1:]
with (r/'commands.log').open('a') as out:out.write(name+' '+' '.join(a)+'\n')
if name=='getprop':print(meta[a[0].split('.')[-1]])
elif name=='setprop':
    if not(meta.get('fail_stop')==a[1] and a[0]=='ctl.stop'):meta[a[1]]='stopped' if a[0]=='ctl.stop' else 'running'
elif name=='sleep':time.sleep(.03)
elif name=='mv':
    if meta.get('fail_move') and a[-1].endswith('/start-compat.sh'):
        meta['fail_move']=False;file.write_text(json.dumps(meta));sys.exit(1)
    os.replace(a[-2],a[-1])
elif name=='chown':pass
else:raise RuntimeError(name)
if name in ('setprop','mv'):file.write_text(json.dumps(meta))
'''
class HandoverTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.r=Path(self.tmp.name);self.state=self.r/'state';self.state.mkdir();tools=self.r/'bin';tools.mkdir()
        self.meta={'panalume_network':'running','panalume_compat':'running','panalume_mqtt':'running','panalume_panel':'running'};self.save()
        for name in ('getprop','setprop','sleep','chown','mv'):
            path=tools/name;path.write_text(FAKE);path.chmod(0o755)
        self.env={**os.environ,'TEST_ROOT':str(self.r),'PATH':str(tools)+':'+os.environ['PATH']}
        self.original={part:'#!/bin/sh\necho LEGACY_'+part+'\n' for part in ('network','compat')}
        for part,content in self.original.items():(self.state/f'start-{part}.sh').write_text(content)
        (self.state/'mqtt.pid').write_text('123\n');(self.state/'backlight.pid').write_text('456\n')
        for name in ('install-home-handover.sh','rollback-home-handover.sh'):
            source=(root/name).read_text().replace('/data/local/panalume',str(self.state));(self.r/name).write_text(source)
    def tearDown(self):self.tmp.cleanup()
    def save(self):(self.r/'services.json').write_text(json.dumps(self.meta))
    def run_script(self,name='install'):return subprocess.run(['sh',str(self.r/(name+'-home-handover.sh'))],env=self.env,capture_output=True,text=True,timeout=15)
    def unchanged_hardware(self):
        self.assertEqual((self.state/'mqtt.pid').read_text(),'123\n');self.assertEqual((self.state/'backlight.pid').read_text(),'456\n')
        log=(self.r/'commands.log').read_text();self.assertNotIn('ctl.stop panalume_mqtt',log);self.assertNotIn('ctl.stop panalume_panel',log)
    def test_success_stops_only_browser_guards(self):
        result=self.run_script();self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        m=json.loads((self.r/'services.json').read_text());self.assertEqual(m['panalume_network'],'stopped');self.assertEqual(m['panalume_compat'],'stopped')
        self.assertTrue((self.state/'unified-home.enabled').exists());self.unchanged_hardware()
    def test_rollback_restores_scripts_and_original_service_states(self):
        self.meta['panalume_compat']='stopped';self.save();self.assertEqual(self.run_script().returncode,0)
        result=self.run_script('rollback');self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        for part,content in self.original.items():self.assertEqual((self.state/f'start-{part}.sh').read_text(),content)
        m=json.loads((self.r/'services.json').read_text());self.assertEqual(m['panalume_network'],'running');self.assertEqual(m['panalume_compat'],'stopped');self.unchanged_hardware()
    def test_partial_install_rolls_back(self):
        self.meta['fail_move']=True;self.save();result=self.run_script();self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.state/'unified-home.enabled').exists())
        for part,content in self.original.items():self.assertEqual((self.state/f'start-{part}.sh').read_text(),content)
        self.unchanged_hardware()
    def test_repeat_refused_without_changing_backup(self):
        self.assertEqual(self.run_script().returncode,0);marker=(self.state/'unified-home.enabled').read_text()
        self.assertNotEqual(self.run_script().returncode,0);self.assertEqual((self.state/'unified-home.enabled').read_text(),marker)
    def test_boot_wrapper_suppresses_original_browser(self):
        self.assertEqual(self.run_script().returncode,0)
        proc=subprocess.Popen(['sh',str(self.state/'start-network.sh')],env=self.env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
        try:
            time.sleep(.15);os.killpg(proc.pid,signal.SIGTERM);stdout,_=proc.communicate(timeout=3);self.assertNotIn(b'LEGACY_network',stdout)
            self.assertIn('setprop ctl.stop panalume_network',(self.r/'commands.log').read_text())
        finally:
            if proc.poll() is None:os.killpg(proc.pid,signal.SIGKILL);proc.communicate(timeout=3)
if __name__=='__main__':unittest.main(verbosity=2)
