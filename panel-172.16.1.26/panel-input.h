/* Shared G10 input handling: vendor press/hold plus physical rotary observer. */
static int input_device(const char *wanted) {
    for(int i=0;i<24;i++) {
        char path[128],name[128];snprintf(path,sizeof(path),"/sys/class/input/event%d/device/name",i);
        FILE *f=fopen(path,"r");if(!f)continue;
        char *ok=fgets(name,sizeof(name),f);fclose(f);if(!ok)continue;
        name[strcspn(name,"\r\n")]=0;if(strcmp(name,wanted))continue;
        snprintf(path,sizeof(path),"/dev/input/event%d",i);return open(path,O_RDONLY|O_NONBLOCK|O_CLOEXEC);
    }
    return -1;
}
static int process_input(int fd,int source,int publish_events) {
#ifdef WALLPANEL_DIRECT_MCU
    if(source==0&&knob_external)return 0;
#endif
    struct input_event events[32];ssize_t n;
    while((n=read(fd,events,sizeof(events)))>0) {
        for(size_t i=0;i<(size_t)n/sizeof(events[0]);i++) {
            struct input_event *e=&events[i];if(e->type!=EV_KEY||e->value!=1)continue;
            const char *event=NULL;
            if(source==0) {
                if(rotary_fd>=0&&(e->code==KEY_FN_F1||e->code==KEY_FN_F2))continue;
                if(e->code==KEY_FN_F1)event="rotate_left";
                else if(e->code==KEY_FN_F2)event="rotate_right";
                else if(e->code==KEY_FN_F3)event="press";
                else if(e->code==KEY_FN_F8)event="long_press";
            } else if(e->code==KEY_DOLLAR)event="button_press";
            if(!event)continue;
            if(publish_events && net>=0) {
                if(source==0){
                    if(e->code==KEY_FN_F3)gesture_pulse_at(0,e->code,ms());
                    else if(e->code==KEY_FN_F8)gesture_hold(0,e->code);
                    else {gesture_flush(0);gesture_publish(0,event,e->code);}
                    if(gesture_publish_failed)return -1;
                } else {
                    char payload[128];snprintf(payload,sizeof(payload),"{\"event_type\":\"button_press\",\"key_code\":%u}",e->code);
                    if(publish(BASE"/event",payload,0))return -1;
                }
            } else if(source==0)gesture_cancel(0);
        }
    }
    return n<0&&errno!=EAGAIN&&errno!=EINTR?-1:0;
}
