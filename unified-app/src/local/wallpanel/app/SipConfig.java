package local.wallpanel.app;
/** SIP policy is independent of MQTT and the hardware profile. No device defaults. */
public final class SipConfig {
    public String server="",user="",authUser="",password="",localAddress="";
    public int serverPort=5060,sipPort=5060,rtpPort=10000,ringSeconds=45,volume=75;
    public boolean autoAnswer=false,aec=true,noiseSuppressor=true,gainControl=true;
    public boolean ringtoneEnabled=true,videoEnabled=false;
    public int ringVolume=65;
    public String videoUrl="";
    public String stationBrand="generic",doorServer="",doorUser="",doorPassword="";
    public boolean doorOneEnabled=true,doorTwoEnabled=false;
    public static String[] stationBrandIds(){return new String[]{"generic","hikvision"};}
    public static String[] stationBrandNames(){return new String[]{"Generic SIP / RTSP (no door API)","Hikvision (ISAPI)"};}
    public void validate(){
        if(!stationBrand.equals("generic")&&!stationBrand.equals("hikvision"))throw new IllegalArgumentException("Unsupported door station brand");
        if(!host(server))throw new IllegalArgumentException("Enter a SIP server hostname or IPv4 address");
        if(!user.matches("[A-Za-z0-9_.+\\-]{1,128}"))throw new IllegalArgumentException("Enter a SIP extension");
        if(authUser.length()>0&&!authUser.matches("[A-Za-z0-9_.+@\\-]{1,128}"))throw new IllegalArgumentException("Invalid authentication user");
        if(password.length()>4096)throw new IllegalArgumentException("SIP password is too long");
        if(localAddress.length()>0&&(!ipv4(localAddress)||localAddress.equals("0.0.0.0")))throw new IllegalArgumentException("Local address must be IPv4, or blank for automatic");
        if(serverPort<1||serverPort>65535||sipPort<1024||sipPort>65535)throw new IllegalArgumentException("Invalid SIP port");
        if(rtpPort<1024||rtpPort>65534||(rtpPort&1)!=0||rtpPort==sipPort)throw new IllegalArgumentException("Use a separate even RTP port from 1024 to 65534");
        if(ringSeconds<5||ringSeconds>180||volume<0||volume>100)throw new IllegalArgumentException("Invalid ring timeout or call volume");
        if(ringVolume<0||ringVolume>100)throw new IllegalArgumentException("Ring volume must be 0 to 100");
        if(videoUrl.length()>4096)throw new IllegalArgumentException("Video URL is too long");
        if(doorServer.length()>0)try{java.net.URI u=new java.net.URI(doorServer);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(u.getPath()!=null&&u.getPath().length()>0&&!u.getPath().equals("/"))||u.getPort()==0||u.getPort()>65535)throw new Exception();}catch(Exception invalid){throw new IllegalArgumentException("Door server must be http(s)://host[:port], without a path");}
        if(doorUser.length()>128||doorPassword.length()>4096)throw new IllegalArgumentException("Invalid door login length");
        if(videoEnabled||videoUrl.length()>0)try{java.net.URI uri=new java.net.URI(videoUrl);if(!"rtsp".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().length()==0||uri.getPort()==0||uri.getPort()>65535||uri.getFragment()!=null)throw new Exception();}catch(Exception invalid){throw new IllegalArgumentException("Enter an RTSP video URL (rtsp://camera:554/path)");}
    }
    public static boolean host(String s){return s.length()>0&&s.length()<=253&&s.matches("[A-Za-z0-9][A-Za-z0-9._\\-]*");}
    public static boolean ipv4(String s){String[] p=s.split("\\.",-1);if(p.length!=4)return false;for(String x:p)try{if(!x.matches("[0-9]{1,3}")||Integer.parseInt(x)>255)return false;}catch(Exception e){return false;}return true;}
    public String authenticationUser(){return authUser.length()==0?user:authUser;}
    public boolean doorEnabled(int door){return stationBrand.equals("hikvision")&&doorServer.length()>0&&doorUser.length()>0&&(door==1?doorOneEnabled:door==2&&doorTwoEnabled);}
    public boolean sameConnection(SipConfig c){return server.equals(c.server)&&serverPort==c.serverPort&&user.equals(c.user)&&authUser.equals(c.authUser)&&password.equals(c.password)&&localAddress.equals(c.localAddress)&&sipPort==c.sipPort&&rtpPort==c.rtpPort;}
}
