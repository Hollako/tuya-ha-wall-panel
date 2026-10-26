/* Auxiliary GC9A01 only. Never write the Android main framebuffer (fb1).
 * No UART access, network listeners, shell payloads or external image downloads.
 * Red/white are the two physically verified states of this panel's LED GPIO. */
#include <linux/fb.h>
#include <sys/ioctl.h>
#include "panel-knob-font.h"
static char knob_text[3][129]={"Home Assistant","--","Configure in HA"};
static char knob_saved_text[3][129]={"Home Assistant","--","Configure in HA"};
static const char *knob_fields[]={"title","value","detail"};
static const char *knob_colors[]={"Cyan","White","Red","Green","Blue","Amber","Purple"};
static const unsigned int knob_rgb[]={0x00cfff,0xffffff,0xff4040,0x40ef80,0x4080ff,0xffb020,0xc070ff};
static int knob_power=1,knob_follow=1,knob_led=0,knob_color=0;
static int knob_fb=-1,knob_ready,knob_dirty=1,knob_report=1,knob_physical=-1;
static long long knob_check_at;
static struct fb_var_screeninfo knob_var;
static uint16_t knob_pixels[240*240];
static unsigned int knob_utf8(const unsigned char **at) {
    const unsigned char *p=*at;unsigned int c=*p++,n=0,min=0;
    if(c<128){*at=p;return c;}
    if(c>=0xc2&&c<=0xdf){c&=31;n=1;min=128;}
    else if(c>=0xe0&&c<=0xef){c&=15;n=2;min=2048;}
    else if(c>=0xf0&&c<=0xf4){c&=7;n=3;min=65536;}
    else return 0xffffffff;
    while(n--){if((*p&0xc0)!=0x80)return 0xffffffff;c=(c<<6)|(*p++&63);}
    if(c<min||c>0x10ffff||(c>=0xd800&&c<=0xdfff))return 0xffffffff;
    *at=p;return c;
}
static int knob_valid_text(const char *text,int limit) {
    if(strlen(text)>128)return 0;
    const unsigned char *p=(const unsigned char *)text;int count=0;
    while(*p){unsigned int c=knob_utf8(&p);int supported=(c>=32&&c<=126)||(c>=160&&c<=255)||c==0x2013||c==0x2014||c==0x2019||c==0x2022||c==0x2212;if(!supported||++count>limit)return 0;}
    return 1;
}
static const struct knob_glyph *knob_glyph(unsigned int cp) {
    for(size_t i=0;i<sizeof(knob_glyphs)/sizeof(knob_glyphs[0]);i++)if(knob_glyphs[i].cp==cp)return &knob_glyphs[i];
    return &knob_glyphs['?'-32];
}
static uint16_t knob_pack(unsigned int rgb,unsigned int alpha) {
    unsigned int r=((rgb>>16)&255)*alpha/255,g=((rgb>>8)&255)*alpha/255,b=(rgb&255)*alpha/255;
    return ((r>>(8-knob_var.red.length))<<knob_var.red.offset)|((g>>(8-knob_var.green.length))<<knob_var.green.offset)|((b>>(8-knob_var.blue.length))<<knob_var.blue.offset);
}
static void knob_pixel(int x,int y,unsigned int rgb,unsigned int alpha) {
    /* The installed LCD is physically horizontally mirrored. */
    if(x>=0&&x<240&&y>=0&&y<240)knob_pixels[y*240+239-x]=knob_pack(rgb,alpha);
}
static int knob_width(const char *s,int size) {
    const unsigned char *p=(const unsigned char *)s;int width=0;
    while(*p)width+=knob_glyph(knob_utf8(&p))->advance;
    return width*size/(48*64);
}
static void knob_line(const char *s,int baseline,int size,int maximum,unsigned int rgb) {
    while(size>6&&knob_width(s,size)>maximum)size--;
    int origin=(240-knob_width(s,size))/2,cursor=0;
    const unsigned char *p=(const unsigned char *)s;
    while(*p){
        const struct knob_glyph *g=knob_glyph(knob_utf8(&p));
        int x=origin+cursor*size/(48*64);
        int w=(g->w*size+47)/48,h=(g->h*size+47)/48;
        for(int y=0;y<h;y++)for(int xx=0;xx<w;xx++){
            unsigned char a=knob_font_pixels[g->offset+(y*48/size)*g->w+(xx*48/size)];
            if(a)knob_pixel(x+g->x*size/48+xx,baseline+g->y*size/48+y,rgb,a);
        }
        cursor+=g->advance;
    }
}
static int knob_node(const char *path,int value) {
    int fd=open(path,O_WRONLY|O_CLOEXEC);if(fd<0)return -1;
    const char *s=value?"on\n":"off\n";size_t len=strlen(s);
    int ok=write(fd,s,len)==(ssize_t)len?0:-1;close(fd);return ok;
}
static int knob_main_awake(void) {
    FILE *f=fopen("/sys/class/backlight/backlight/brightness","r");if(!f)return 0;
    char s[64]={0};char *ok=fgets(s,sizeof(s),f);fclose(f);if(!ok)return 0;
    char *p=s;while(*p&&!isdigit((unsigned char)*p))p++;
    return *p&&strtol(p,NULL,10)>0;
}
static int knob_render(void) {
    memset(knob_pixels,0,sizeof(knob_pixels));
    unsigned int accent=knob_rgb[knob_color];
    for(int y=0;y<240;y++)for(int x=0;x<240;x++){int r=(x-120)*(x-120)+(y-120)*(y-120);if(r>=114*114&&r<=116*116)knob_pixel(x,y,accent,255);}
    knob_line(knob_text[0],69,24,180,accent);
    knob_line(knob_text[1],143,56,208,0xffffff);
    knob_line(knob_text[2],188,22,180,0xb8c8d0);
    size_t at=0;if(lseek(knob_fb,0,SEEK_SET)<0)return -1;
    while(at<sizeof(knob_pixels)){ssize_t n=write(knob_fb,(char *)knob_pixels+at,sizeof(knob_pixels)-at);if(n<=0)return -1;at+=n;}
    knob_dirty=0;return 0;
}
static int knob_save(void) {
    FILE *f=fopen(STATE"/knob-settings.new","w");if(!f)return -1;
    fprintf(f,"%d %d %d %d\n%s\n%s\n%s\n",knob_power,knob_follow,knob_led,knob_color,knob_saved_text[0],knob_saved_text[1],knob_saved_text[2]);
    int bad=fflush(f)||fsync(fileno(f));if(fclose(f))bad=1;
    if(bad||chmod(STATE"/knob-settings.new",0600)||rename(STATE"/knob-settings.new",STATE"/knob-settings"))return -1;
    return 0;
}
static void knob_load(void) {
    FILE *f=fopen(STATE"/knob-settings","r");if(!f)return;
    int power,follow,led,color;char texts[3][129],line[256];int ok=0;
    if(fgets(line,sizeof(line),f)&&sscanf(line,"%d %d %d %d",&power,&follow,&led,&color)==4&&power>=0&&power<=1&&follow>=0&&follow<=1&&led>=0&&led<=1&&color>=0&&color<7){
        ok=1;for(int i=0;i<3;i++){if(!fgets(line,sizeof(line),f)){ok=0;break;}line[strcspn(line,"\r\n")]=0;if(!knob_valid_text(line,i==1?16:32)){ok=0;break;}strcpy(texts[i],line);}
    }
    fclose(f);if(!ok)return;
    knob_power=power;knob_follow=follow;knob_led=led;knob_color=color;memcpy(knob_text,texts,sizeof(texts));memcpy(knob_saved_text,texts,sizeof(texts));
}
static void knob_init(void) {
    if(dry_run)return;
    knob_load();knob_fb=open("/dev/graphics/fb0",O_RDWR|O_CLOEXEC);
    struct fb_fix_screeninfo fix;
    if(knob_fb<0||ioctl(knob_fb,FBIOGET_VSCREENINFO,&knob_var)||ioctl(knob_fb,FBIOGET_FSCREENINFO,&fix)||strncmp(fix.id,"fb_gc9a01",9)||knob_var.xres!=240||knob_var.yres!=240||knob_var.bits_per_pixel!=16||fix.line_length!=480||fix.smem_len<sizeof(knob_pixels)||knob_var.red.length!=5||knob_var.green.length!=6||knob_var.blue.length!=5){
        if(knob_fb>=0)close(knob_fb);knob_fb=-1;puts("Knob display unavailable: unexpected framebuffer layout.");return;
    }
    if(knob_render()||knob_node("/proc/vendor/led_ctrl",knob_led)){close(knob_fb);knob_fb=-1;return;}
    knob_ready=1;puts("Auxiliary knob display ready: 240x240 RGB565, mirrored mounting corrected.");
}
static void knob_tick(void) {
    if(!knob_ready||ms()<knob_check_at)return;knob_check_at=ms()+200;
    if(knob_dirty&&knob_render()){puts("Knob screen write failed.");return;}
    int value=knob_power&&(!knob_follow||knob_main_awake());
    if(value!=knob_physical&&!knob_node("/proc/vendor/lcd_ctrl",value)){knob_physical=value;knob_report=1;}
}
static int knob_publish(void) {
#ifdef WALLPANEL_DIRECT_MCU
    if(knob_external)return 0;
#endif
    if(!knob_report)return 0;
    if(publish(BASE"/knob/input/availability","online",1))return -1;
    if(publish(BASE"/knob/display/availability",knob_ready?"online":"offline",1))return -1;
    if(!knob_ready){knob_report=0;return 0;}
    for(int i=0;i<3;i++){char t[160];snprintf(t,sizeof(t),BASE"/knob/display/%s/state",knob_fields[i]);if(publish(t,knob_text[i],1))return -1;}
    if(publish(BASE"/knob/display/power/state",knob_power?"ON":"OFF",1)||publish(BASE"/knob/display/follow_main/state",knob_follow?"ON":"OFF",1)||publish(BASE"/knob/display/color/state",knob_colors[knob_color],1)||publish(BASE"/knob/led/color/state",knob_led?"White":"Red",1)||publish(BASE"/knob/display/actual/state",knob_physical==1?"ON":"OFF",1))return -1;
    knob_report=0;return 0;
}
static void knob_command(const char *topic,const char *payload,int retained) {
#ifdef WALLPANEL_DIRECT_MCU
    if(knob_external||wp_lock_held(WP_KNOB_REQUEST))return;
#endif
    if(retained||dry_run||!knob_ready)return;
    if(!strcmp(topic,BASE"/knob/display/frame/set")){
        /* HA live frames are RAM-only: clocks and sensor changes must not
         * rewrite flash-backed user settings. Validate all lines atomically. */
        char frame[512];size_t len=strlen(payload);if(len>=sizeof(frame))return;
        memcpy(frame,payload,len+1);char *lines[3]={frame,NULL,NULL};
        lines[1]=strchr(frame,'\n');if(!lines[1])return;*lines[1]++=0;
        lines[2]=strchr(lines[1],'\n');if(!lines[2])return;*lines[2]++=0;
        if(strchr(lines[2],'\n'))return;
        for(int i=0;i<3;i++)if(!knob_valid_text(lines[i],i==1?16:32))return;
        int changed=0;for(int i=0;i<3;i++)if(strcmp(knob_text[i],lines[i])){strcpy(knob_text[i],lines[i]);changed=1;}
        if(changed){knob_dirty=1;knob_report=1;}return;
    }
    int matched=0;
    for(int i=0;i<3;i++){char t[160];snprintf(t,sizeof(t),BASE"/knob/display/%s/set",knob_fields[i]);if(!strcmp(t,topic)){
        if(!knob_valid_text(payload,i==1?16:32)){puts("Rejected invalid or overlength knob text.");knob_report=1;return;}
        if(!strcmp(knob_text[i],payload)&&!strcmp(knob_saved_text[i],payload)){knob_report=1;return;}strcpy(knob_text[i],payload);strcpy(knob_saved_text[i],payload);knob_dirty=1;matched=1;
    }}
    const char *switches[]={BASE"/knob/display/power/set",BASE"/knob/display/follow_main/set"};
    for(int i=0;i<2;i++)if(!strcmp(topic,switches[i])){if(strcmp(payload,"ON")&&strcmp(payload,"OFF"))return;int v=!strcmp(payload,"ON");if(i)knob_follow=v;else knob_power=v;matched=1;}
    if(!strcmp(topic,BASE"/knob/display/color/set"))for(int i=0;i<7;i++)if(!strcmp(payload,knob_colors[i])){knob_color=i;knob_dirty=1;matched=1;}
    if(!strcmp(topic,BASE"/knob/led/color/set")){
        if(strcmp(payload,"White")&&strcmp(payload,"Red"))return;
        int v=!strcmp(payload,"White");if(knob_node("/proc/vendor/led_ctrl",v))return;knob_led=v;matched=1;
    }
    if(matched){if(knob_save())puts("Could not persist knob display settings.");knob_check_at=0;knob_report=1;knob_tick();}
}
