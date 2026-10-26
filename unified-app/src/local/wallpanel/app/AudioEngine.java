package local.wallpanel.app;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;

/** Outbound-only local IPC. No broker login, TCP server, recording or auto-play. */
final class AudioEngine extends android.content.ContextWrapper {
    interface Listener {void state(JSONObject data);boolean hardware(String command);}
    private final Listener listener;
    AudioEngine(android.content.Context context,Listener listener){super(context);this.listener=listener;}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private MediaPlayer player;
    private MediaSession session;
    private volatile boolean running;
    private String source="Android playback", state="idle", title="", error="";
    private boolean power=true, prepared=false;
    private int lastVolume=0,commandSeq=0;
    private VolumePolicy volumePolicy;
    private java.lang.reflect.Method unmutedIndex;
    private String requestId="";
    private boolean intercomHeld,intercomResume;private String intercomState;private volatile int focusGeneration;
    private final String audioSession=java.util.UUID.randomUUID().toString();
    private PowerManager.WakeLock wake;
    private final AudioManager.OnAudioFocusChangeListener focus=new AudioManager.OnAudioFocusChangeListener(){
        public void onAudioFocusChange(int change){final int generation=focusGeneration;if(change<0)handler.post(new Runnable(){public void run(){if(running&&generation==focusGeneration&&!intercomHeld&&player!=null)pause();}});}
    };

    void start()throws Exception {
        VendorAudio.aux();VendorAudio.external();running=true;
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        VendorAudio.init((android.app.Application)getApplicationContext());
        syncSource();
        lastVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try{unmutedIndex=AudioManager.class.getMethod("getLastAudibleStreamVolume",Integer.TYPE);}catch(Exception ignored){}
        volumePolicy=new VolumePolicy(new VolumePolicy.Backend(){
            public int maximum(){return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);}
            public int index(){
                // getStreamVolume reports zero while muted. Read the actual
                // stored index to distinguish manual mute from volume zero.
                try{if(unmutedIndex!=null)return ((Integer)unmutedIndex.invoke(audio,Integer.valueOf(AudioManager.STREAM_MUSIC))).intValue();}catch(Exception ignored){}
                return audio.isStreamMute(AudioManager.STREAM_MUSIC)?lastVolume:audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            }
            public boolean muted(){return audio.isStreamMute(AudioManager.STREAM_MUSIC);}
            public void setIndex(int value){lastVolume=value;audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);}
            public void setMuted(boolean value){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,value?AudioManager.ADJUST_MUTE:AudioManager.ADJUST_UNMUTE,0);}
        });
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:audio");
        session=new MediaSession(this,"Tuya HA Wall Panel Audio");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            public void onPlay(){play();} public void onPause(){pause();} public void onStop(){stop();}
            public void onSeekTo(long pos){if(player!=null&&prepared)player.seekTo((int)Math.min(Integer.MAX_VALUE,Math.max(0,pos)));}
        });
        handler.post(tick);

    }
    void close(){
        running=false;handler.removeCallbacksAndMessages(null);releasePlayer();
        if(wake!=null&&wake.isHeld())wake.release();if(session!=null)session.release();
    }
    private final Runnable tick=new Runnable(){public void run(){if(!running)return;if(IntercomSession.active&&!intercomHeld)beginIntercom();if(!IntercomSession.active&&intercomHeld)endIntercom();if(!intercomHeld)syncSource();report();handler.postDelayed(this,1000);}};
    boolean powered(){return power;}
    void beginIntercom(){if(intercomHeld)return;focusGeneration++;intercomHeld=true;intercomState=state;intercomResume=player!=null&&((prepared&&player.isPlaying())||state.equals("buffering"));if(intercomResume&&prepared)player.pause();if(state.equals("playing")||state.equals("buffering"))state="paused";}
    void endIntercom(){if(!intercomHeld)return;focusGeneration++;intercomHeld=false;state=intercomState;if(intercomResume&&player!=null&&prepared){focus();player.start();state="playing";}intercomResume=false;}
    private void syncSource(){
        try{
            String actual=VendorAudio.aux()?"Line Input":"Android playback";
            if(!actual.equals(source)){
                releasePlayer();source=actual;title="";error="";
                state=actual.equals("Line Input")?"playing":"idle";
                if(actual.equals("Line Input"))power=true;
            }
        }catch(Exception failed){error="Cannot read board audio source";}
    }
    private void selectSource(String chosen)throws Exception{
        if(!chosen.equals("Android playback")&&!chosen.equals("Line Input"))throw new IllegalArgumentException("Unknown source");
        VendorAudio.source(chosen.equals("Line Input"));
        releasePlayer();source=chosen;power=true;title="";error="";listener.hardware("audio power 1");
        state=chosen.equals("Line Input")?"playing":"idle";
    }
    private void releasePlayer(){
        prepared=false;
        if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
        if(audio!=null)audio.abandonAudioFocus(focus);
    }
    private void focus(){
        audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
    }
    private void play(){
        power=true;error="";
        listener.hardware("audio power 1");
        if(source.equals("Line Input")){try{selectSource("Line Input");}catch(Exception failed){error="Cannot select AUX";}}
        else if(player!=null&&prepared){focus();player.start();state="playing";}
        else if(player==null){external(KeyEvent.KEYCODE_MEDIA_PLAY);}
        report();
    }
    private void pause(){
        if(source.equals("Line Input")){try{selectSource("Android playback");}catch(Exception failed){error="Cannot disconnect AUX";}}
        else if(player!=null&&prepared){player.pause();state="paused";}
        else external(KeyEvent.KEYCODE_MEDIA_PAUSE);
        report();
    }
    private void stop(){
        if(source.equals("Line Input"))try{selectSource("Android playback");}catch(Exception failed){error="Cannot disconnect AUX";report();return;}
        releasePlayer();state="idle";title="";error="";report();
    }
    private void external(int code){
        long now=android.os.SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0));
    }
    private void playUrl(JSONObject data)throws Exception{
        String url=data.getString("url");
        if(url.length()>2048)throw new IllegalArgumentException("URL too long");
        URI uri=new URI(url);
        if(!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null)
            throw new IllegalArgumentException("Use an HTTP or HTTPS media URL");
        selectSource("Android playback");state="buffering";
        title=data.optString("title","Home Assistant audio");if(title.length()>100)title=title.substring(0,100);
        final MediaPlayer next=new MediaPlayer();player=next;
        next.setAudioStreamType(AudioManager.STREAM_MUSIC);
        next.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
        next.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer mp){
            if(player!=mp)return;prepared=true;if(intercomHeld){intercomResume=true;return;}focus();mp.start();state="playing";report();
        }});
        next.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer mp){
            if(player==mp){state="idle";if(intercomHeld){intercomResume=false;intercomState="idle";}audio.abandonAudioFocus(focus);report();}
        }});
        next.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer mp,int what,int extra){
            if(player==mp){releasePlayer();state="idle";error="Playback failed ("+what+","+extra+")";report();}return true;
        }});
        final String mediaUrl=url;
        new Thread(new Runnable(){public void run(){
            try{next.setDataSource(mediaUrl);next.prepareAsync();}
            catch(Exception failed){handler.post(new Runnable(){public void run(){if(player==next){releasePlayer();state="idle";error="Could not open audio URL";report();}}});}
        }},"wallpanel-audio-url").start();
    }
    void command(String raw){
        if(!running)return;
        try{
            JSONObject data=new JSONObject(raw);String cmd=data.getString("command");
            if(intercomHeld&&!cmd.equals("status"))throw new IllegalStateException("Intercom owns call audio");
            requestId=data.optString("request_id","");
            if(!requestId.matches("[A-Za-z0-9_-]{0,64}"))throw new IllegalArgumentException("Invalid request id");
            commandSeq++;
            if(cmd.equals("volume")){
                volumePolicy.volume(data.getDouble("value"));
            }else if(cmd.equals("mute")){
                volumePolicy.mute(data.getBoolean("value"));
            }else if(cmd.equals("source")){
                selectSource(data.getString("value"));
            }else if(cmd.equals("external_speakers")){
                boolean enabled=data.getBoolean("value");
                VendorAudio.external(enabled);
                if(!listener.hardware("audio speakers "+(enabled?1:0)))throw new java.io.IOException("Hardware command queue unavailable");
            }else if(cmd.equals("speaker_saved")){
                if(!data.getBoolean("ok"))error="Could not save vendor speaker preference";
                else if("true".equals(android.provider.Settings.System.getString(getContentResolver(),"com.sznaner.pulldownmenu.showing"))){
                    // Tuya refreshes this tile only when its dialog is shown.
                    // Refresh an already-open dialog without opening a new one.
                    sendBroadcast(new Intent("com.yhk.pulldown.action").setPackage("com.sznaner.pulldownmenu"));
                }
            }else if(cmd.equals("play_url"))playUrl(data);
            else if(cmd.equals("play"))play();
            else if(cmd.equals("pause"))pause();
            else if(cmd.equals("stop")){if(player==null&&!source.equals("Line Input"))external(KeyEvent.KEYCODE_MEDIA_STOP);stop();}
            else if(cmd.equals("off")){stop();power=false;listener.hardware("audio power 0");}
            else if(cmd.equals("on")){power=true;listener.hardware("audio power 1");}
            else if(cmd.equals("next"))external(KeyEvent.KEYCODE_MEDIA_NEXT);
            else if(cmd.equals("previous"))external(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            else if(cmd.equals("seek")){
                double position=data.getDouble("value");if(position<0||position>2147483)throw new IllegalArgumentException("Invalid position");
                if(player!=null&&prepared)player.seekTo((int)(position*1000));
            }else if(!cmd.equals("status"))return;
            report();
        }catch(Exception invalid){error="Audio command failed: "+invalid.getClass().getSimpleName();report();}
    }
    void hardwareResult(String command,boolean ok){
        if(!running)return;
        if(command.equals("speaker_saved")){try{command(new JSONObject().put("command","speaker_saved").put("ok",ok).toString());}catch(Exception ignored){}}
        else if(!ok){error="Amplifier route update failed";report();}
    }
    private void report(){
        try{
            boolean active=state.equals("playing")||state.equals("buffering");
            if(active&&!wake.isHeld())wake.acquire();else if(!active&&wake.isHeld())wake.release();
            VolumePolicy.Snapshot levels=volumePolicy.snapshot();
            int max=levels.maximum,volume=levels.index;
            boolean muted=levels.muted;
            lastVolume=volume;
            JSONObject data=new JSONObject();data.put("state",power?state:"off");data.put("source",source);
            data.put("command_seq",commandSeq);
            data.put("request_id",requestId);data.put("audio_session",audioSession);data.put("volume_steps",max);
            data.put("volume_policy","slider_unmutes");
            data.put("protocol_version",2);
            data.put("source_control","vendor_aux_mode");
            data.put("volume_scope","Android playback");
            data.put("external_speakers",VendorAudio.external());
            data.put("volume",max==0?0:(double)volume/max);data.put("muted",muted);data.put("title",title);data.put("error",error);
            if(player!=null&&prepared){data.put("duration",player.getDuration()/1000.0);data.put("position",player.getCurrentPosition()/1000.0);}
            int st=state.equals("playing")?PlaybackState.STATE_PLAYING:state.equals("paused")?PlaybackState.STATE_PAUSED:state.equals("buffering")?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_STOPPED;
            if(source.equals("Android playback")&&player!=null){
                session.setPlaybackState(new PlaybackState.Builder().setState(st,prepared?player.getCurrentPosition():0,1).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO).build());
                session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).build());
                session.setActive(active||state.equals("paused"));
            }else session.setActive(false);
            // Native AUX is a board route: never request the old PCM loopback.
            listener.state(data);
        }catch(Exception ignored){}
    }
}
