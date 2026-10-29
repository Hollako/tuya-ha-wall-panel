"""Repeated builds must initialize the private receiver listener exactly once."""
from pathlib import Path
import tempfile,subprocess,sys
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as folder:
    work=Path(folder)
    block='\n  extern int wpar_control_start(void);\n  if(wpar_control_start()) die("Private receiver control could not start");'
    (work/'shairport.c').write_text('  activity_monitor_start();'+block+block)
    (work/'ap2_event_receiver.c').write_text('ssize_t ap2_event_port_send_message(void) {\n  pthread_mutex_lock(&conn->event_sender_mutex);\n  if (conn->event_channel_fd != 0) {\n  }\n}')
    patch=[sys.executable,str(root/'scripts/patch-control.py'),str(work)]
    subprocess.run(patch,check=True)
    once={p.name:p.read_text() for p in work.iterdir()}
    subprocess.run(patch,check=True)
    assert once=={p.name:p.read_text() for p in work.iterdir()}
    assert once['shairport.c'].count('if(wpar_control_start())')==1
print('PASS: repeated control patch is idempotent and repairs duplicate initializers')
