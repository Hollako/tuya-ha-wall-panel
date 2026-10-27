"""Migration/rollback simulations. No ADB invocation or device access."""
import contextlib,io,json,tempfile,unittest,zipfile,sys
from pathlib import Path
from unittest.mock import patch
import receiver_cutover as migration

XML=b'<map><string name="receiver_name">Bedroom</string><int name="latency_offset_ms" value="-15"/><int name="volume_trim" value="85"/><boolean name="enabled" value="true"/><string name="ha_token">private-do-not-copy</string></map>'

class FakeDevice:
    def __init__(self,*args):
        self.calls=[];self.fail_start=False;self.busy=False;self.bad_hash=False;self.old_installed=True;self.old_uid=10063
        self.files={migration.BASE+'/start-helper.sh':b'#!/system/bin/sh\nuid=$(stat -c %u /data/user/0/com.wallpanel.audioreceiver)\n',migration.OLD_DATA+'/shared_prefs/wpar_prefs.xml':XML}
    def read(self,path):return self.files[path]
    def exists(self,path):return path in self.files
    def write(self,path,data,**kwargs):self.calls.append(('write',path));self.files[path]=data
    def stop_helper(self):self.calls.append(('stop-helper',))
    def start_helper(self):
        self.calls.append(('start-helper',))
        if self.fail_start:self.fail_start=False;raise RuntimeError('injected helper failure')
    def launch(self):
        self.calls.append(('launch',));pending=migration.NEW_DATA+'/files/receiver-migration.json'
        if pending in self.files:
            values=json.loads(self.files.pop(pending))
            self.files[migration.NEW_DATA+'/shared_prefs/wpar_prefs.xml']=('<map><string name="migration_id">'+values['id']+'</string><boolean name="enabled" value="true"/></map>').encode()
    def shell(self,command,check=True):
        self.calls.append(('shell',command))
        if command=='id -u':return '0'
        if command=='getenforce':return 'Permissive'
        if command=='dumpsys package '+migration.NEW:return 'versionCode=43'
        if command=='dumpsys package '+migration.OLD:return 'User 0: installed=true enabled=0'
        if command=='pm path '+migration.OLD:return 'package:/data/app/receiver/base.apk' if self.old_installed else ''
        if command.startswith('pm install -r '):self.old_installed=True;self.old_uid=10066;return 'Success'
        if command.startswith('stat -c %u '):return '10050' if command.endswith(migration.NEW) else str(self.old_uid)
        if command.startswith('getprop init.svc.'):return 'running'
        if command.startswith('getprop ro.product.cpu.'):return 'arm64-v8a,armeabi-v7a'
        if command.startswith('sha256sum '):return ('bad' if self.bad_hash else 'abc')+'  file'
        if command.startswith('cp '):
            words=command.split();self.files[words[2]]=self.files[words[1]]
        if command.startswith('mv '):
            words=command.split();self.files[words[2]]=self.files.pop(words[1])
        if 'com.wallpanel.audioreceiver.STOP' in command:self.files[migration.NEW_DATA+'/shared_prefs/wpar_prefs.xml']=b'<map><boolean name="enabled" value="false"/></map>'
        return ''

class CutoverTests(unittest.TestCase):
    def invoke(self,device,*extra):
        with tempfile.TemporaryDirectory() as folder:
            apk=Path(folder)/'receiver.apk'
            record={name:{'sha256':'abc'} for name in ('wpar-ptp-helper','nqptp')}
            with zipfile.ZipFile(apk,'w') as archive:archive.writestr('assets/receiver/native-payloads.json',json.dumps({'abis':{'arm64-v8a':record}}))
            with patch.object(sys,'argv',['receiver_cutover.py','--device','fake','--apk',str(apk),*extra]),patch.object(migration,'Device',return_value=device),contextlib.redirect_stdout(io.StringIO()):migration.main()
    def test_import_allows_only_receiver_settings(self):
        result=migration.import_settings(XML)
        self.assertEqual(result['latency_offset_ms'],-15);self.assertEqual(result['volume_trim'],85)
        self.assertNotIn('ha_token',result);self.assertNotIn('enabled',result)
    def test_invalid_calibration_rejected(self):
        for value in (b'501',b'-501'):
            with self.assertRaises(ValueError):migration.import_settings(XML.replace(b'-15',value))
    def test_plan_does_not_write(self):
        device=FakeDevice();before=dict(device.files);self.invoke(device)
        self.assertEqual(device.files,before)
        self.assertFalse(any(c[0] in ('write','stop-helper','start-helper','launch') for c in device.calls))
        self.assertFalse(any(c[0]=='shell' and any(word in c[1] for word in ('force-stop','disable-user','mkdir','chown')) for c in device.calls))
    def test_helper_mismatch_refuses_before_mutation(self):
        device=FakeDevice();device.bad_hash=True;before=dict(device.files)
        with self.assertRaises(RuntimeError):self.invoke(device,'--apply')
        self.assertEqual(device.files,before)
    def test_cutover_preserves_source_data_and_can_rollback(self):
        device=FakeDevice();self.invoke(device,'--apply')
        self.assertEqual(device.files[migration.OLD_DATA+'/shared_prefs/wpar_prefs.xml'],XML)
        self.assertIn(migration.NEW.encode(),device.files[migration.BASE+'/start-helper.sh'])
        receipt=next(path for path in device.files if path.endswith('/receipt.json'))
        migration.rollback(device,receipt.rsplit('/',1)[0])
        self.assertIn(migration.OLD.encode(),device.files[migration.BASE+'/start-helper.sh'])
    def test_failed_cutover_restores_old_owner(self):
        device=FakeDevice();device.fail_start=True
        with self.assertRaises(RuntimeError):self.invoke(device,'--apply')
        self.assertIn(migration.OLD.encode(),device.files[migration.BASE+'/start-helper.sh'])
        self.assertTrue(any(c==('shell','pm default-state --user 0 '+migration.OLD) for c in device.calls))
    def test_rollback_reinstalls_removed_receiver_and_uses_its_new_uid(self):
        device=FakeDevice();self.invoke(device,'--apply')
        receipt=next(path for path in device.files if path.endswith('/receipt.json'))
        backup=receipt.rsplit('/',1)[0];device.files[backup+'/standalone.apk']=b'fake-signed-apk';device.old_installed=False
        migration.rollback(device,backup)
        self.assertTrue(device.old_installed)
        self.assertIn(('shell','chown -R 10066:10066 '+migration.BASE+'/shm'),device.calls)

if __name__=='__main__':unittest.main()
