package local.wallpanel.app;
import java.net.URI;
/** Editable camera address with its login retained separately in the encrypted SIP store. */
public final class CameraAddress {
 private static URI parse(String value)throws Exception{URI uri=new URI(value);if(!"rtsp".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getFragment()!=null)throw new IllegalArgumentException("Enter a valid RTSP URL");return uri;}
 public static String user(String value)throws Exception{if(value.isEmpty())return "";String info=parse(value).getUserInfo();if(info==null)return "";int colon=info.indexOf(':');return colon<0?info:info.substring(0,colon);}
 public static String password(String value)throws Exception{if(value.isEmpty())return "";String info=parse(value).getUserInfo();if(info==null)return "";int colon=info.indexOf(':');return colon<0?"":info.substring(colon+1);}
 public static String visible(String value)throws Exception{if(value.isEmpty())return "";URI uri=parse(value);String authority=uri.getRawAuthority();return uri.getScheme()+"://"+authority.substring(authority.lastIndexOf('@')+1)+(uri.getRawPath()==null?"":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());}
 public static String withLogin(String value,String user,String password)throws Exception{
  if(value.isEmpty())return "";if(user.indexOf(':')>=0||user.isEmpty()&&!password.isEmpty())throw new IllegalArgumentException("Enter a camera username without a colon");String address=visible(value);if(user.isEmpty())return address;
  String encoded=new URI("rtsp",user+":"+password,"camera",-1,null,null,null).toASCIIString();String info=encoded.substring(encoded.indexOf("://")+3,encoded.lastIndexOf('@'));int start=address.indexOf("://")+3;return address.substring(0,start)+info+"@"+address.substring(start);
 }
 private CameraAddress(){}
}
