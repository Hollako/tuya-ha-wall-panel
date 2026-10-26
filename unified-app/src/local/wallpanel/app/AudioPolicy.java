package local.wallpanel.app;
public final class AudioPolicy {
    public static boolean command(String name){return name!=null&&name.matches("volume|mute|source|external_speakers|play_url|play|pause|stop|off|on|next|previous|seek|status");}
    public static String level(String value){
        if(value==null||!value.matches("(?:0|[1-9][0-9]?|100)"))return null;return "audio level "+value;
    }
    private AudioPolicy(){}
}
