package local.wallpanel.app;
/** No hardware or app-internal controls: validate the supervised launch target. */
public final class AppModePolicy {
    public static String packageName(String value){
        if(value==null||value.length()>160||!value.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new IllegalArgumentException("Choose an installed app");
        if(value.equals("local.wallpanel.app"))throw new IllegalArgumentException("Choose an app other than Tuya HA Wall Panel");
        return value;
    }
    public static void validate(String mode,String target){
        if(!"web".equals(mode)&&!"app".equals(mode))throw new IllegalArgumentException("Unknown display mode");
        if("app".equals(mode))packageName(target);
    }
    private AppModePolicy(){}
}
