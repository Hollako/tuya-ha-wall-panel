package local.wallpanel.app;
public final class ActivationTests {
    private static void check(boolean c){if(!c)throw new AssertionError();}
    public static void main(String[] args){
        check(!ActivationPolicy.timedOut(false,false,false,10000));
        check(!ActivationPolicy.timedOut(false,true,false,60000));
        check(ActivationPolicy.timedOut(false,false,true,31000));
        check(!ActivationPolicy.timedOut(false,true,true,16000));
        check(!ActivationPolicy.timedOut(true,true,false,16000));
        check(ActivationPolicy.timedOut(true,false,true,31000));
        check(ActivationPolicy.nextRetry(5000)==10000&&ActivationPolicy.nextRetry(60000)==60000);
        System.out.println("PASS: offline first boot keeps local hardware, only hardware timeout recovers, and retry backoff is bounded.");
    }
}
