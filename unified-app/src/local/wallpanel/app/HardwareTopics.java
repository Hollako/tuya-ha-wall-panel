package local.wallpanel.app;

/** Parse only the existing two-relay/four-button protocol; never shell text. */
public final class HardwareTopics {
    public static String base(String value){
        if(value==null||value.length()>160||!value.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)+"))throw new IllegalArgumentException("Invalid MQTT base topic");return value;
    }
    public static String command(PanelConfig c,MqttSession.Message m){
        if(m.retained)return null;String base=base(c.mqttBase);
        for(int i=1;i<=2;i++)if(m.topic.equals(base+"/relay/"+i+"/set")){
            if(!c.selected.contains("relays"))return null;
            return m.payload.equals("ON")?"relay "+i+" 1":m.payload.equals("OFF")?"relay "+i+" 0":null;
        }
        for(int i=1;i<=4;i++)if(m.topic.equals(base+"/button/"+i+"/binding/set")){
            return m.payload.matches("[0-2],[0-2]")?"bind "+i+" "+m.payload.charAt(0)+" "+m.payload.charAt(2):null;
        }
        if(m.topic.equals(base+"/app/command/set")&&(m.payload.equals("OPEN_SETUP")||m.payload.equals("RELOAD_DASHBOARD")||m.payload.equals("RESTART_APP")))return m.payload;
        return null;
    }
    private HardwareTopics(){}
}
