package local.wallpanel.app;
/** ITU G.711 PCMU, signed 16-bit PCM. The wire byte is one's-complement encoded. */
public final class SipG711 {
    public static short decode(byte value){int u=(~value)&255;int t=(((u&15)<<3)+132)<<((u>>>4)&7);return (short)((u&128)!=0?132-t:t-132);}
    public static byte encode(short value){int pcm=value,sign=0;if(pcm<0){sign=128;pcm=-pcm;}pcm=Math.min(32635,pcm)+132;int exp=7;for(int mask=0x4000;exp>0&&(pcm&mask)==0;exp--,mask>>=1){}return (byte)~(sign|(exp<<4)|((pcm>>>(exp+3))&15));}
    public static void decode(byte[] in,int offset,short[] out,int count){if(offset<0||count<0||offset+count>in.length||count>out.length)throw new IllegalArgumentException();for(int i=0;i<count;i++)out[i]=decode(in[offset+i]);}
    private SipG711(){}
}
