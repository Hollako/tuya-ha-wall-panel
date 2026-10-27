from pathlib import Path
import sys
p=Path(sys.argv[1]);s=p.read_text()
if 'wpar_mdns_update' in s:raise SystemExit(0)
s=s.replace('static int mdns_tinysvcmdns_register(', 'static int register_inner(')
s=s.replace('__attribute__((unused)) ','')
a=s.index('  char hostname[100 + 6];');b=s.index('  if (getifaddrs',a)
s=s[:a]+'''  char hostname[100 + 6];
  snprintf(hostname,sizeof(hostname),"wpar-%02x%02x%02x%02x%02x%02x.local",
    config.ap1_prefix[0],config.ap1_prefix[1],config.ap1_prefix[2],
    config.ap1_prefix[3],config.ap1_prefix[4],config.ap1_prefix[5]);
'''+s[b:]
s=s.replace('    if (ifa->ifa_flags & IFF_LOOPBACK)', '    if (!ifa->ifa_addr || (ifa->ifa_flags & IFF_LOOPBACK))')
a=s.index('  char *txtwithoutmetadata[]');b=s.index('  if (config.regtype == NULL)',a)
s=s[:a]+'''  char *fallback[] = {MDNS_RECORD_WITH_METADATA, NULL};
  char **txt=txt_records ? txt_records : fallback;
'''+s[b:]
a=s.index('  free(extendedregtype);')
s=s[:a]+'''#ifdef CONFIG_AIRPLAY_2
  if(ap2name && config.regtype2 && secondary_txt_records) {
    char *type=malloc(strlen(config.regtype2)+7);
    if(!type) die("No memory for AirPlay 2 service");
    sprintf(type,"%s.local",config.regtype2);
    svc=mdnsd_register_svc(svr,ap2name,type,port,NULL,(const char **)secondary_txt_records);
    mdns_service_destroy(svc);free(type);
  }
#endif
'''+s[a:]
a=s.index('static void mdns_tinysvcmdns_unregister');b=s.index('mdns_backend mdns_tinysvcmdns',a)
s=s[:a]+'''static pthread_mutex_t wpar_mdns_lock=PTHREAD_MUTEX_INITIALIZER;
static char *saved_ap1,*saved_ap2;static int saved_port;
static int mdns_tinysvcmdns_register(char *a,char *b,int port,char **txt,char **secondary){
 pthread_mutex_lock(&wpar_mdns_lock);
 free(saved_ap1);free(saved_ap2);saved_ap1=strdup(a);saved_ap2=b?strdup(b):NULL;saved_port=port;
 int rc=register_inner(a,b,port,txt,secondary);
 pthread_mutex_unlock(&wpar_mdns_lock);return rc;
}
static int wpar_mdns_update(char **txt,char **secondary){
 pthread_mutex_lock(&wpar_mdns_lock);
 if(svr){mdnsd_stop(svr);svr=NULL;}
 int rc=saved_ap1?register_inner(saved_ap1,saved_ap2,saved_port,txt,secondary):-1;
 pthread_mutex_unlock(&wpar_mdns_lock);return rc;
}
static void mdns_tinysvcmdns_unregister(void){
 pthread_mutex_lock(&wpar_mdns_lock);
 if(svr){mdnsd_stop(svr);svr=NULL;}free(saved_ap1);free(saved_ap2);saved_ap1=saved_ap2=NULL;
 pthread_mutex_unlock(&wpar_mdns_lock);
}

'''+s[b:]
s=s.replace('.mdns_unregister = mdns_tinysvcmdns_unregister,','.mdns_unregister = mdns_tinysvcmdns_unregister,\n                                 .mdns_update = wpar_mdns_update,')
p.write_text(s)
