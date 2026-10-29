"""Expose upstream AP2 remote commands without enabling extra broker/daemon interfaces."""
from pathlib import Path
import sys
work=Path(sys.argv[1])
path=work/'shairport.c';text=path.read_text()
marker='  activity_monitor_start();'
block='\n  extern int wpar_control_start(void);\n  if(wpar_control_start()) die("Private receiver control could not start");'
# Normalize previous build staging too. Reapplying this patch must never bind
# the same abstract socket twice during one engine startup.
text=text.replace(block,'')
assert text.count(marker)==1
text=text.replace(marker,marker+block)
path.write_text(text)
path=work/'ap2_event_receiver.c';text=path.read_text()
if 'WPAR_CONTROL_DEADLINES' not in text:
    marker='  pthread_mutex_lock(&conn->event_sender_mutex);'
    at=text.index(marker,text.index('ssize_t ap2_event_port_send_message('))
    replacement='''  /* WPAR_CONTROL_DEADLINES: event control must not hold a connection forever. */
  struct timespec deadline;clock_gettime(CLOCK_REALTIME,&deadline);deadline.tv_sec+=2;
  if(pthread_mutex_timedlock(&conn->event_sender_mutex,&deadline))return -1;'''
    text=text[:at]+text[at:].replace(marker,replacement,1)
    marker='  if (conn->event_channel_fd != 0) {'
    replacement='''  if (conn->event_channel_fd > 0) {
    struct timeval timeout={2,0};
    setsockopt(conn->event_channel_fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    setsockopt(conn->event_channel_fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));'''
    assert marker in text;text=text.replace(marker,replacement,1)
    text='#include <sys/time.h>\n#include <time.h>\n'+text
    path.write_text(text)
