"""Exercise the real root SystemUiLease with fake Binder services and EOF stdin."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parent
sources={
'android/os/IBinder.java':'package android.os;public interface IBinder {}',
'android/os/Binder.java':'package android.os;public class Binder implements IBinder {}',
'android/os/Process.java':'package android.os;public class Process {public static int uid=0;public static int myUid(){return uid;}}',
'android/os/ServiceManager.java':'package android.os;public class ServiceManager {public static IBinder getService(String name){if(!name.equals("statusbar"))throw new AssertionError();return new Binder();}}',
'com/android/internal/statusbar/IStatusBarService.java':'''package com.android.internal.statusbar;
import android.os.IBinder;import java.util.*;
public interface IStatusBarService {
void disable(int f,IBinder token,String owner);void disable2(int f,IBinder token,String owner);void collapsePanels();
public static class Stub {public static IStatusBarService asInterface(IBinder b){return Fake.INSTANCE;}}
public static class Fake implements IStatusBarService {
public static final Fake INSTANCE=new Fake();public static boolean fail;public static List<Integer> primary=new ArrayList<Integer>(),secondary=new ArrayList<Integer>();public static IBinder token;public static int collapses;
private void check(IBinder t,String owner){if(!owner.equals("local.wallpanel.app.dashboard"))throw new AssertionError();if(token==null)token=t;if(token!=t)throw new AssertionError("Token changed");}
public void disable(int f,IBinder t,String o){check(t,o);primary.add(f);}
public void disable2(int f,IBinder t,String o){check(t,o);secondary.add(f);if(f!=0&&fail)throw new IllegalStateException("Synthetic disable2 failure");}
public void collapsePanels(){collapses++;}
public static void reset(){primary.clear();secondary.clear();token=null;collapses=0;}
}}
''',
'Harness.java':'''import com.android.internal.statusbar.IStatusBarService.Fake;
public class Harness {static void expect(boolean x){if(!x)throw new AssertionError();}public static void main(String[] args)throws Exception {
local.wallpanel.system.SystemUiLease.main(new String[0]);expect(Fake.primary.size()==2&&Fake.primary.get(0)==0x03ff0000&&Fake.primary.get(1)==0);expect(Fake.secondary.size()==2&&Fake.secondary.get(0)==7&&Fake.secondary.get(1)==0);expect(Fake.collapses==1);
Fake.reset();Fake.fail=true;local.wallpanel.system.SystemUiLease.main(new String[0]);expect(Fake.primary.size()==2&&Fake.primary.get(1)==0);expect(Fake.secondary.size()==2&&Fake.secondary.get(1)==0);expect(Fake.collapses==0);
Fake.reset();android.os.Process.uid=2000;local.wallpanel.system.SystemUiLease.main(new String[0]);expect(Fake.primary.isEmpty()&&Fake.secondary.isEmpty());System.out.println("PASS: exact dashboard flags, one binder token, EOF restores controls, partial failure clears flags, non-root refusal.");}}
'''}
sources.update({
'android/content/Context.java':'''package android.content;public class Context {public static final int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public java.io.FileOutputStream openFileOutput(String name,int mode)throws java.io.IOException{return new java.io.FileOutputStream(new java.io.File(System.getProperty("guard.test.folder"),name));}}''',
'android/net/LocalSocket.java':r'''package android.net;public class LocalSocket {public static volatile LocalSocket last;public static volatile int opens;public volatile boolean closed;public LocalSocket(){last=this;opens++;}public void setSoTimeout(int n){}public java.io.InputStream getInputStream(){return new java.io.InputStream(){private byte[] header="{\"ready\":true}\n".getBytes();private int at;public int read(){if(at<header.length)return header[at++];while(!closed)try{Thread.sleep(5);}catch(Exception e){}return -1;}};}public void shutdownInput()throws java.io.IOException{closed=true;}public void shutdownOutput()throws java.io.IOException{closed=true;}public void close()throws java.io.IOException{closed=true;}}''',
'org/json/JSONObject.java':r'''package org.json;public class JSONObject {private String text;public JSONObject(String s){text=s;}public boolean optBoolean(String name,boolean fallback){return text.contains("\"ready\":true");}}''',
'local/wallpanel/app/PrivilegeChannel.java':r'''package local.wallpanel.app;class PrivilegeChannel {static android.net.LocalSocket open(String request){if(!request.equals("UI_GUARD\n"))throw new AssertionError();return new android.net.LocalSocket();}}''',
'local/wallpanel/app/GuardHarness.java':'''package local.wallpanel.app;public class GuardHarness {static void expect(boolean b){if(!b)throw new AssertionError();}public static void main(String[] args)throws Exception{System.setProperty("guard.test.folder",args[0]);SystemUiGuard guard=new SystemUiGuard(new android.content.Context());guard.start();java.io.File status=new java.io.File(args[0],"ui-guard-status.json");long until=System.currentTimeMillis()+3000;while(!status.exists()&&System.currentTimeMillis()<until)Thread.sleep(10);expect(status.exists());expect(android.net.LocalSocket.opens==1&&!android.net.LocalSocket.last.closed);SystemUiGuard.holdForAdministrator(guard);expect(!android.net.LocalSocket.last.closed);expect(SystemUiGuard.takeAdministratorLease()==guard);expect(!android.net.LocalSocket.last.closed);SystemUiGuard.holdForAdministrator(guard);SystemUiGuard.administratorAuthenticated();expect(android.net.LocalSocket.last.closed);expect(SystemUiGuard.takeAdministratorLease()==null);System.out.println("PASS: PIN handoff retains the live restriction lease, dashboard return reuses it, authenticated setup releases it.");}}
'''})
with tempfile.TemporaryDirectory(dir=r/'test-classes',prefix='ui-') as folder:
    temp=Path(folder);files=[]
    for name,body in sources.items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(p)
    java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';out=temp/'classes';out.mkdir()
    subprocess.run([java,'-jar',str(r.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-d',str(out),str(r/'system-ui/src/local/wallpanel/system/SystemUiLease.java'),str(r/'src/local/wallpanel/app/SystemUiGuard.java'),*[str(p) for p in files]],check=True)
    subprocess.run([java,'-cp',str(out),'Harness'],input='',text=True,check=True,timeout=10)
    subprocess.run([java,'-cp',str(out),'local.wallpanel.app.GuardHarness',str(temp)],input='',text=True,check=True,timeout=10)
