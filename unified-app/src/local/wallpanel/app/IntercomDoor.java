package local.wallpanel.app;
import java.io.IOException;
/** Brand routing only; vendor wire protocols belong to their own adapter. */
final class IntercomDoor {
    interface Connections {java.net.HttpURLConnection open(java.net.URL url)throws java.io.IOException;}
    interface Guard {boolean allowed();}
    static void open(SipConfig config,int door,Guard guard)throws Exception{
        open(config,door,guard,new Connections(){public java.net.HttpURLConnection open(java.net.URL url)throws java.io.IOException{return (java.net.HttpURLConnection)url.openConnection();}});
    }
    static void open(SipConfig config,int door,Guard guard,Connections connections)throws Exception{
        config.validate();
        if(!"hikvision".equals(config.stationBrand))throw new IOException("Door control is not supported for the selected brand");
        HikvisionDoor.open(config,door,guard,connections);
    }
    private IntercomDoor(){}
}
