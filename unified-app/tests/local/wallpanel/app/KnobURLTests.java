package local.wallpanel.app;
public final class KnobURLTests {
    static void check(boolean good){if(!good)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        PanelConfig c=new PanelConfig();c.mqttBase="wallpanel/test";c.selected.add("knob_display");
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/frame/set","Title\n20°\nDetail",false)).equals("knob frame 5469746c650a3230c2b00a44657461696c"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/led/color/set","White",false)).equals("knob led 5768697465"));
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/power/set","ON",true))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/unknown/set","ON",false))==null);
        check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/title/set","",false)).equals("knob title -"));
        c.selected.clear();check(KnobTopics.command(c,new MqttSession.Message(c.mqttBase+"/knob/display/title/set","x",false))==null);
        check(DashboardConfig.validate("https://host/path?a=1&b=2#room").endsWith("#room"));check(DashboardConfig.validate("").equals(""));
        for(String bad:new String[]{"javascript:bad","file:///foo","http://user:pass@host/","http://host:0","http://host/a b","http://host/\n"}){
            boolean rejected=false;try{DashboardConfig.validate(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        }
        System.out.println("PASS: knob exact routes, UTF-8 framing, retained/disabled rejection and dashboard URL validation.");
    }
}
