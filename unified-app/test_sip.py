"""SIP/codec/packet tests use loopback UDP and fake media, never the panel or HA."""
from pathlib import Path
from engine_sources import sip_source
import subprocess
r=Path(__file__).resolve().parent;out=r/'test-classes/sip';out.mkdir(parents=True,exist_ok=True)
stub=out/'Log.java';stub.write_text('''package android.util;public class Log {public static final int VERBOSE=2,DEBUG=3,INFO=4,WARN=5,ERROR=6,ASSERT=7;public static boolean isLoggable(String t,int l){return false;}public static int println(int p,String t,String m){return 0;}public static String getStackTraceString(Throwable t){return "";}public static int v(String t,String m){return 0;}public static int d(String t,String m){return 0;}public static int i(String t,String m){return 0;}public static int w(String t,String m){return 0;}public static int e(String t,String m){return 0;}public static int e(String t,String m,Throwable e){return 0;}}''')
java=r'C:\Program Files (x86)\Java\jre1.8.0_431\bin\java.exe';jars=list((r/'vendor/sip').glob('*.jar'));cp=';'.join(str(p) for p in jars)
src=r/'src/local/wallpanel/app';names=['SipConfig.java','SipG711.java','SipRtpPacket.java','SipDigest.java','SipSdp.java','SipEngine.java']
subprocess.run([java,'-jar',str(r.parent/'audio/tooling/ecj.jar'),'-encoding','UTF-8','-1.7','-nowarn','-classpath',cp,'-d',str(out),*[str(sip_source(n)) for n in names],str(stub),str(r/'tests/local/wallpanel/app/SipTests.java')],check=True)
subprocess.run([java,'-cp',str(out)+';'+cp,'local.wallpanel.app.SipTests'],check=True,timeout=45)
