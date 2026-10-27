"""Production external-app guard with fake Android tasks/settings. No device use."""
from pathlib import Path
import subprocess,tempfile
R=Path(__file__).resolve().parent
sources={
'android/os/Looper.java':'package android.os;public class Looper {public static Looper myLooper(){return new Looper();}public static void prepare(){}}',
'android/os/Process.java':'package android.os;public class Process {public static int uid=0;public static int myUid(){return uid;}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
'android/content/Intent.java':'package android.content;public class Intent {public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_REORDER_TO_FRONT=2;public String target;public Intent(String p){target=p;}public Intent addFlags(int f){return this;}}',
'android/content/ComponentName.java':'package android.content;public class ComponentName {String p;public ComponentName(String s){p=s;}public String getPackageName(){return p;}}',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public static boolean missing;public android.content.Intent getLaunchIntentForPackage(String p){return missing?null:new android.content.Intent(p);}}',
'android/content/Context.java':'package android.content;public class Context {public static final String ACTIVITY_SERVICE="activity";public int launches;public String last;public Object getSystemService(String s){return new android.app.ActivityManager();}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public Object getContentResolver(){return this;}public void startActivity(Intent i){launches++;last=i.target;}}',
'android/app/ActivityThread.java':'package android.app;public class ActivityThread {public static android.content.Context context=new android.content.Context();public static ActivityThread systemMain(){return new ActivityThread();}public android.content.Context getSystemContext(){return context;}}',
'android/app/ActivityManager.java':'package android.app;import java.util.*;public class ActivityManager {public static String foreground="local.wallpanel.app";public static class RunningTaskInfo {public android.content.ComponentName topActivity=new android.content.ComponentName(foreground);}public List<RunningTaskInfo> getRunningTasks(int maximum){if(maximum!=1)throw new AssertionError();return Collections.singletonList(new RunningTaskInfo());}}',
'android/provider/Settings.java':'package android.provider;public class Settings {public static class Global {public static String value;public static String getString(Object resolver,String key){if(!key.equals("policy_control"))throw new AssertionError();return value;}public static boolean putString(Object resolver,String key,String v){value=v;return true;}}}',
'Harness.java':'''import local.wallpanel.system.*;import local.wallpanel.app.AppModePolicy;import android.app.*;import android.provider.Settings;public class Harness {
static void check(boolean b){if(!b)throw new AssertionError();}static void tick(AppTaskGuard g)throws Exception{java.lang.reflect.Method m=AppTaskGuard.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(g);}
public static void main(String[] args)throws Exception{
System.setProperty("wallpanel.policy.state",args[0]);
AppModePolicy.validate("web","");AppModePolicy.validate("app","io.homeassistant.companion.android");
for(String bad:new String[]{"","local.wallpanel.app","example.app;sh","../example.app"}){boolean refused=false;try{AppModePolicy.validate("app",bad);}catch(IllegalArgumentException e){refused=true;}check(refused);}
check(AppTaskPolicy.allowed("local.wallpanel.app","example.app"));check(AppTaskPolicy.allowed("com.android.permissioncontroller","example.app"));check(!AppTaskPolicy.allowed("com.other.app","example.app"));
check(AppTaskPolicy.immersive(null,"example.app").equals("immersive.full=example.app"));check(AppTaskPolicy.immersive("immersive.full=*,-example.app:immersive.navigation=com.old","example.app").equals("immersive.full=*:immersive.navigation=com.old"));
String previous="immersive.navigation=com.old";Settings.Global.value=previous;AppTaskGuard g=new AppTaskGuard("example.app");check(Settings.Global.value.equals(previous));g.start();check(Settings.Global.value.contains(previous)&&Settings.Global.value.contains("immersive.full=example.app"));
tick(g);check(ActivityThread.context.launches==0);ActivityManager.foreground="example.app";tick(g);check(ActivityThread.context.launches==0);
ActivityManager.foreground="com.android.permissioncontroller";tick(g);check(ActivityThread.context.launches==0);
ActivityManager.foreground="com.other.app";tick(g);check(ActivityThread.context.launches==1&&ActivityThread.context.last.equals("example.app"));tick(g);check(ActivityThread.context.launches==1);
android.os.SystemClock.now+=2000;tick(g);check(ActivityThread.context.launches==2);g.close();check(Settings.Global.value.equals(previous));android.os.SystemClock.now+=20000;tick(g);check(ActivityThread.context.launches==2);
AppTaskGuard changed=new AppTaskGuard("example.app");changed.start();Settings.Global.value="external-change";changed.close();check(Settings.Global.value.equals("external-change"));
android.content.pm.PackageManager.missing=true;boolean refused=false;try{new AppTaskGuard("example.missing");}catch(java.io.IOException e){refused=true;}check(refused&&Settings.Global.value.equals("external-change"));
java.io.DataOutputStream backup=new java.io.DataOutputStream(new java.io.FileOutputStream(args[0]));backup.writeBoolean(true);backup.writeUTF(previous);backup.writeUTF("immersive.full=example.app");backup.close();Settings.Global.value="immersive.full=example.app";AppTaskGuard.restoreStale();check(Settings.Global.value.equals(previous)&&!new java.io.File(args[0]).exists());
android.os.Process.uid=2000;refused=false;try{new AppTaskGuard("example.app");}catch(SecurityException e){refused=true;}check(refused);
System.out.println("PASS: production app target validation, root-only guard, call/setup/permission exemptions, foreground recovery/backoff, lease cleanup and scoped immersive-policy restoration.");}}
'''}
with tempfile.TemporaryDirectory(dir=R/'test-classes',prefix='external-app-') as folder:
    temp=Path(folder);files=[]
    for name,text in sources.items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);files.append(p)
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe'
    prod=[R/'src/local/wallpanel/app/AppModePolicy.java',R/'system-ui/src/local/wallpanel/system/AppTaskPolicy.java',R/'system-ui/src/local/wallpanel/system/AppTaskGuard.java']
    subprocess.run([java,'-jar',str(R.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(temp/'classes'),*[str(p) for p in prod+files]],check=True)
    subprocess.run([java,'-cp',str(temp/'classes'),'Harness',str(temp/'policy-backup')],check=True,timeout=15)
