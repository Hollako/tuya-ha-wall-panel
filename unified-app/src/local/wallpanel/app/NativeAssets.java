package local.wallpanel.app;

/** Linux executables follow the OS-supported ARM ABIs, independent of Java/Dex. */
public final class NativeAssets {
    public static String abi(String[] supported){
        if(supported!=null){
            for(String item:supported)if("arm64-v8a".equals(item))return "arm64";
            for(String item:supported)if("armeabi-v7a".equals(item))return "arm32";
        }
        throw new IllegalArgumentException("This APK requires ARM32 or ARM64 Android");
    }
    public static String prefix(String profile,String[] supported){
        if(!"g10".equals(profile)&&!"a133".equals(profile))throw new IllegalArgumentException("Select a hardware profile first");
        return "hardware/"+profile+"-"+abi(supported)+"/";
    }
    private NativeAssets(){}
}
