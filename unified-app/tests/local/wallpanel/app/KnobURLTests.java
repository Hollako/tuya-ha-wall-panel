package local.wallpanel.app;
public final class KnobURLTests {
    static void check(boolean good){if(!good)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        PanelConfig c=new PanelConfig();c.mqttBase="wallpanel/test";c.selected.add("knob_display");
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/frame/set","Title\n20°\nDetail",false)).equals("knob frame 5469746c650a3230c2b00a44657461696c"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/led/color/set","White",false)).equals("knob led 5768697465"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/power/set","ON",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/activity/set","ON",false)).equals("knob activity 4f4e"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/activity/set","ON",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/unknown/set","ON",false))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/title/set","",false)).equals("knob title -"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/screensaver/set","OFF",false)).equals("knob screensaver 4f4646"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/screensaver/set","OFF",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/ring/set","OFF",false)).equals("knob ring 4f4646"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/ring/set","OFF",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/media_frame/set","Audio\n40%\nMuted\nON",false)).startsWith("knob media_frame "));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/media_frame/set","Audio\n40%\nMuted\nON",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/speaker_mute/set","ON",false))==null);
        c.selected.clear();check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/title/set","x",false))==null);
        check(DashboardConfig.validate("https://host/path?a=1&b=2#room").endsWith("#room"));check(DashboardConfig.validate("").equals(""));
        for(String bad:new String[]{"javascript:bad","file:///foo","http://user:pass@host/","http://host:0","http://host/a b","http://host/\n"}){
            boolean rejected=false;try{DashboardConfig.validate(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        }
        System.out.println("PASS: knob exact routes, UTF-8 framing, retained/disabled rejection and dashboard URL validation.");
    }
}
