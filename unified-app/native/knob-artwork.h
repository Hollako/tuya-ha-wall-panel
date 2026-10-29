/* Decode happens in Android. The fixed-size RGB file never contains instructions. */
#ifndef WP_KNOB_ART_PATH
#define WP_KNOB_ART_PATH "/data/user/0/local.wallpanel.app/files/knob-art.rgb"
#endif
static int core_knob_media_page,core_knob_art_present;
static unsigned char core_knob_art[19200];
static int core_knob_art_load(const char *id){
    if(!strcmp(id,"OFF")){core_knob_art_present=0;knob_dirty=1;knob_check_at=0;return 0;}
    core_knob_art_present=0;knob_dirty=1;knob_check_at=0;
    int fd=open(WP_KNOB_ART_PATH,O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return -1;
    struct stat info;unsigned char bytes[19264];size_t at=0;int bad=fstat(fd,&info)||!S_ISREG(info.st_mode)||info.st_size!=(off_t)sizeof(bytes);
    while(!bad&&at<sizeof(bytes)){ssize_t n=read(fd,bytes+at,sizeof(bytes)-at);if(n<0&&errno==EINTR)continue;if(n<=0){bad=1;break;}at+=(size_t)n;}close(fd);
    if(bad||memcmp(bytes,id,64))return -1;
    memcpy(core_knob_art,bytes+64,sizeof(core_knob_art));core_knob_art_present=1;knob_dirty=1;knob_check_at=0;return 0;
}
static int core_knob_art_draw(void){
    if(!core_knob_media_page||!core_knob_art_present)return 0;
    knob_line(knob_text[0],43,14,120,knob_rgb[knob_color]);
    for(int y=0;y<80;y++)for(int x=0;x<80;x++){unsigned char *p=core_knob_art+(y*80+x)*3;knob_pixel(80+x,50+y,((unsigned int)p[0]<<16)|((unsigned int)p[1]<<8)|p[2],255);}
    knob_line(knob_text[1],161,26,170,0xffffff);knob_line(knob_text[2],214,15,120,0xb8c8d0);return 1;
}
