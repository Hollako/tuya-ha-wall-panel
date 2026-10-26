"""Signed-APK payload bounds/integrity and explicit installer-target behavior."""
import contextlib,hashlib,io,json,tempfile,unittest,zipfile
from pathlib import Path
from bootstrap_panel import payload,main
ROOT=Path(__file__).resolve().parent
class PackageTests(unittest.TestCase):
    def setUp(self):self.apk=ROOT/json.loads((ROOT/'build-result.json').read_text())['apk']
    def mutate(self,change):
        with zipfile.ZipFile(self.apk) as archive:files={name:archive.read(name) for name in archive.namelist()}
        change(files)
        temp=tempfile.TemporaryDirectory(prefix='wp-payload-');path=Path(temp.name)/'test.apk'
        with zipfile.ZipFile(path,'w') as archive:
            for name,data in files.items():archive.writestr(name,data)
        return temp,path
    def test_payload_has_all_six_verified_components(self):
        self.assertEqual(set(payload(self.apk)),{'wallpanel-core','wallpanel-capture','wallpanel-privilege','wallpanel-system-ui.jar','setup-panel.sh','bootstrap-panel.sh'})
    def test_changed_component_is_rejected(self):
        temp,path=self.mutate(lambda f:f.__setitem__('assets/provision/setup-panel.sh',b'changed'))
        try:
            with self.assertRaises(ValueError):payload(path)
        finally:temp.cleanup()
    def test_archive_cannot_add_extraction_paths(self):
        def change(files):
            m=json.loads(files['assets/provision/manifest.json']);m['files']['../../outside']='0'*64;files['assets/provision/manifest.json']=json.dumps(m).encode()
        temp,path=self.mutate(change)
        try:
            with self.assertRaises(ValueError):payload(path)
        finally:temp.cleanup()
    def test_windows_line_endings_rejected_even_with_matching_digest(self):
        def change(files):
            name='assets/provision/setup-panel.sh';files[name]=files[name].replace(b'\n',b'\r\n');m=json.loads(files['assets/provision/manifest.json']);m['files']['setup-panel.sh']=hashlib.sha256(files[name]).hexdigest();files['assets/provision/manifest.json']=json.dumps(m).encode()
        temp,path=self.mutate(change)
        try:
            with self.assertRaises(ValueError):payload(path)
        finally:temp.cleanup()
    def test_target_cannot_be_implicitly_selected(self):
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as caught:main([])
        self.assertEqual(caught.exception.code,2)
if __name__=='__main__':unittest.main(verbosity=2)
