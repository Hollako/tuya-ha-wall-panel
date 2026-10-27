package local.wallpanel.app;

/** Exact knob routes; UTF-8 is hex encoded across the ASCII-only native pipe. */
public final class KnobTopics {
    public static String command(PanelConfig c,MqttSession.Message m)throws Exception {
        if(m.retained||!c.selected.contains("knob_display"))return null;
        String prefix=c.mqttBase+"/knob/display/",field=null;
        if(m.topic.startsWith(prefix)&&m.topic.endsWith("/set"))field=m.topic.substring(prefix.length(),m.topic.length()-4);
        else if(m.topic.equals(c.mqttBase+"/knob/led/color/set"))field="led";
        if(field==null||!field.matches("frame|media_frame|title|value|detail|power|follow_main|activity|screensaver|ring|color|led"))return null;
        return encode(field,m.payload);
    }
    static String encode(String field,String payload)throws Exception {
        byte[] bytes=payload.getBytes("UTF-8");if(bytes.length>511)return null;
        StringBuilder hex=new StringBuilder();for(byte b:bytes){int n=b&255;hex.append("0123456789abcdef".charAt(n>>4)).append("0123456789abcdef".charAt(n&15));}
        return "knob "+field+" "+(bytes.length==0?"-":hex.toString());
    }
    private KnobTopics(){}
}
