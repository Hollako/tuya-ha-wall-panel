/* Request + resource locks: acknowledge yield only after all old handles close. */
#ifdef WALLPANEL_DIRECT_MCU
static int knob_owner_fd=-1;
static void knob_owner_tick(struct pollfd *fds){
    if(wp_lock_held(WP_KNOB_REQUEST)){
        if(!knob_external||fds[0].fd>=0||knob_fb>=0||rotary_fd>=0||knob_owner_fd>=0){
            knob_external=1;gesture_cancel(0);rotary_cleanup();
            if(fds[0].fd>=0)close(fds[0].fd);fds[0].fd=-1;
            if(knob_fb>=0)close(knob_fb);knob_fb=-1;knob_ready=0;
            if(knob_owner_fd>=0)close(knob_owner_fd);knob_owner_fd=-1;
        }
        return;
    }
    if(knob_owner_fd>=0)return;
    if(fds[0].fd>=0){close(fds[0].fd);fds[0].fd=-1;}
    int owner=wp_lock_acquire(WP_KNOB_RESOURCE);if(owner<0)return;
    if(fds[0].fd>=0)close(fds[0].fd);
    fds[0].fd=input_device("knod-aispeech");
    if(fds[0].fd<0){close(owner);return;}
    knob_owner_fd=owner;knob_external=0;gesture_cancel(0);
    knob_physical=-1;knob_check_at=0;knob_dirty=knob_report=1;
    knob_init();rotary_init();
}
#else
static void knob_owner_tick(struct pollfd *fds){(void)fds;}
#endif
