"""Universal APK/payload checks and simulated installer; no panel contact."""
from pathlib import Path
import contextlib,hashlib,io,json,struct,subprocess,tempfile,unittest,zipfile
from unittest.mock import patch
from bootstrap_panel import payload,select_abi,main
R=Path(__file__).resolve().parent
class AbiTests(unittest.TestCase):
    def setUp(self):self.apk=R/json.loads((R/'build-result.json').read_text())['apk']
    def test_all_native_payloads_have_matching_elf_and_hashes(self):
        with zipfile.ZipFile(self.apk) as z:
            catalog=json.loads(z.read('assets/hardware/catalog.json'))
            self.assertEqual(set(catalog['abis']),{'arm32','arm64'})
            for profile in ('g10','a133'):
                for abi,klass,machine in (('arm32',1,40),('arm64',2,183)):
                    base='assets/hardware/'+profile+'-'+abi+'/'
                    metadata=json.loads(z.read(base+'manifest.json'))
                    self.assertEqual(metadata['profile'],profile);self.assertEqual(metadata['abi'],abi)
                    self.assertEqual(metadata['runtime_ready'],profile=='g10')
                    for name,key in (('wallpanel-core','sha256'),('wallpanel-capture','capture_sha256'),('wallpanel-privilege','privilege_sha256')):
                        binary=z.read(base+name)
                        self.assertEqual(binary[:4],b'\x7fELF');self.assertEqual(binary[4],klass)
                        self.assertEqual(struct.unpack_from('<H',binary,18)[0],machine)
                        self.assertEqual(hashlib.sha256(binary).hexdigest(),metadata[key])
                    self.assertIn(base+'wallpanel-system-ui.jar',z.namelist())
            self.assertEqual(payload(self.apk,'arm32')['wallpanel-core'][4],1)
            self.assertEqual(payload(self.apk,'arm64')['wallpanel-core'][4],2)
    def test_selection_never_guesses_an_unsupported_abi(self):
        self.assertEqual(select_abi('armeabi-v7a,armeabi'),'arm32')
        self.assertEqual(select_abi('armeabi-v7a,arm64-v8a'),'arm64')
        for bad in ('x86_64,x86','','arm64-v8a-unverified'):
            with self.assertRaises(ValueError):select_abi(bad)
    def test_app_only_installs_without_root_or_vendor_operations(self):
        calls=[]
        def fake(args,**kwargs):
            calls.append(args);op=args[3:]
            if op==['get-state']:out=b'device\n'
            elif op==['shell','getprop','ro.product.cpu.abilist']:out=b'armeabi-v7a,armeabi\n'
            elif op==['install','-r',str(self.apk.resolve())]:out=b'Success\n'
            else:raise AssertionError('Unexpected device operation: '+str(op))
            return subprocess.CompletedProcess(args,0,out,b'')
        with patch('bootstrap_panel.subprocess.run',side_effect=fake),contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(main(['--device','test-usb','--apk',str(self.apk),'--adb','fake-adb','--app-only']),0)
        self.assertEqual(len(calls),3)
    def test_tampered_arm32_payload_is_rejected_independently(self):
        with zipfile.ZipFile(self.apk) as source:
            files={name:source.read(name) for name in source.namelist()}
        files['assets/hardware/g10-arm32/wallpanel-core']+=b'tampered'
        with tempfile.TemporaryDirectory(prefix='wp-abi-integrity-') as folder:
            apk=Path(folder)/'bad.apk'
            with zipfile.ZipFile(apk,'w',zipfile.ZIP_DEFLATED) as out:
                for name,data in files.items():out.writestr(name,data)
            with self.assertRaises(ValueError):payload(apk,'arm32')
            self.assertEqual(payload(apk,'arm64')['wallpanel-core'][4],2)

    def test_production_java_abi_selection(self):
        java=Path(r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe')
        src=R/'src/local/wallpanel/app'
        with tempfile.TemporaryDirectory(dir=R/'test-classes',prefix='abis-') as folder:
            subprocess.run([str(java),'-jar',str(R.parent/'audio/tooling/ecj.jar'),'-1.7','-nowarn','-encoding','UTF-8','-d',folder,*[str(src/n) for n in ('NativeAssets.java','PanelConfig.java','ModuleCatalog.java','HardwareTopics.java','MqttSession.java','ManagementPolicy.java')],str(R/'tests/local/wallpanel/app/NativeAssetsTests.java')],check=True)
            subprocess.run([str(java),'-cp',folder,'local.wallpanel.app.NativeAssetsTests'],check=True)
if __name__=='__main__':unittest.main(verbosity=2)
