package local.wallpanel.app;
import java.util.*;
/** Audio-only offer/answer. Unsupported media are explicitly rejected with port zero. */
public final class SipSdp {
    private static final class Track {String kind,protocol,address="";int port;String[] formats;Map<Integer,String> codecs=new HashMap<Integer,String>();}
    private final List<Track> tracks=new ArrayList<Track>();private Track selected;
    public String remoteAddress;public int remotePort,payloadType;
    public static SipSdp parse(String text){
        if(text==null||text.length()>16384)throw new IllegalArgumentException("Missing or oversized SDP");SipSdp out=new SipSdp();Track current=null;String sessionAddress="";
        for(String raw:text.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("m=")){String[] p=line.substring(2).trim().split(" +");if(p.length<4||out.tracks.size()>=8)throw new IllegalArgumentException("Invalid media offer");current=new Track();current.kind=p[0];current.port=Integer.parseInt(p[1].split("/")[0]);current.protocol=p[2];current.formats=Arrays.copyOfRange(p,3,p.length);out.tracks.add(current);}
            else if(line.startsWith("c=IN IP4 ")){String address=line.substring(9).split("/")[0].trim();if(current==null)sessionAddress=address;else current.address=address;}
            else if(current!=null&&line.startsWith("a=rtpmap:")){String[] p=line.substring(9).split(" +",2);if(p.length==2)current.codecs.put(Integer.valueOf(p[0]),p[1].toUpperCase(Locale.US));}}
        for(Track t:out.tracks){if(!t.kind.equals("audio")||!t.protocol.equals("RTP/AVP")||t.port<=0||t.port>65535)continue;for(String f:t.formats){int pt=Integer.parseInt(f);String codec=t.codecs.get(pt);if((pt==0&&(codec==null||codec.equals("PCMU/8000")||codec.equals("PCMU/8000/1")))||(pt>=96&&pt<=127&&(codec!=null&&(codec.equals("PCMU/8000")||codec.equals("PCMU/8000/1"))))){out.selected=t;out.payloadType=pt;break;}}if(out.selected!=null)break;}
        if(out.selected==null)throw new IllegalArgumentException("The call must offer PCMU audio");Track t=out.selected;out.remoteAddress=t.address.length()==0?sessionAddress:t.address;out.remotePort=t.port;
        if(!SipConfig.host(out.remoteAddress)||out.remoteAddress.equals("0.0.0.0"))throw new IllegalArgumentException("Invalid media address");return out;
    }
    public String answer(String local,int port){if(!SipConfig.ipv4(local))throw new IllegalArgumentException();StringBuilder s=new StringBuilder("v=0\r\no=WallPanel "+System.currentTimeMillis()+" 1 IN IP4 "+local+"\r\ns=Tuya HA Wall Panel\r\nc=IN IP4 "+local+"\r\nt=0 0\r\n");for(Track t:tracks){if(t==selected)s.append("m=audio ").append(port).append(" RTP/AVP ").append(payloadType).append("\r\na=rtpmap:").append(payloadType).append(" PCMU/8000\r\na=ptime:20\r\na=sendrecv\r\n");else{s.append("m=").append(t.kind).append(" 0 ").append(t.protocol);for(String f:t.formats)s.append(' ').append(f);s.append("\r\n");}}return s.toString();}
}
