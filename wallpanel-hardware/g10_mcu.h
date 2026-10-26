/* G10 MCU framing, independent of Android/Tuya Java services. */
#ifndef WALLPANEL_G10_MCU_H
#define WALLPANEL_G10_MCU_H
#include <stddef.h>
#include <stdint.h>
#include <string.h>
enum g10_event_kind {G10_RELAY_STATE=1,G10_BUTTON_CLICK=2};
struct g10_event {enum g10_event_kind kind;int channel,value;};
typedef void (*g10_event_fn)(void *,const struct g10_event *);
struct g10_parser {unsigned char bytes[7];size_t used;};
static int g10_key(unsigned char id){return id==1?1:id==2?2:id==5?3:id==6?4:0;}
static void g10_feed(struct g10_parser *p,const unsigned char *data,size_t len,
                     g10_event_fn callback,void *context){
    for(size_t i=0;i<len;i++){
        p->bytes[p->used++]=data[i];
        if(p->used<7)continue;
        unsigned char *b=p->bytes;
        if(b[0]==0xfc&&b[1]==0&&b[2]==0&&b[6]==0xfe){
            struct g10_event event={0};
            if((b[3]==0xa1||b[3]==0xa2)&&b[4]<=1){
                event.kind=G10_RELAY_STATE;event.channel=b[3]-0xa0;event.value=b[4];
            }else if(b[3]==0&&b[4]==1&&g10_key(b[5])){
                event.kind=G10_BUTTON_CLICK;event.channel=g10_key(b[5]);event.value=b[5];
            }
            if(event.kind&&callback)callback(context,&event);
            p->used=0;
        }else{memmove(b,b+1,6);p->used=6;}
    }
}
/* No general raw-command API: only observed query, event-mode configuration,
 * two-relay writes and the vendor's exact global night-light commands. */
static void g10_query(unsigned char out[7]){const unsigned char q[7]={0xfc,0,0,0xaa,0xff,0,0xfe};memcpy(out,q,7);}
static int g10_relay(unsigned char out[7],int channel,int value){
    if((channel!=1&&channel!=2)||(value!=0&&value!=1))return -1;
    const unsigned char command[7]={0xfc,0,0,(unsigned char)(0xa0+channel),(unsigned char)value,0,0xfe};
    memcpy(out,command,7);return 0;
}
static int g10_scene(unsigned char out[7],int button){
    if(button<1||button>4)return -1;
    const unsigned char ids[]={1,2,5,6};
    const unsigned char frame[7]={0xfc,0,0x0b,0,0,ids[button-1],0xfe};
    memcpy(out,frame,7);return 0;
}
/* Vendor A485LedActivity/F5SmallLightUtil label this global "small night light".
 * Its physical target on G10 is not verified: do not call it button LED control
 * until the user confirms. There is no channel/color field in these frames. */
static int g10_light_power(unsigned char out[7],int on){
    if(on!=0&&on!=1)return -1;
    const unsigned char f[7]={0xfc,0,0x1d,(unsigned char)on,0,0,0xfe};memcpy(out,f,7);return 0;
}
static int g10_light_brightness(unsigned char out[7],int value){
    if(value<0||value>100)return -1;
    const unsigned char f[7]={0xfc,0,0x1d,2,(unsigned char)value,0,0xfe};memcpy(out,f,7);return 0;
}
static int g10_allowed(const unsigned char f[7]){
    if(f[0]!=0xfc||f[1]!=0||f[6]!=0xfe)return 0;
    if(f[2]==0x0b)return !f[3]&&!f[4]&&g10_key(f[5]);
    if(f[2]==0x1d)return !f[5]&&((f[3]<=1&&!f[4])||(f[3]==2&&f[4]<=100));
    return f[2]==0&&f[5]==0&&((f[3]==0xaa&&f[4]==0xff)||((f[3]==0xa1||f[3]==0xa2)&&f[4]<=1));
}
#endif
