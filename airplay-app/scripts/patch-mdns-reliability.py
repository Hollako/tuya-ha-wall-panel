"""Keep a single responder alive across TXT changes and support QU questions."""
from pathlib import Path
import sys,shutil
work=Path(sys.argv[1]);root=Path(__file__).resolve().parents[1]
shutil.copyfile(root/'native/wpar-mdns-updates.h',work/'wpar-mdns-updates.h')
p=work/'tinysvcmdns.c';s=p.read_text()
if 'WPAR_MDNS_RELIABILITY' not in s:
    s='#include <time.h>\n/* WPAR_MDNS_RELIABILITY */\n'+s
    marker='  struct rr_list *services;'
    assert s.count(marker)==1;s=s.replace(marker,marker+'\n  struct rr_list *wpar_updates;\n  time_t wpar_announce_at;')
    old='''      // check if it's a unicast query - we ignore those
      if (qn->unicast_query) {
        DEBUG_PRINTF("skipping unicast query\\n");
        continue;
      }'''
    assert old in s;s=s.replace(old,'      /* QU questions are answered to the source address by main_loop. */')
    marker='// main loop to receive, process and send out MDNS replies'
    assert marker in s;s=s.replace(marker,'#include "wpar-mdns-updates.h"\n\n'+marker)
    old='    select(max_fd + 1, &sockfd_set, NULL, NULL, NULL);'
    new='''    struct timeval refresh_wait={1,0};
    int selected=select(max_fd+1,&sockfd_set,NULL,NULL,&refresh_wait);
    if(selected<0){if(errno==EINTR)continue;log_message(LOG_ERR,"mDNS select failed: %m");break;}
    wpar_mdns_apply_updates(svr);
    wpar_mdns_refresh(svr);'''
    assert old in s;s=s.replace(old,new)
    old='''          send_packet(svr->sockfd, pkt_buffer, replylen);'''
    assert s.count(old)==1;s=s.replace(old,'''          if(wpar_mdns_wants_unicast(mdns))sendto(svr->sockfd,pkt_buffer,replylen,0,(struct sockaddr *)&fromaddr,sizeof(fromaddr));
          else send_packet(svr->sockfd,pkt_buffer,replylen);''')
    marker='  rr_group_destroy(s->group);'
    assert marker in s;s=s.replace(marker,'  rr_list_destroy(s->wpar_updates,1);\n'+marker)
    p.write_text(s)
p=work/'mdns_tinysvcmdns.c';s=p.read_text()
s=s.replace('extern int wpar_mdns_update_txt(struct mdnsd *,const char *,const char *,const char **);\n','')
a=s.index('static int wpar_mdns_update(char **txt,char **secondary){');b=s.index('static void mdns_tinysvcmdns_unregister',a)
s=s[:a]+'''extern int wpar_mdns_update_txt(struct mdnsd *,const char *,const char *,const char **);
static int wpar_mdns_update(char **txt,char **secondary){
 pthread_mutex_lock(&wpar_mdns_lock);
 int rc=-1;
 if(svr&&saved_ap1){
   char type[128];snprintf(type,sizeof(type),"%s.local",config.regtype);
   rc=wpar_mdns_update_txt(svr,saved_ap1,type,(const char **)txt);
   if(saved_ap2&&config.regtype2&&secondary){snprintf(type,sizeof(type),"%s.local",config.regtype2);if(wpar_mdns_update_txt(svr,saved_ap2,type,(const char **)secondary)<0)rc=-1;}
 }else if(saved_ap1)rc=register_inner(saved_ap1,saved_ap2,saved_port,txt,secondary);
 if(rc<0)warn("mDNS TXT update unavailable");
 pthread_mutex_unlock(&wpar_mdns_lock);return rc;
}
'''+s[b:]
p.write_text(s)
