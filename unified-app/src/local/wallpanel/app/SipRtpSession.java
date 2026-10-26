package local.wallpanel.app;
import android.media.*;
import android.media.audiofx.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** PCMU RTP with validated headers, bounded packets, partial I/O and exact peer filtering. */
final class SipRtpSession implements SipEngine.Media {
    interface NetworkBinding {void bind(DatagramSocket socket)throws java.io.IOException;InetAddress resolve(String host)throws java.io.IOException;}
    private NetworkBinding networkBinding;
    void networkBinding(NetworkBinding binding){networkBinding=binding;}
    interface Failure {void failed();}
    private final String local;private final SipConfig config;private final Failure failure;
    private volatile InetSocketAddress peer;private volatile int payloadType;private volatile boolean running,microphoneMuted;private boolean closed;
    private DatagramSocket socket;private AudioRecord recorder;private AudioTrack player;private AudioEffect aec,ns,agc;private Thread tx,rx;private final AtomicBoolean notified=new AtomicBoolean();
    private int sequence=new java.security.SecureRandom().nextInt(65536),ssrc=new java.security.SecureRandom().nextInt();private long timestamp=new java.security.SecureRandom().nextInt()&0xffffffffL;
    SipRtpSession(String address,SipConfig c,Failure f){local=address;config=c;failure=f;}
    public synchronized void start(SipSdp offer)throws Exception{
        if(closed)throw new IllegalStateException("Media session was cancelled");
        try{
            update(offer);socket=new DatagramSocket(null);if(networkBinding!=null)networkBinding.bind(socket);socket.bind(new InetSocketAddress(InetAddress.getByName(local),config.rtpPort));socket.setSoTimeout(200);
            int rb=AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),pb=AudioTrack.getMinBufferSize(8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(rb<=0||pb<=0)throw new IllegalStateException("Unsupported call audio");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(4096,rb));
            player=new AudioTrack(AudioManager.STREAM_VOICE_CALL,8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(4096,pb),AudioTrack.MODE_STREAM);
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED||player.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Call audio unavailable");int session=recorder.getAudioSessionId();
            try{if(config.aec&&AcousticEchoCanceler.isAvailable()){aec=AcousticEchoCanceler.create(session);if(aec!=null)aec.setEnabled(true);}}catch(Exception ignored){}
            try{if(config.noiseSuppressor&&NoiseSuppressor.isAvailable()){ns=NoiseSuppressor.create(session);if(ns!=null)ns.setEnabled(true);}}catch(Exception ignored){}
            try{if(config.gainControl&&AutomaticGainControl.isAvailable()){agc=AutomaticGainControl.create(session);if(agc!=null)agc.setEnabled(true);}}catch(Exception ignored){}
            running=true;recorder.startRecording();player.play();tx=new Thread(new Runnable(){public void run(){capture();}},"wallpanel-rtp-capture");rx=new Thread(new Runnable(){public void run(){playback();}},"wallpanel-rtp-playback");tx.start();rx.start();
        }catch(Exception e){close();throw e;}
    }
    public void update(SipSdp offer)throws Exception{peer=new InetSocketAddress(networkBinding==null?InetAddress.getByName(offer.remoteAddress):networkBinding.resolve(offer.remoteAddress),offer.remotePort);payloadType=offer.payloadType;}
    private void failed(){if(running&&notified.compareAndSet(false,true))failure.failed();}
    void microphoneMuted(boolean value){microphoneMuted=value;}
    private void capture(){short[] samples=new short[160];byte[] packet=new byte[172];int have=0;try{while(running){int n=recorder.read(samples,have,160-have);if(n<=0)throw new IllegalStateException();have+=n;if(have!=160)continue;boolean muted=microphoneMuted;for(int i=0;i<160;i++)packet[12+i]=muted?(byte)255:SipG711.encode(samples[i]);SipRtpPacket.header(packet,payloadType,sequence++,timestamp,ssrc);timestamp+=160;InetSocketAddress target=peer;socket.send(new DatagramPacket(packet,packet.length,target));have=0;}}catch(Exception e){failed();}}
    private void playback(){byte[] bytes=new byte[4096];short[] samples=new short[4096];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);SipRtpPacket.Sequence order=new SipRtpPacket.Sequence();try{while(running){try{packet.setLength(bytes.length);socket.receive(packet);}catch(SocketTimeoutException waiting){continue;}InetSocketAddress expected=peer;if(!packet.getAddress().equals(expected.getAddress())||packet.getPort()!=expected.getPort())continue;SipRtpPacket frame=SipRtpPacket.parse(bytes,packet.getLength(),payloadType);if(frame==null||!order.accept(frame))continue;SipG711.decode(bytes,frame.offset,samples,frame.length);int at=0;while(at<frame.length&&running){int n=player.write(samples,at,frame.length-at);if(n<=0)throw new IllegalStateException();at+=n;}}}catch(Exception e){failed();}}
    public synchronized void close(){closed=true;running=false;if(socket!=null)socket.close();try{if(recorder!=null)recorder.stop();}catch(Exception ignored){}try{if(player!=null)player.stop();}catch(Exception ignored){}
        for(Thread t:new Thread[]{tx,rx})if(t!=null&&t!=Thread.currentThread())try{t.join(1200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        for(AudioEffect effect:new AudioEffect[]{aec,ns,agc})if(effect!=null)try{effect.release();}catch(Exception ignored){}aec=ns=agc=null;if(recorder!=null){recorder.release();recorder=null;}if(player!=null){player.release();player=null;}socket=null;
    }
}
