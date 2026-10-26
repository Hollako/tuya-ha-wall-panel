package local.wallpanel.app;
public final class DisplayTests {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        PanelConfig c=new PanelConfig();c.mqttBase="wallpanel/test";c.selected.add("display");
        check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set","DISPLAY_OFF",false)).equals("display power 0"));
        check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set","REBOOT",false))==null);
        check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/device/brightness/set","0",false))==null);
        check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/display/policy/off_after/set","0",false)).equals("display off_after 0"));
        check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/display/policy/dim_after/set","60",true))==null);
        for(String value:new String[]{"01","-1","86401","1;reboot","1\n"})check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/display/policy/off_after/set",value,false))==null);
        c.selected.clear();check(DisplayTopics.command(c,new MqttSession.Message(c.mqttBase+"/device/command/set","DISPLAY_ON",false))==null);
        System.out.println("PASS: display exact routes, power/brightness distinction, retained/injection/range rejection and independent legacy reboot routing.");
    }
}
