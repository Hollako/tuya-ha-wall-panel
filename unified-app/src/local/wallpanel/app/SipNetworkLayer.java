package local.wallpanel.app;
import android.net.Network;
import java.net.*;
import java.io.IOException;
import javax.net.ssl.*;
import android.gov.nist.core.net.NetworkLayer;
import android.gov.nist.javax.sip.SipStackImpl;
/** Every socket in one SIP stack is pinned to its selected Android Network. */
public final class SipNetworkLayer implements NetworkLayer {
    private final Network network;
    public SipNetworkLayer()throws IOException{network=NetworkRouter.current().require(true);}
    public DatagramSocket createDatagramSocket()throws SocketException{return createDatagramSocket(0,null);}
    public DatagramSocket createDatagramSocket(int port,InetAddress local)throws SocketException{
        DatagramSocket s=new DatagramSocket(null);try{network.bindSocket(s);if(local!=null&&!local.isAnyLocalAddress()&&!NetworkRouter.current().owns(network,local.getHostAddress()))throw new IOException("SIP address is not on the selected network");s.bind(new InetSocketAddress(local,port));return s;}catch(IOException failed){s.close();SocketException e=new SocketException("Selected SIP network unavailable");e.initCause(failed);throw e;}
    }
    public void setSipStack(SipStackImpl stack){}
    public ServerSocket createServerSocket(int p,int backlog,InetAddress bind)throws IOException{throw new IOException("SIP TCP is not enabled");}
    public SSLServerSocket createSSLServerSocket(int p,int backlog,InetAddress bind)throws IOException{throw new IOException("SIP TLS is not enabled");}
    public Socket createSocket(InetAddress a,int p)throws IOException{throw new IOException("SIP TCP is not enabled");}
    public Socket createSocket(InetAddress a,int p,InetAddress l)throws IOException{return createSocket(a,p);}
    public Socket createSocket(InetAddress a,int p,InetAddress l,int lp)throws IOException{return createSocket(a,p);}
    public SSLSocket createSSLSocket(InetAddress a,int p)throws IOException{throw new IOException("SIP TLS is not enabled");}
    public SSLSocket createSSLSocket(InetAddress a,int p,InetAddress l)throws IOException{return createSSLSocket(a,p);}
}
