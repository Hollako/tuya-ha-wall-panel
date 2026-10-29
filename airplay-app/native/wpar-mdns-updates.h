/* Included in tinysvcmdns.c. TXT replacements run on its own responder thread. */
int wpar_mdns_update_txt(struct mdnsd *svr,const char *instance,const char *type,const char **txt){
  if(!svr||!instance||!type||!txt)return -1;
  uint8_t *label=create_label(instance),*kind=create_nlabel(type);
  if(!label||!kind){free(label);free(kind);return -1;}
  uint8_t *name=join_nlabel(label,kind);free(label);free(kind);if(!name)return -1;
  struct rr_entry *entry=rr_create(name,RR_TXT);
  for(const char **item=txt;*item;item++)rr_add_txt(entry,*item);
  pthread_mutex_lock(&svr->data_lock);
  struct rr_list *pending=svr->wpar_updates;
  for(;pending;pending=pending->next)if(!cmp_nlabel(pending->e->name,entry->name))break;
  if(pending){rr_entry_destroy(pending->e);pending->e=entry;}else rr_list_append(&svr->wpar_updates,entry);
  pthread_mutex_unlock(&svr->data_lock);
  write_pipe(svr->notify_pipe[1],".",1);return 0;
}
static void wpar_mdns_apply_updates(struct mdnsd *svr){
  pthread_mutex_lock(&svr->data_lock);
  struct rr_list *pending=svr->wpar_updates;svr->wpar_updates=NULL;
  for(struct rr_list *item=pending;item;item=item->next){
    struct rr_group *group=rr_group_find(svr->group,item->e->name);int applied=0;
    if(group)for(struct rr_list *record=group->rr;record;record=record->next)if(record->e->type==RR_TXT){
      rr_entry_destroy(record->e);record->e=item->e;item->e=NULL;applied=1;break;
    }
    if(!applied&&item->e){rr_entry_destroy(item->e);item->e=NULL;}
  }
  if(pending)for(struct rr_list *service=svr->services;service;service=service->next)rr_list_append(&svr->announce,service->e);
  pthread_mutex_unlock(&svr->data_lock);rr_list_destroy(pending,0);
}
static void wpar_mdns_refresh(struct mdnsd *svr){
  struct timespec now;clock_gettime(CLOCK_MONOTONIC,&now);
  if(now.tv_sec-svr->wpar_announce_at<60)return;
  svr->wpar_announce_at=now.tv_sec;
  pthread_mutex_lock(&svr->data_lock);
  for(struct rr_list *service=svr->services;service;service=service->next)rr_list_append(&svr->announce,service->e);
  pthread_mutex_unlock(&svr->data_lock);
}
static int wpar_mdns_wants_unicast(struct mdns_pkt *packet){
  if(!packet->num_qn)return 0;
  for(struct rr_list *q=packet->rr_qn;q;q=q->next)if(!q->e->unicast_query)return 0;
  return 1;
}
