"""Run the real restart receiver against fake Android services; no device access."""
from pathlib import Path
import subprocess,tempfile

root=Path(__file__).resolve().parent
sources={
'local/wallpanel/app/ExternalAppService.java':'package local.wallpanel.app;class ExternalAppService {}',
'com/wallpanel/audioreceiver/ReceiverService.java':'package com.wallpanel.audioreceiver;public class ReceiverService {}',
'android/content/Context.java':'''package android.content;public class Context {public static final String ALARM_SERVICE="alarm";public Context getApplicationContext(){return this;}public Object getSystemService(String name){return local.wallpanel.app.RecoveryHarness.alarm;}public boolean stopService(Intent i){local.wallpanel.app.RecoveryHarness.record("stop:"+i.type.getSimpleName());return true;}public void startActivity(Intent i){local.wallpanel.app.RecoveryHarness.record("home");}}''',
'android/content/Intent.java':'''package android.content;public class Intent {public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;public Class<?> type;private String action;public Intent(Context c,Class<?> cls){type=cls;}public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}public Intent addFlags(int f){if(f!=FLAG_ACTIVITY_NEW_TASK)throw new AssertionError();return this;}}''',
'android/content/BroadcastReceiver.java':'''package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}''',
'android/app/PendingIntent.java':'''package android.app;public class PendingIntent {public static final int FLAG_CANCEL_CURRENT=1,FLAG_IMMUTABLE=2;public android.content.Intent intent;public static PendingIntent getBroadcast(android.content.Context c,int id,android.content.Intent i,int flags){if(id!=73||flags!=3)throw new AssertionError();PendingIntent p=new PendingIntent();p.intent=i;return p;}}''',
'android/app/AlarmManager.java':'''package android.app;public class AlarmManager {public static final int ELAPSED_REALTIME_WAKEUP=2;public PendingIntent launch;public boolean fail;public void setExact(int type,long when,PendingIntent p){if(fail)throw new IllegalStateException();if(type!=2||when!=2600)throw new AssertionError();launch=p;local.wallpanel.app.RecoveryHarness.record("alarm");}}''',
'android/os/Looper.java':'''package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java':'''package android.os;public class Handler {public static Runnable delayed;public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public boolean postDelayed(Runnable r,long delay){if(delay!=1000)throw new AssertionError();delayed=r;local.wallpanel.app.RecoveryHarness.record("scheduled-exit");return true;}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock {public static long elapsedRealtime(){return 100;}}''',
'android/os/Process.java':'''package android.os;public class Process {public static int myPid(){return 42;}public static void killProcess(int pid){if(pid!=42)throw new AssertionError();local.wallpanel.app.RecoveryHarness.record("exit");}}''',
'local/wallpanel/app/ConfigStore.java':'''package local.wallpanel.app;class ConfigStore {ConfigStore(android.content.Context c){}void runtimeStatus(String s){RecoveryHarness.record("error");}}''',
'local/wallpanel/app/BridgeService.java':'''package local.wallpanel.app;class BridgeService {static void ensureStarted(android.content.Context c){RecoveryHarness.record("hardware-start");}}''',
'local/wallpanel/app/SipService.java':'''package local.wallpanel.app;class SipService {}''',
'local/wallpanel/app/SipRuntime.java':'''package local.wallpanel.app;class SipRuntime {static void reconcile(android.content.Context c){RecoveryHarness.record("sip-start");}}''',
'local/wallpanel/app/HomeActivity.java':'''package local.wallpanel.app;class HomeActivity {}''',
'local/wallpanel/app/RecoveryHarness.java':'''package local.wallpanel.app;import java.util.*;import android.content.*;public class RecoveryHarness {public static android.app.AlarmManager alarm=new android.app.AlarmManager();static List<String> events=new ArrayList<String>();public static void record(String s){events.add(s);}static void check(boolean b){if(!b)throw new AssertionError(events.toString());}public static void main(String[] args){Context c=new Context();Runnable ready=new Runnable(){public void run(){record("offline");}};AppRestartReceiver r=new AppRestartReceiver();r.onReceive(c,null);r.onReceive(c,new Intent(c,AppRestartReceiver.class).setAction("wrong"));check(events.isEmpty());alarm.fail=true;AppRestartReceiver.request(c,ready);check(events.equals(Arrays.asList("error")));check(android.os.Handler.delayed==null);events.clear();alarm.fail=false;AppRestartReceiver.request(c,ready);check(events.equals(Arrays.asList("alarm","offline","stop:BridgeService","stop:SipService","stop:ExternalAppService","stop:ReceiverService","scheduled-exit")));int count=events.size();AppRestartReceiver.request(c,ready);check(events.size()==count);android.os.Handler.delayed.run();check(events.get(events.size()-1).equals("exit"));events.clear();r.onReceive(c,alarm.launch.intent);check(events.equals(Arrays.asList("hardware-start","sip-start","home")));System.out.println("PASS: alarm before shutdown, no exit on alarm failure, duplicate suppression, delayed process exit, private action validation and module-aware relaunch.");}}'''
}
with tempfile.TemporaryDirectory(dir=root/'test-classes',prefix='recovery-') as folder:
    temp=Path(folder);files=[]
    for name,body in sources.items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(p)
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';out=temp/'classes';out.mkdir()
    subprocess.run([java,'-jar',str(root.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.8','-nowarn','-d',str(out),str(root/'src/local/wallpanel/app/AppRestartReceiver.java'),*[str(p) for p in files]],check=True)
    subprocess.run([java,'-cp',str(out),'local.wallpanel.app.RecoveryHarness'],check=True,timeout=10)
