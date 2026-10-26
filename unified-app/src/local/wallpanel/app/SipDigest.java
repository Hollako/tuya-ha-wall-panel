package local.wallpanel.app;
import java.security.MessageDigest;
/** Digest material remains in memory; never log this result or HA1. */
public final class SipDigest {
    private static String hash(String text,String algorithm)throws Exception{byte[] bytes=MessageDigest.getInstance(algorithm).digest(text.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
    public static String response(String user,String realm,String password,String method,String uri,String nonce,String algorithm,String qop,String nc,String cnonce)throws Exception{
        if(algorithm==null||algorithm.length()==0)algorithm="MD5";boolean session=algorithm.toLowerCase(java.util.Locale.US).endsWith("-sess");String base=session?algorithm.substring(0,algorithm.length()-5):algorithm;
        if(!base.equalsIgnoreCase("MD5")&&!base.equalsIgnoreCase("SHA-256"))throw new IllegalArgumentException("Unsupported digest algorithm");
        if(qop!=null&&qop.length()>0&&!qop.equals("auth"))throw new IllegalArgumentException("Unsupported digest quality");
        String ha1=hash(user+":"+realm+":"+password,base);if(session)ha1=hash(ha1+":"+nonce+":"+cnonce,base);
        String ha2=hash(method+":"+uri,base);return hash(ha1+":"+nonce+":"+(qop==null||qop.length()==0?"":nc+":"+cnonce+":"+qop+":")+ha2,base);
    }
    private SipDigest(){}
}
