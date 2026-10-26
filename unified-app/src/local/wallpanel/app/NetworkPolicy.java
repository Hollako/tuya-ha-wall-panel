package local.wallpanel.app;
/** Saved network choices are independent of credentials and hardware configuration. */
public final class NetworkPolicy {
    public String ha="auto",intercom="auto";
    public boolean haFallback=false,intercomFallback=false;
    public static final String[] IDS={"auto","ethernet","wifi"};
    public static final String[] NAMES={"Auto (Android default)","Ethernet","Wi-Fi"};
    public static boolean valid(String s){return "auto".equals(s)||"ethernet".equals(s)||"wifi".equals(s);}
    public void validate(){if(!valid(ha)||!valid(intercom))throw new IllegalArgumentException("Select Auto, Ethernet or Wi-Fi");}
    public static int index(String s){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(s))return i;throw new IllegalArgumentException("Unknown network choice");}
}
