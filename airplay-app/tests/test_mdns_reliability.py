"""Real responder records/parser under fake logging; no panel or audio traffic."""
from pathlib import Path
import subprocess,tempfile,shutil,sys
root=Path(__file__).resolve().parents[1];source=root/'build/native-work/shairport-sync-5.6.1'
with tempfile.TemporaryDirectory() as folder:
    work=Path(folder);(work/'utilities').mkdir()
    for name in ('tinysvcmdns.c','tinysvcmdns.h','mdns_tinysvcmdns.c'):shutil.copyfile(source/name,work/name)
    patch=[sys.executable,str(root/'scripts/patch-mdns-reliability.py'),str(work)]
    subprocess.run(patch,check=True);first={name:(work/name).read_bytes() for name in ('tinysvcmdns.c','mdns_tinysvcmdns.c')}
    subprocess.run(patch,check=True);assert first=={name:(work/name).read_bytes() for name in first},'Patch must be idempotent'
    (work/'common.h').write_text('''#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <errno.h>
#include <pthread.h>
#include <unistd.h>
static void debug(int level,const char *format,...){(void)level;(void)format;}
static void warn(const char *format,...){(void)format;}
static void die(const char *format,...){fprintf(stderr,"%s",format);abort();}
static int named_pthread_create(pthread_t *t,const pthread_attr_t *a,void *(*f)(void*),void *p,const char *n){(void)n;return pthread_create(t,a,f,p);}
static int safe_socket_close(int *fd){int result=close(*fd);*fd=-1;return result;}
''')
    (work/'utilities/network_utilities.h').write_text('')
    (work/'test.c').write_text('''#include "tinysvcmdns.c"
#include <assert.h>
int main(void){
 struct mdnsd server={0};server.sockfd=42;pthread_mutex_init(&server.data_lock,NULL);assert(!pipe(server.notify_pipe));
 mdnsd_set_hostname(&server,"test.local",inet_addr("172.16.1.99"));
 const char *txt[]={"state=idle",NULL};struct mdns_service *svc=mdnsd_register_svc(&server,"Test","_airplay._tcp.local",7000,NULL,txt);mdns_service_destroy(svc);
 struct mdns_pkt query={0},reply={0};query.num_qn=1;struct rr_entry *q=rr_create(create_nlabel("_airplay._tcp.local"),RR_PTR);q->unicast_query=1;rr_list_append(&query.rr_qn,q);
 assert(process_mdns_pkt(&server,&query,&reply)>0);assert(wpar_mdns_wants_unicast(&query));q->unicast_query=0;assert(!wpar_mdns_wants_unicast(&query));
 const char *changed[]={"state=busy",NULL};assert(!wpar_mdns_update_txt(&server,"Test","_airplay._tcp.local",changed));
 const char *latest[]={"state=paused",NULL};assert(!wpar_mdns_update_txt(&server,"Test","_airplay._tcp.local",latest));assert(server.wpar_updates&&!server.wpar_updates->next);
 wpar_mdns_apply_updates(&server);assert(!server.wpar_updates);assert(server.sockfd==42);assert(server.services->e->ttl>0);
 struct rr_group *group=rr_group_find(server.group,(uint8_t*)"\\004Test\\010_airplay\\004_tcp\\005local");assert(group);
 int found=0;for(struct rr_list *r=group->rr;r;r=r->next)if(r->e->type==RR_TXT){assert(!strcmp((char*)r->e->data.TXT.txt+1,"state=paused"));found=1;}assert(found);
 uint8_t bytes[4096];mdns_init_reply(&reply,0);assert(process_mdns_pkt(&server,&query,&reply)>0);assert(mdns_encode_pkt(&reply,bytes,sizeof(bytes))>0);
 server.wpar_announce_at=-60;wpar_mdns_refresh(&server);assert(server.wpar_announce_at>=0&&server.announce);
 mdns_init_reply(&reply,0);rr_list_destroy(query.rr_qn,1);rr_group_destroy(server.group);rr_list_destroy(server.services,0);rr_list_destroy(server.announce,0);free(server.hostname);close(server.notify_pipe[0]);close(server.notify_pipe[1]);pthread_mutex_destroy(&server.data_lock);
 puts("PASS: QU answers, unicast routing, coalesced in-place TXT updates, unchanged socket/TTL, refresh and idempotent patch");
}''')
    subprocess.run(['gcc','-D_DEFAULT_SOURCE','-Wall','-Wextra','-Wno-unused-parameter','-pthread',str(work/'test.c'),'-o',str(work/'test')],check=True)
    subprocess.run([str(work/'test')],check=True,timeout=10)
