package local.wallpanel.app;
/** Exact settings scope; ordinary apps, dashboards and calls never get the button. */
final class SettingsBackPolicy {
    static boolean setup(String name){return name!=null&&(name.equals("com.wallpanel.multiroom.GroupManagerActivity")||name.equals("com.wallpanel.multiroom.GroupSettingsActivity")||name.equals("local.wallpanel.app.MainActivity")||name.equals("local.wallpanel.app.PanelSetupActivity")||name.equals("local.wallpanel.app.NetworkSettingsActivity")||name.equals("local.wallpanel.app.SipSettingsActivity")||name.equals("local.wallpanel.app.ReceiverSettingsActivity"));}
    static boolean androidSettings(String pkg){return "com.android.settings".equals(pkg)||"com.android.tv.settings".equals(pkg);}
    static boolean keyboard(String pkg,String name){return pkg!=null&&(pkg.contains("inputmethod")||(name!=null&&name.startsWith("android.inputmethodservice.")));}
    private SettingsBackPolicy(){}
}
