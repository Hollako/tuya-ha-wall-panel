package local.wallpanel.app;
import java.net.*;import java.io.*;import javax.net.SocketFactory;
/** Operator-invoked DESCRIBE only; credentials arrive on stdin and are never logged. */
public class ReadOnlyRtspProbe {
 public static void main(String[] args)throws Exception {
  SocketFactory sockets=new SocketFactory(){public Socket createSocket(){return new Socket(){public void connect(SocketAddress peer,int timeout)throws IOException{InetSocketAddress a=(InetSocketAddress)peer;super.connect(new InetSocketAddress(InetAddress.getByName(a.getHostString()),a.getPort()),timeout);}};}public Socket createSocket(String h,int p){throw new UnsupportedOperationException();}public Socket createSocket(InetAddress h,int p){throw new UnsupportedOperationException();}public Socket createSocket(String h,int p,InetAddress l,int lp){throw new UnsupportedOperationException();}public Socket createSocket(InetAddress h,int p,InetAddress l,int lp){throw new UnsupportedOperationException();}};
  try {String url=new BufferedReader(new InputStreamReader(System.in,"UTF-8")).readLine();new RtspVideoSession(url,sockets,new RtspVideoSession.Listener(){public void phase(String s){}public void parameters(byte[] a,byte[] b){throw new AssertionError("No media permitted");}public void frame(byte[] a,long p){throw new AssertionError("No media permitted");}}).describeOnly();System.out.println("PASS: production RTSP login and H264 description accepted by camera. DESCRIBE only; no SETUP or PLAY.");}
  catch(Exception error){System.out.println("FAIL: "+error.getClass().getSimpleName()+" at "+error.getStackTrace()[0].getClassName()+":"+error.getStackTrace()[0].getLineNumber());System.exit(1);}
 }
}
