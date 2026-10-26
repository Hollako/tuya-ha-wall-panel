package local.wallpanel.app;
import android.net.Network;
import android.gov.nist.core.net.AddressResolver;
import android.gov.nist.javax.sip.stack.HopImpl;
import android.javax.sip.address.Hop;
/** Hostnames are resolved through the same network as the SIP transport. */
public final class SipNetworkResolver implements AddressResolver {
    private final Network network;
    public SipNetworkResolver()throws java.io.IOException{network=NetworkRouter.current().require(true);}
    public Hop resolveAddress(Hop input){try{return new HopImpl(NetworkRouter.current().resolve(network,input.getHost()).getHostAddress(),input.getPort()>0?input.getPort():5060,input.getTransport());}catch(java.io.IOException failed){throw new IllegalArgumentException("SIP host is unavailable on the selected network");}}
}
