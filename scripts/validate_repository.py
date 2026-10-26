"""Validate HACS structure, metadata, syntax and local brand images offline."""
from pathlib import Path
import ast,json,struct

root=Path(__file__).resolve().parent.parent
component=root/'custom_components/tuya_wall_panel'
assert [p.name for p in (root/'custom_components').iterdir() if p.is_dir() and p.name!='__pycache__']==['tuya_wall_panel']
m=json.loads((component/'manifest.json').read_text())
assert m['domain']=='tuya_wall_panel'
assert all(m.get(k) for k in ('documentation','issue_tracker','codeowners','name','version'))
assert m['codeowners']==['@Hollako']
assert m['documentation']=='https://github.com/Hollako/tuya-ha-wall-panel'
assert m['issue_tracker']==m['documentation']+'/issues'
assert json.loads((root/'hacs.json').read_text())['name']=='Tuya HA Wall Panel'
for p in component.rglob('*.json'):json.loads(p.read_text())
for p in component.rglob('*.py'):ast.parse(p.read_text(encoding='utf-8-sig'),filename=str(p))
for name,size in (('icon.png',256),('icon@2x.png',512)):
    data=(component/'brand'/name).read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',data[16:24])==(size,size)
print('PASS: HACS root layout, manifest metadata, JSON/Python syntax and local brand sizes.')
