/* Root-private, credential-free health snapshot in RAM for installation checks. */
#ifdef WP_HEALTH_PATH
static void core_health(const char *line){
    if(!core_leased)return;
    int fd=open(WP_HEALTH_PATH ".new",O_WRONLY|O_CREAT|O_CLOEXEC|O_NOFOLLOW,0600);if(fd<0)return;
    struct stat st;int safe=!fstat(fd,&st)&&S_ISREG(st.st_mode)&&st.st_uid==geteuid()&&!(st.st_mode&0077);
    size_t n=strlen(line);int ok=safe&&!ftruncate(fd,0)&&write(fd,line,n)==(ssize_t)n;if(close(fd))ok=0;
    if(ok)rename(WP_HEALTH_PATH ".new",WP_HEALTH_PATH);else unlink(WP_HEALTH_PATH ".new");
}
#else
static void core_health(const char *line){(void)line;}
#endif
