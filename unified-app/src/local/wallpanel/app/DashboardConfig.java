package local.wallpanel.app;
public final class DashboardConfig {
    public static String validate(String url){
        if(url==null||url.length()>255)throw new IllegalArgumentException("Dashboard URL too long");
        for(int i=0;i<url.length();i++)if(url.charAt(i)<=32||url.charAt(i)==127)throw new IllegalArgumentException("Invalid URL whitespace");
        return PanelConfig.webUrl(url,true);
    }
    private DashboardConfig(){}
}
