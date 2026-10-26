"""Compile/run host fixtures in WSL; never contacts the panel."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[2]
base='/mnt/'+r.drive[0].lower()+r.as_posix()[2:]
tests=['unified-app/native/test-core.c','unified-app/native/test-knob-core.c','wallpanel-hardware/test-mcu-lease.c','wallpanel-hardware/test-knob-lease.c','wallpanel-hardware/test-direct-bridge.c','panel-172.16.1.26/test-mqtt-input.c','panel-172.16.1.26/test-rotary-decoder.c','panel-172.16.1.26/test-knob-renderer.c']
tests+=['unified-app/native/test-audio-core.c','wallpanel-hardware/test-audio-lease.c']
tests+=['panel-172.16.1.26/test-display.c']
tests+=['unified-app/native/test-management-core.c','unified-app/native/test-capture.c','wallpanel-hardware/test-management-lease.c']
tests+=['unified-app/native/test-health.c']
tests+=['unified-app/native/test-knob-activity.c']
for i,t in enumerate(tests):
    out='/tmp/wallpanel-05-test-'+str(i)
    subprocess.run(['wsl','-d','Ubuntu','--','gcc','-std=gnu11','-O1','-Wall','-Wextra','-Wno-unused-function','-Wno-misleading-indentation',base+'/'+t,'-o',out],check=True)
    subprocess.run(['wsl','-d','Ubuntu','--',out],check=True,timeout=15)
