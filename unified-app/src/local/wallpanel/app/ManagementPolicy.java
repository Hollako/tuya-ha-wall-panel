package local.wallpanel.app;
public final class ManagementPolicy {
    public static final int LIMIT=8*1024*1024;
    public static boolean png(byte[] data){
        if(data==null||data.length<32||data.length>LIMIT)return false;
        int[] start={137,80,78,71,13,10,26,10},end={0,0,0,0,73,69,78,68,174,66,96,130};
        for(int i=0;i<start.length;i++)if((data[i]&255)!=start[i])return false;
        for(int i=0;i<end.length;i++)if((data[data.length-end.length+i]&255)!=end[i])return false;return true;
    }
    public static String command(PanelConfig c,MqttSession.Message m){
        if(m.retained||!m.topic.equals(c.mqttBase+"/device/command/set"))return null;
        return m.payload.equals("REBOOT")?"management reboot 0":m.payload.equals("SCREENSHOT")?"SCREENSHOT":null;
    }
    public static boolean due(long now,long anchor,boolean online,boolean owned,boolean busy){return online&&owned&&!busy&&now-anchor>=300000;}
    private ManagementPolicy(){}
}
