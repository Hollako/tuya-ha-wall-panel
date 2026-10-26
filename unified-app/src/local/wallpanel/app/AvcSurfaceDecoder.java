package local.wallpanel.app;
import android.media.*;
import android.view.Surface;
import java.nio.ByteBuffer;
import androidx.media3.container.NalUnitUtil;

/** Uses the same platform H264 decoder as Android playback, configured from real SPS/PPS. */
final class AvcSurfaceDecoder {
    interface Events {void size(int width,int height);void rendered();}
    private final Surface surface;private final Events events;private MediaCodec codec;private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();private boolean shown;
    AvcSurfaceDecoder(Surface target,Events listener){surface=target;events=listener;}
    void parameters(byte[] sps,byte[] pps)throws Exception{
        close();NalUnitUtil.SpsData format=NalUnitUtil.parseSpsNalUnit(sps,0,sps.length);if(format.width<=0||format.height<=0||format.width>4096||format.height>4096)throw new IllegalArgumentException("Unsupported camera dimensions");
        MediaFormat f=MediaFormat.createVideoFormat("video/avc",format.width,format.height);f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,2*1024*1024);f.setByteBuffer("csd-0",ByteBuffer.wrap(annexB(sps)));f.setByteBuffer("csd-1",ByteBuffer.wrap(annexB(pps)));
        codec=MediaCodec.createDecoderByType("video/avc");codec.configure(f,surface,null,0);codec.start();codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);events.size(format.width,format.height);
    }
    void frame(byte[] bytes,long pts)throws Exception{if(codec==null)return;drain();int index=codec.dequeueInputBuffer(10000);if(index<0){drain();index=codec.dequeueInputBuffer(10000);}if(index<0)return;ByteBuffer buffer=codec.getInputBuffer(index);if(buffer==null||buffer.capacity()<bytes.length)throw new IllegalArgumentException("Camera frame too large");buffer.clear();buffer.put(bytes);codec.queueInputBuffer(index,0,bytes.length,pts,0);drain();}
    private void drain(){for(int count=0;count<32;count++){int index=codec.dequeueOutputBuffer(info,0);if(index>=0){codec.releaseOutputBuffer(index,true);if(!shown){shown=true;events.rendered();}}else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();events.size(f.getInteger(MediaFormat.KEY_WIDTH),f.getInteger(MediaFormat.KEY_HEIGHT));}else break;}}
    private static byte[] annexB(byte[] nal){byte[] b=new byte[nal.length+4];b[3]=1;System.arraycopy(nal,0,b,4,nal.length);return b;}
    void close(){MediaCodec old=codec;codec=null;shown=false;if(old!=null){try{old.stop();}catch(Exception ignored){}try{old.release();}catch(Exception ignored){}}}
}
