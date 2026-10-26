"""Check strings in the actual APK's DEX, not just the Java source text."""
from pathlib import Path
import json,zipfile
root=Path(__file__).resolve().parents[1]
metadata=json.loads((root/'build-result.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(root/metadata['apk']) as apk:dex=apk.read('classes.dex')
assert ' — not migrated'.encode('utf-8') in dex
assert 'Connecting to broker…'.encode('utf-8') in dex
for broken in ('â€','Ã¢','â€“','â€”','â€¦'):
    assert broken.encode('utf-8') not in dex,repr(broken)
print('PASS: APK DEX contains correct Unicode UI labels and no Windows-encoding mojibake.')
