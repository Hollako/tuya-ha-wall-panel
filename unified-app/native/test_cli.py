"""Host CLI and Java-to-C settings interoperability checks; never --run hardware."""
import hashlib,json,os,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='wallpanel-native-cli-') as temp:
    temp=Path(temp);binary=temp/'worker'
    subprocess.run(['gcc','-O2','-Wall','-Wextra','-Werror','-Wno-unused-function','-Wno-misleading-indentation',str(ROOT/'native/wallpanel-core.c'),'-o',str(binary)],check=True)
    caps=json.loads(subprocess.check_output([str(binary),'--describe']))
    assert caps['v']==1 and caps['profile_verified'] is False and caps['network'] is False
    assert caps['buttons']==4 and caps['relays']==2 and caps['button_hold'] is False
    config=temp/'private.conf';config.write_bytes((ROOT/'test-classes/exported-native.conf').read_bytes());config.chmod(0o600)
    result=json.loads(subprocess.check_output([str(binary),'--check-config',str(config)]))
    assert result['configuration_valid'] is True and result['hardware_accessed'] is False
    config.chmod(0o644)
    assert subprocess.run([str(binary),'--check-config',str(config)],capture_output=True).returncode==3
    assert subprocess.run([str(binary),'--bad-command'],capture_output=True).returncode==2
    # Check the exact worker bytes packaged in the new APK, not a different local binary.
    metadata=json.loads((ROOT/'build-result.json').read_text())
    with zipfile.ZipFile(ROOT/metadata['apk']) as apk:
        base='assets/hardware/g10-arm64/'
        manifest=json.loads(apk.read(base+'manifest.json'))
        assert hashlib.sha256(apk.read(base+manifest['binary'])).hexdigest()==manifest['sha256']
        assert hashlib.sha256(apk.read(base+'wallpanel-capture')).hexdigest()==manifest['capture_sha256']
        assert manifest['automatic_activation'] is False and manifest['network'] is False
        assert 'app-signing-key.pem' not in apk.namelist()
    print('PASS: native CLI description and strict validation; Java-generated configuration accepted by C; insecure modes rejected; packaged ARM binary hash and inactive state verified. No worker activated.')
