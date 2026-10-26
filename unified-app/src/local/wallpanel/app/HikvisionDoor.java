package local.wallpanel.app;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Only the configured Hikvision door's momentary-open command. No redirects/replay. */
final class HikvisionDoor {
    private static final class Reply {int code;String challenge,body;}
    static void open(SipConfig config,int door,IntercomDoor.Guard guard,IntercomDoor.Connections connections)throws Exception{
        config.validate();if(!config.doorEnabled(door))throw new IOException("Door is not enabled");
        String base=config.doorServer.endsWith("/")?config.doorServer.substring(0,config.doorServer.length()-1):config.doorServer;
        String path="/ISAPI/AccessControl/RemoteControl/door/"+door;URL url=new URL(base+path);
        Reply r=send(url,null,guard,connections);
        if(r.code==401){String header=authorization(config.doorUser,config.doorPassword,path,r.challenge);r=send(url,header,guard,connections);}
        if(r.code!=200||!Pattern.compile("<(?:[A-Za-z0-9_]+:)?statusCode(?:\\s[^>]*)?>\\s*1\\s*</",Pattern.CASE_INSENSITIVE).matcher(r.body).find())throw new IOException("Door request was not confirmed (HTTP "+r.code+")");
    }
    private static Reply send(URL url,String auth,IntercomDoor.Guard guard,IntercomDoor.Connections connections)throws Exception{
        if(!guard.allowed())throw new IOException("Call has ended");
        HttpURLConnection c=connections.open(url);c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(5000);c.setRequestMethod("PUT");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/xml; charset=UTF-8");if(auth!=null)c.setRequestProperty("Authorization",auth);
        byte[] body=("<?xml version=\"1.0\" encoding=\"UTF-8\"?><RemoteControlDoor version=\"2.0\" xmlns=\"http://www.isapi.org/ver20/XMLSchema\"><cmd>open</cmd></RemoteControlDoor>").getBytes("UTF-8");c.setFixedLengthStreamingMode(body.length);
        try{if(!guard.allowed())throw new IOException("Call has ended");OutputStream out=c.getOutputStream();try{out.write(body);}finally{out.close();}Reply r=new Reply();r.code=c.getResponseCode();r.challenge=c.getHeaderField("WWW-Authenticate");InputStream in=r.code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null)try{byte[] b=new byte[1024];int n;while((n=in.read(b))>0){if(bytes.size()+n>8192)throw new IOException("Door response is too large");bytes.write(b,0,n);}}finally{in.close();}r.body=new String(bytes.toByteArray(),"UTF-8");return r;}finally{c.disconnect();}
    }
    static String authorization(String user,String password,String path,String challenge)throws Exception{
        if(challenge==null||!challenge.regionMatches(true,0,"Digest ",0,7))throw new IOException("Door requires unsupported authentication");
        Map<String,String> fields=new HashMap<String,String>();Matcher m=Pattern.compile("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))").matcher(challenge.substring(7));while(m.find())fields.put(m.group(1).toLowerCase(Locale.US),m.group(2)!=null?m.group(2):m.group(3));
        String realm=fields.get("realm"),nonce=fields.get("nonce"),algorithm=fields.containsKey("algorithm")?fields.get("algorithm"):"MD5",qop="";
        if(realm==null||nonce==null)throw new IOException("Invalid door authentication");if(fields.containsKey("qop")){for(String item:fields.get("qop").split(","))if(item.trim().equals("auth"))qop="auth";if(qop.length()==0)throw new IOException("Unsupported door authentication quality");}
        String cnonce=UUID.randomUUID().toString().replace("-","");String result="Digest username="+quote(user)+", realm="+quote(realm)+", nonce="+quote(nonce)+", uri="+quote(path)+", response="+quote(SipDigest.response(user,realm,password,"PUT",path,nonce,algorithm,qop,"00000001",cnonce))+", algorithm="+quote(algorithm);
        if(qop.length()>0)result+=", qop=auth, nc=00000001, cnonce="+quote(cnonce);else if(algorithm.toLowerCase(Locale.US).endsWith("-sess"))result+=", cnonce="+quote(cnonce);if(fields.containsKey("opaque"))result+=", opaque="+quote(fields.get("opaque"));return result;
    }
    private static String quote(String s)throws IOException{if(s.indexOf('\r')>=0||s.indexOf('\n')>=0)throw new IOException("Invalid authentication header");return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private HikvisionDoor(){}
}
