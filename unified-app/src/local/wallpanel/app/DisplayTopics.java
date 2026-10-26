package local.wallpanel.app;
public final class DisplayTopics {
    public static String command(PanelConfig c,MqttSession.Message m){
        if(m.retained||!c.selected.contains("display"))return null;
        String field=null;
        if(m.topic.equals(c.mqttBase+"/device/command/set")){
            if(m.payload.equals("DISPLAY_ON"))return "display power 1";if(m.payload.equals("DISPLAY_OFF"))return "display power 0";return null;
        }
        if(m.topic.equals(c.mqttBase+"/device/brightness/set"))field="brightness";
        for(String key:new String[]{"dim_after","off_after","dim_brightness"})if(m.topic.equals(c.mqttBase+"/display/policy/"+key+"/set"))field=key;
        if(field==null||!m.payload.matches("(?:0|[1-9][0-9]{0,4})"))return null;
        int value=Integer.parseInt(m.payload);if(value>86400||((field.equals("brightness")||field.equals("dim_brightness"))&&(value<1||value>255)))return null;
        return "display "+field+" "+value;
    }
    private DisplayTopics(){}
}
