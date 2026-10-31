package local.wallpanel.app;
public final class NativeAssetsTests {
    static void check(boolean good){if(!good)throw new AssertionError();}
    public static void main(String[] args){
        check(NativeAssets.abi(new String[]{"armeabi-v7a","armeabi"}).equals("arm32"));
        check(NativeAssets.abi(new String[]{"armeabi-v7a","arm64-v8a"}).equals("arm64"));
        check(NativeAssets.prefix("a133",new String[]{"armeabi-v7a"}).equals("hardware/a133-arm32/"));
        check(NativeAssets.prefix("g10",new String[]{"arm64-v8a"}).equals("hardware/g10-arm64/"));
        boolean rejected=false;try{NativeAssets.abi(new String[]{"x86_64","x86"});}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        rejected=false;try{NativeAssets.prefix("../g10",new String[]{"arm64-v8a"});}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        PanelConfig c=new PanelConfig();c.profile="a133";c.validate();
        check(ModuleCatalog.find("dashboard").supported(c.profile));check(ModuleCatalog.find("sip").supported(c.profile));
        check(!ModuleCatalog.find("buttons").supported(c.profile));for(String id:new String[]{"display","relays","knob_input","knob_display","audio","amplifier"})check(ModuleCatalog.find(id).supported(c.profile));
        System.out.println("PASS: ARM32/ARM64 selection, profile-specific asset paths, unknown ABI/path refusal and A133 hardware guards.");
    }
}
