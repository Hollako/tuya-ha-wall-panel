package local.wallpanel.app;
import java.io.*;
import java.util.Arrays;

/** Bounded RFC6184 access units, including in-band SPS/PPS absent from SDP. */
final class H264RtpAssembler {
    interface Output {void parameters(byte[] sps,byte[] pps)throws Exception;void frame(byte[] annexB,long timeUs)throws Exception;}
    private static final int LIMIT=2*1024*1024;
    private final int payloadType;private final Output output;private final ByteArrayOutputStream frame=new ByteArrayOutputStream(),fragment=new ByteArrayOutputStream();
    private byte[] sps,pps;private boolean haveKey,key,vcl,damaged,fragmenting;private final SipRtpPacket.Sequence sequence=new SipRtpPacket.Sequence();private int previous=-1;private long stamp=-1,origin=-1;
    H264RtpAssembler(int payload,Output sink){payloadType=payload;output=sink;}
    void packet(byte[] data,int length)throws Exception{
        SipRtpPacket p=SipRtpPacket.parse(data,length,payloadType);if(p==null||p.length==0||!sequence.accept(p))return;
        boolean gap=previous>=0&&p.sequence!=((previous+1)&65535);previous=p.sequence;
        if(stamp!=p.timestamp){frame.reset();key=false;vcl=false;damaged=false;fragmenting=false;fragment.reset();stamp=p.timestamp;}
        if(gap){damaged=true;haveKey=false;fragmenting=false;fragment.reset();}
        int at=p.offset,end=at+p.length,type=data[at]&31;
        if(type>0&&type<24)nal(Arrays.copyOfRange(data,at,end));
        else if(type==24){at++;while(at+2<=end){int n=((data[at]&255)<<8)|(data[at+1]&255);at+=2;if(n<=0||n>end-at){damaged=true;break;}nal(Arrays.copyOfRange(data,at,at+n));at+=n;}if(at!=end)damaged=true;}
        else if(type==28&&p.length>=2){int header=data[at+1]&255;if((header&32)!=0||(header&31)==0||(header&31)>=24||(header&192)==192){damaged=true;return;}if((header&128)!=0){fragment.reset();fragment.write((data[at]&224)|(header&31));fragmenting=true;}if(fragmenting){if(fragment.size()+p.length-2>LIMIT){fragmenting=false;damaged=true;}else{fragment.write(data,at+2,p.length-2);if((header&64)!=0){nal(fragment.toByteArray());fragment.reset();fragmenting=false;}}}}
        else damaged=true;
        if((data[1]&128)!=0){if(!damaged&&!fragmenting&&vcl&&frame.size()>0&&sps!=null&&pps!=null&&(haveKey||key)){if(key)haveKey=true;if(origin<0)origin=stamp;output.frame(frame.toByteArray(),((stamp-origin)&0xffffffffL)*1000000L/90000L);}if(damaged)haveKey=false;frame.reset();key=false;vcl=false;damaged=false;fragmenting=false;fragment.reset();}
    }
    private void nal(byte[] bytes)throws Exception{if(bytes.length==0)return;int type=bytes[0]&31;if((bytes[0]&128)!=0||type==0||type>=24){damaged=true;return;}if(type==7||type==8)if(bytes.length>65536)throw new IOException("Camera parameters too large");if(type>=1&&type<=5)vcl=true;
        if(type==7){if(!Arrays.equals(sps,bytes)){sps=bytes;haveKey=false;if(pps!=null)output.parameters(sps,pps);}}
        else if(type==8){if(!Arrays.equals(pps,bytes)){pps=bytes;haveKey=false;if(sps!=null)output.parameters(sps,pps);}}
        if(type==5)key=true;
        if(frame.size()+bytes.length+4>LIMIT){frame.reset();damaged=true;return;}
        frame.write(new byte[]{0,0,0,1});frame.write(bytes);
    }
}
