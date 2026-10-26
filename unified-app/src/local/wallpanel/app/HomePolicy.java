package local.wallpanel.app;
/** Home routing must not depend on MQTT login, hardware state, or an admin PIN. */
public final class HomePolicy {
    public static final int SETUP=0,LOAD=1,KEEP=2;
    public static int route(String desired,boolean enabled,String current,boolean hasView){
        if(!enabled)return SETUP;
        try{desired=PanelConfig.webUrl(desired,false);}catch(IllegalArgumentException e){return SETUP;}
        return hasView&&desired.equals(current)?KEEP:LOAD;
    }
    private HomePolicy(){}
}
