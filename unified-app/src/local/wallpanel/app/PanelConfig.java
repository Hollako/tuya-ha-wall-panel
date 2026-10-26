package local.wallpanel.app;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/** Platform-independent configuration; profile selection never operates hardware. */
public final class PanelConfig {
    public String deviceId="";
    public long revision=0;
    public String dashboard="", haUrl="", broker="", username="", password="", profile="generic",mqttBase="";
    public int port=1883;
    public boolean tls=false, lock=false;
    public Set<String> selected=new HashSet<String>();

    public static String webUrl(String value, boolean optional) {
        String text=value.trim();
        if(optional && text.length()==0)return text;
        if(text.length()>4096)throw new IllegalArgumentException("URL is too long");
        try {
            URI u=new URI(text);
            if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))
                ||u.getHost()==null||u.getHost().length()==0||u.getRawUserInfo()!=null
                ||u.getPort()>65535||u.getPort()==0||u.getPort() < -1)
                throw new IllegalArgumentException("Use an HTTP or HTTPS URL without embedded credentials");
            return text;
        } catch(java.net.URISyntaxException e) {throw new IllegalArgumentException("Invalid dashboard or HA URL");}
    }
    public void validateBroker() {
        if(broker.length()==0||broker.length()>253||!broker.equals(broker.trim())
            ||!broker.matches("[A-Za-z0-9._:-]+"))
            throw new IllegalArgumentException("Enter a broker hostname or IP, without a URL or path");
        if(port<1||port>65535)throw new IllegalArgumentException("Broker port must be 1 to 65535");
        if(username.length()>256||password.length()>4096)throw new IllegalArgumentException("MQTT credentials are too long");
        if(password.length()>0&&username.length()==0)throw new IllegalArgumentException("MQTT password requires a username");
    }
    public void validate() {
        if(mqttBase.length()>0)HardwareTopics.base(mqttBase);
        if(deviceId.length()>0&&!deviceId.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Invalid panel identity");
        dashboard=webUrl(dashboard,true);haUrl=webUrl(haUrl,true);
        if(port<1||port>65535)throw new IllegalArgumentException("Broker port must be 1 to 65535");
        if(!profile.equals("generic")&&!profile.equals("g10"))throw new IllegalArgumentException("Unknown hardware profile");
        if(broker.length()>0||username.length()>0||password.length()>0)validateBroker();
        for(String id:selected)if(ModuleCatalog.find(id)==null)throw new IllegalArgumentException("Unknown module");
    }
}
