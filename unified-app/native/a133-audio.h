/* A133 uses Android/receiver volume; never touches G10 codec registers. */
static int core_audio_owner=-1,core_audio_level_last=-1;
static int core_audio_owned(void){return core_audio_owner>=0;}
static void core_audio_close(void){}
static void core_audio_tick(void){if(core_audio_owner<0){core_audio_owner=wp_lock_acquire(WP_AUDIO_RESOURCE);if(core_audio_owner>=0)core_status(0);}}
static int core_audio_command(const char *field,int value){if(!core_audio_owned()||value<0||value>1)return -1;int bad=-1;if(!strcmp(field,"power")&&core_config.amplifier)bad=a133_write_node("/proc/vendor/outside_pa",value);else if(!strcmp(field,"speakers")&&core_config.audio)bad=a133_write_node("/proc/vendor/horn_mode",value);if(!bad){char b[160];snprintf(b,sizeof(b),"{\"v\":1,\"type\":\"audio_result\",\"command\":\"%s\",\"ok\":true}\n",!strcmp(field,"speakers")?"speaker_saved":"route");WP_OUTPUT(b);}return bad;}
