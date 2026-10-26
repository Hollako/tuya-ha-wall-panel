#undef NDEBUG
#include <assert.h>
#include <stdio.h>
#include "g10_mcu.h"
static struct g10_event events[1024];static int count;
static void event(void*c,const struct g10_event*e){(void)c;assert(count<1024);events[count++]=*e;}
static void reset(struct g10_parser *p){memset(p,0,sizeof(*p));count=0;}
int main(void){
    const unsigned char reports[]={0xfc,0,0,0xa1,0,0,0xfe,0xfc,0,0,0xa2,1,2,0xfe};
    struct g10_parser parser;
    for(size_t split=0;split<=sizeof(reports);split++){
        reset(&parser);g10_feed(&parser,reports,split,event,NULL);g10_feed(&parser,reports+split,sizeof(reports)-split,event,NULL);
        assert(count==2&&events[0].channel==1&&events[0].value==0&&events[1].channel==2&&events[1].value==1);
    }
    const int ids[]={1,2,5,6};
    for(int k=0;k<4;k++){
        unsigned char click[]={0xfc,0,0,0,1,(unsigned char)ids[k],0xfe};
        reset(&parser);for(size_t i=0;i<7;i++)g10_feed(&parser,click+i,1,event,NULL);
        assert(count==1&&events[0].kind==G10_BUTTON_CLICK&&events[0].channel==k+1);
    }
    const unsigned char noise[]={0,0xff,0xfc,0,0x99,0xfd};
    reset(&parser);g10_feed(&parser,noise,sizeof(noise),event,NULL);g10_feed(&parser,reports,sizeof(reports),event,NULL);assert(count==2);
    for(int field=0;field<7;field++)for(int value=0;value<256;value++){
        unsigned char bad[7];memcpy(bad,reports,7);bad[field]=(unsigned char)value;
        reset(&parser);g10_feed(&parser,bad,7,event,NULL);
        if(field==0&&value!=0xfc)assert(!count);
        if((field==1||field==2)&&value)assert(!count);
        if(field==4&&value>1)assert(!count);
        if(field==6&&value!=0xfe)assert(!count);
        g10_feed(&parser,reports,sizeof(reports),event,NULL);
        assert(count>=2); /* Always resynchronize after a malformed frame. */
    }
    for(int channel=-1;channel<6;channel++)for(int value=-1;value<3;value++){
        unsigned char out[7];memset(out,0x55,7);
        int valid=(channel==1||channel==2)&&(value==0||value==1);
        assert((g10_relay(out,channel,value)==0)==valid);
        if(!valid)for(int i=0;i<7;i++)assert(out[i]==0x55);
        else assert(out[0]==0xfc&&out[3]==0xa0+channel&&out[4]==value&&out[6]==0xfe&&g10_allowed(out));
    }
    unsigned char query[7];g10_query(query);
    const unsigned char expected[]={0xfc,0,0,0xaa,0xff,0,0xfe};assert(!memcmp(query,expected,7)&&g10_allowed(query));
    for(int button=0;button<6;button++){
        unsigned char scene[7];int valid=button>=1&&button<=4;
        assert((g10_scene(scene,button)==0)==valid);
        if(valid)assert(g10_allowed(scene)&&scene[2]==0x0b&&g10_key(scene[5])==button);
    }
    const unsigned char forbidden[][7]={{0xfc,0,0,0xa3,1,0,0xfe},{0xfc,0,0x1b,0,0,0xff,0xfe},{0xfc,0,0x0b,0,0,3,0xfe},{0xfc,0,0,0xa1,2,0,0xfe}};
    for(size_t i=0;i<sizeof(forbidden)/sizeof(forbidden[0]);i++)assert(!g10_allowed(forbidden[i]));
    puts("PASS: all frame splits, bytewise keys, noise, malformed-byte resynchronization, two-relay/event-mode allowlists, status query.");return 0;
}
