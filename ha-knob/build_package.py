"""Package only this integration, never credentials or other HA configuration."""
import json,zipfile,shutil
from pathlib import Path
root=Path(__file__).resolve().parent
component=root.parent/'custom_components'/'tuya_wall_panel'
manifest=json.loads((component/'manifest.json').read_text(encoding='utf-8'))
if manifest['domain']!=component.name:
    raise ValueError('Component folder and manifest domain must match')
# Package the canonical translations without rewriting source/UI settings.
strings=json.loads((component/'strings.json').read_text(encoding='utf-8'))
assert strings==json.loads((component/'translations/en.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(root/'tuya-wall-panel.zip','w',zipfile.ZIP_DEFLATED) as out:
    for path in sorted(component.rglob('*')):
        if path.is_file() and '__pycache__' not in path.parts:out.write(path,path.relative_to(component.parent.parent))
print('Built',root/'tuya-wall-panel.zip')
exploded=root/'tuya-wall-panel'
if not exploded.resolve().is_relative_to(root.resolve()) or exploded.is_symlink() or exploded.is_junction():
    raise RuntimeError('Expanded package directory must stay inside this project')
for parent in (exploded/'custom_components',exploded/'custom_components/tuya_wall_panel'):
    if parent.is_symlink() or parent.is_junction():raise RuntimeError('Expanded package must not redirect to another directory')
shutil.copytree(component,exploded/'custom_components/tuya_wall_panel',dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
print('Updated expanded component folder for copying.')
