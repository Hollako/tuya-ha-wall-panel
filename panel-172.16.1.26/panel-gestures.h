/* Independent completed-click classifiers. Native long gestures are supplied
 * by hardware; a one-shot MCU click is never mistaken for a held button. */
#define DOUBLE_PRESS_MS 400
struct pending_click { long long deadline; unsigned int code; };
static struct pending_click gesture_pending[5]; /* 0 knob, 1..4 panel buttons */
static void gesture_publish(int control,const char *event,unsigned int code);
static void gesture_cancel(int control){gesture_pending[control].deadline=0;}
static void gestures_cancel_all(void){memset(gesture_pending,0,sizeof(gesture_pending));}
static void gesture_flush(int control) {
    struct pending_click p=gesture_pending[control];gesture_cancel(control);
    if(p.deadline)gesture_publish(control,"press",p.code);
}
static void gesture_pulse_at(int control,unsigned int code,long long now) {
    struct pending_click *p=&gesture_pending[control];
    if(p->deadline&&now<p->deadline){gesture_cancel(control);gesture_publish(control,"double_press",code);return;}
    if(p->deadline)gesture_flush(control);
    p->deadline=now+DOUBLE_PRESS_MS;p->code=code;
}
static void gesture_hold(int control,unsigned int code) {
    gesture_cancel(control);gesture_publish(control,"long_press",code);
}
static void gestures_tick_at(long long now) {
    for(int i=0;i<5;i++)if(gesture_pending[i].deadline&&now>=gesture_pending[i].deadline)gesture_flush(i);
}
static int gestures_wait_ms(long long now,int ceiling) {
    for(int i=0;i<5;i++)if(gesture_pending[i].deadline){long long left=gesture_pending[i].deadline-now;if(left<ceiling)ceiling=left>0?(int)left:0;}
    return ceiling;
}
