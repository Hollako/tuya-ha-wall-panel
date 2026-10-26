/* G10 half-step quadrature decoder. A/B are inputs, never driven here.
 * The encoder rests at both 00 and 11: one detent is two valid transitions.
 * Contact bounce reverses its own partial progress; invalid jumps resync. */
struct rotary_decoder {int state,progress,ready;};
/* G10 mounting polarity, confirmed by the user: the positive electrical
 * sequence is counterclockwise. Public positive direction means clockwise. */
static int rotary_physical_direction(int electrical){return -electrical;}
static void rotary_reset(struct rotary_decoder *d,int state) {
    d->state=state;d->progress=0;d->ready=state==0||state==3;
}
static int rotary_transition(struct rotary_decoder *d,int state) {
    static const int delta[16]={0,-1,1,0,1,0,0,-1,-1,0,0,1,0,1,-1,0};
    if(state==d->state)return 0;
    int previous=d->state;d->state=state;
    if((previous^state)==3){d->progress=0;d->ready=state==0||state==3;return 0;}
    if(!d->ready){if(state==0||state==3){d->ready=1;d->progress=0;}return 0;}
    d->progress+=delta[previous*4+state];
    if(state==0||state==3) {
        int step=d->progress>=2?1:d->progress<=-2?-1:0;
        d->progress=0;return step;
    }
    return 0;
}
