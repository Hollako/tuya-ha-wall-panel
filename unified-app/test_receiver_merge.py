"""Shared production engine tests; no ADB, playback or HA calls."""
from pathlib import Path
import subprocess,sys,unittest,zipfile,json
root=Path(__file__).resolve().parent
sys.path.insert(0,str(root))
from receiver_payloads import payloads

class ReceiverMergeTests(unittest.TestCase):
    def test_shared_output_policy(self):
        out=root/'test-classes/receiver';out.mkdir(parents=True,exist_ok=True)
        java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
        shared=root.parent/'shared/audio-receiver'
        subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.8','-nowarn','-d',str(out),str(shared/'src/com/wallpanel/audioreceiver/AudioOutputGate.java'),str(shared/'tests/AudioOutputGateTest.java')],check=True)
        subprocess.run([java,'-cp',str(out),'com.wallpanel.audioreceiver.AudioOutputGateTest'],check=True)
    def test_one_receiver_source(self):
        legacy=root.parent/'airplay-app/android/app/src/main/java/com/wallpanel/audioreceiver'
        self.assertEqual({p.name for p in legacy.glob('*.java')},{'MainActivity.java','BootReceiver.java','BootStartService.java'})
        self.assertIn('../../../shared/audio-receiver/src',(root.parent/'airplay-app/android/app/build.gradle').read_text())
    def test_native_payload_integrity(self):
        self.assertEqual(len(payloads()),6)
    def test_one_sip_protocol_core(self):
        for name in ('SipConfig.java','SipEngine.java','SipDigest.java','SipG711.java','SipRtpPacket.java','SipSdp.java'):
            self.assertTrue((root.parent/'shared/sip/src/local/wallpanel/app'/name).is_file())
            self.assertFalse((root/'src/local/wallpanel/app'/name).exists())
    def test_both_apks_use_identical_native_engine(self):
        unified=root/json.loads((root/'build-result.json').read_text())['apk']
        dedicated=root.parent/'airplay-app/android/app/build/outputs/apk/debug/app-debug.apk'
        import hashlib
        with zipfile.ZipFile(unified) as panel,zipfile.ZipFile(dedicated) as receiver:
            self.assertEqual(panel.read('assets/timing.html'),receiver.read('assets/timing.html'))
            for (abi,name),path in payloads().items():
                entry=('lib/'+abi+'/libwpar_shairport.so') if name=='shairport-sync' else ('assets/receiver/'+abi+'/'+name)
                self.assertEqual(hashlib.sha256(panel.read(entry)).digest(),hashlib.sha256(path.read_bytes()).digest())
                if name=='shairport-sync':self.assertEqual(panel.read(entry),receiver.read(entry))
            for apk in (panel,receiver):
                dex=b''.join(apk.read(n) for n in apk.namelist() if n.startswith('classes') and n.endswith('.dex'))
                for descriptor in (b'Lcom/wallpanel/audioreceiver/AudioOutputGate;',b'Lcom/wallpanel/audioreceiver/PcmPump;'):
                    self.assertIn(descriptor,dex)

if __name__=='__main__':unittest.main()
