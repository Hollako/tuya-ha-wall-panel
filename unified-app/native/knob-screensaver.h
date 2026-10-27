/* Auxiliary LCD only. Clock keeps ticking offline; HA supplies UTC offset/weather.
 * Drawing uses the same mirrored pixel path as the control pages. */
#include <time.h>
enum { SAVER_Y_OFFSET=6 };
static int saver_enabled=1,saver_offset,saver_12h,saver_idle_last=-1;
static long long saver_minute=-1;
static char saver_condition[32]="unavailable",saver_temperature[129]="--";
#ifndef WP_KNOB_SAVER_PATH
#define WP_KNOB_SAVER_PATH STATE"/knob-screensaver"
#endif
static const char *saver_conditions[]={"sunny","clear-night","partlycloudy","cloudy","fog","rainy","pouring","snowy","snowy-rainy","hail","lightning","lightning-rainy","windy","windy-variant","exceptional","unavailable"};
static int saver_idle(long long now){return saver_enabled&&core_knob_activity_enabled&&core_knob_activity_until<=now;}
static int saver_parse(const char *payload,int apply){
    if(!strcmp(payload,"OFF")){if(apply){saver_enabled=0;knob_dirty=1;knob_check_at=0;}return 1;}
    char b[256];if(strlen(payload)>=sizeof(b))return 0;strcpy(b,payload);
    char *part[4]={b};for(int i=1;i<4;i++){part[i]=strchr(part[i-1],'\n');if(!part[i])return 0;*part[i]++=0;}
    char *end;long offset=strtol(part[0],&end,10);
    if(!*part[0]||*end||offset< -86400||offset>86400|| (strcmp(part[1],"12h")&&strcmp(part[1],"24h"))||!knob_valid_text(part[3],16))return 0;
    int known=0;for(size_t i=0;i<sizeof(saver_conditions)/sizeof(*saver_conditions);i++)if(!strcmp(part[2],saver_conditions[i]))known=1;
    if(!known)return 0;
    if(apply){saver_enabled=1;saver_offset=(int)offset;saver_12h=!strcmp(part[1],"12h");strcpy(saver_condition,part[2]);strcpy(saver_temperature,part[3]);knob_dirty=1;knob_check_at=0;}
    return 1;
}
static int saver_save(const char *payload){
    char previous[256]={0};FILE *f=fopen(WP_KNOB_SAVER_PATH,"r");
    if(f){size_t n=fread(previous,1,sizeof(previous)-1,f);fclose(f);previous[n]=0;if(!strcmp(previous,payload))return 0;}
    char temp[512];snprintf(temp,sizeof(temp),"%s.new",WP_KNOB_SAVER_PATH);
    f=fopen(temp,"w");if(!f)return -1;
    int bad=fputs(payload,f)<0||fflush(f)||fsync(fileno(f));if(fclose(f))bad=1;
    if(bad||chmod(temp,0600)||rename(temp,WP_KNOB_SAVER_PATH))return -1;return 0;
}
static void saver_load(void){
    char b[256]={0};FILE *f=fopen(WP_KNOB_SAVER_PATH,"r");if(!f)return;
    size_t n=fread(b,1,sizeof(b)-1,f);int extra=fgetc(f);fclose(f);
    if(extra!=EOF)return;b[n]=0;saver_parse(b,1);
}
static int saver_icon_dx,saver_icon_dy;
static void saver_disc(int cx,int cy,int radius,unsigned int color){
    for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)if(x*x+y*y<=radius*radius)knob_pixel(cx+x+saver_icon_dx,cy+y+saver_icon_dy,color,255);
}
static void saver_stroke(int x,int y,int xx,int yy,int radius,unsigned int color){
    int dx=abs(xx-x),sx=x<xx?1:-1,dy=-abs(yy-y),sy=y<yy?1:-1,err=dx+dy;
    for(;;){saver_disc(x,y,radius,color);if(x==xx&&y==yy)break;int e=2*err;if(e>=dy){err+=dy;x+=sx;}if(e<=dx){err+=dx;y+=sy;}}
}
static void saver_icon(void){
    const char *c=saver_condition;
    /* Center each complete icon (including rays/precipitation) at (120,157),
     * below the date. Keep a separate band above the temperature. */
    saver_icon_dx=-1;saver_icon_dy=17;
    if(!strcmp(c,"sunny")){saver_icon_dx=8;saver_icon_dy=23;}
    else if(!strcmp(c,"partlycloudy")){saver_icon_dx=2;saver_icon_dy=23;}
    else if(!strcmp(c,"clear-night")){saver_icon_dx=0;saver_icon_dy=16;}
    else if(!strcmp(c,"windy")||!strcmp(c,"windy-variant")){saver_icon_dx=1;saver_icon_dy=16;}
    else if(strstr(c,"lightning")||!strcmp(c,"exceptional"))saver_icon_dy=6;
    else if(strstr(c,"rain")||strstr(c,"snow")||!strcmp(c,"pouring")||!strcmp(c,"hail")||!strcmp(c,"fog"))saver_icon_dy=9;
    saver_icon_dy+=SAVER_Y_OFFSET;
    int sun=!strcmp(c,"sunny")||!strcmp(c,"partlycloudy"),night=!strcmp(c,"clear-night");
    if(sun){saver_disc(112,128,14,0xffc440);for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++)if(i||j)saver_stroke(112+i*21,128+j*21,112+i*25,128+j*25,1,0xffc440);}
    if(night){saver_disc(120,135,23,0xf0df9a);saver_disc(130,126,22,0);saver_disc(145,116,2,0xffffff);}
    if(!strcmp(c,"unavailable")){knob_line("--",160+SAVER_Y_OFFSET,30,110,0x80949e);return;}
    if(sun&&!strcmp(c,"sunny"))return;if(night)return;
    if(!strcmp(c,"windy")||!strcmp(c,"windy-variant")){
        for(int i=0;i<3;i++)saver_stroke(90+i*4,125+i*10,148-i*5,125+i*10,2,0xaac9da);return;
    }
    saver_disc(104,140,12,0xc6d9e5);saver_disc(121,132,17,0xc6d9e5);saver_disc(138,141,12,0xc6d9e5);
    saver_stroke(103,148,139,148,5,0xc6d9e5);
    if(!strcmp(c,"fog")){for(int i=0;i<2;i++)saver_stroke(91,161+i*7,149,161+i*7,1,0x91aebb);}
    if(strstr(c,"rain")||!strcmp(c,"pouring"))for(int i=0;i<3;i++)saver_stroke(103+i*17,160,99+i*17,169,1,0x40b9ff);
    if(strstr(c,"snow")||!strcmp(c,"hail"))for(int i=0;i<3;i++){int x=100+i*20;saver_stroke(x-3,164,x+3,164,1,0xffffff);saver_stroke(x,161,x,167,1,0xffffff);}
    if(strstr(c,"lightning")||!strcmp(c,"exceptional")){saver_stroke(124,151,117,162,2,0xffc440);saver_stroke(117,162,127,161,2,0xffc440);saver_stroke(127,161,121,173,2,0xffc440);}
}
static int core_knob_saver_draw(void){
    if(!saver_idle(ms()))return 0;
    time_t stamp=time(NULL)+saver_offset;struct tm local;gmtime_r(&stamp,&local);
    char clock[32],date[48];strftime(clock,sizeof(clock),saver_12h?"%I:%M":"%H:%M",&local);
    if(saver_12h&&clock[0]=='0')memmove(clock,clock+1,strlen(clock));
    strftime(date,sizeof(date),"%a %d %b %Y",&local);
    knob_line(clock,76+SAVER_Y_OFFSET,54,188,0xffffff);knob_line(date,107+SAVER_Y_OFFSET,20,190,0xaac1cd);
    if(saver_12h)knob_line(local.tm_hour>=12?"PM":"AM",35+SAVER_Y_OFFSET,13,100,0xaac1cd);
    saver_icon();knob_line(saver_temperature,217+SAVER_Y_OFFSET,29,150,0xffffff);
    return 1;
}
static void saver_tick(long long now){
    int idle=saver_idle(now);long long minute=((long long)time(NULL)+saver_offset)/60;
    if(idle!=saver_idle_last||(idle&&minute!=saver_minute)){knob_dirty=1;knob_check_at=0;saver_idle_last=idle;saver_minute=minute;}
}
