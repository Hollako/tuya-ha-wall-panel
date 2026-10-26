package local.wallpanel.app;
/** Hardware recovers independently; unavailable Wi-Fi/MQTT never stops local control. */
public final class ActivationPolicy {
    public static boolean timedOut(boolean verified,boolean hardware,boolean mqtt,long age){return age>30000&&!hardware;}
    public static int nextRetry(int current){return Math.min(current*2,60000);}
    private ActivationPolicy(){}
}
