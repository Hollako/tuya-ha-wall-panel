package local.wallpanel.app;
/** Bounds-checked RTP v2 parser. DTMF/other codecs are never played as PCMU. */
public final class SipRtpPacket {
    public final int offset,length,sequence;public final long timestamp,ssrc;
    private SipRtpPacket(int o,int l,int s,long t,long source){offset=o;length=l;sequence=s;timestamp=t;ssrc=source;}
    private static long word(byte[] d,int p){return ((long)(d[p]&255)<<24)|((long)(d[p+1]&255)<<16)|((long)(d[p+2]&255)<<8)|(d[p+3]&255);}
    public static SipRtpPacket parse(byte[] d,int size,int payloadType){
        if(size<12||size>d.length||(d[0]&0xc0)!=0x80||(d[1]&127)!=payloadType)return null;
        int at=12+4*(d[0]&15);if(at>size)return null;
        if((d[0]&16)!=0){if(at+4>size)return null;int words=((d[at+2]&255)<<8)|(d[at+3]&255);at+=4+4*words;if(at>size)return null;}
        int end=size;if((d[0]&32)!=0){int pad=d[size-1]&255;if(pad==0||pad>end-at)return null;end-=pad;}
        if(end<=at)return null;return new SipRtpPacket(at,end-at,((d[2]&255)<<8)|(d[3]&255),word(d,4),word(d,8));
    }
    public static void header(byte[] d,int payloadType,int sequence,long timestamp,int ssrc){d[0]=(byte)128;d[1]=(byte)payloadType;d[2]=(byte)(sequence>>>8);d[3]=(byte)sequence;for(int i=0;i<4;i++){d[4+i]=(byte)(timestamp>>>(24-8*i));d[8+i]=(byte)(ssrc>>>(24-8*i));}}
    public static final class Sequence {
        private boolean seen;private int last;private long source;
        public synchronized boolean accept(SipRtpPacket p){if(!seen||p.ssrc!=source){seen=true;source=p.ssrc;last=p.sequence;return true;}if((short)(p.sequence-last)<=0)return false;last=p.sequence;return true;}
    }
    private SipRtpPacket(){offset=length=sequence=0;timestamp=ssrc=0;}
}
