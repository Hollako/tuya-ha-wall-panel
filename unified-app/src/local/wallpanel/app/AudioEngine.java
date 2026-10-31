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
    private boolean receiverLocalStopped;
    private boolean power=true, prepared=false;
    private int commandSeq=0;
    private AudioRouteMemory routeMemory;
    private void playerVolume(double value)throws Exception{
        com.wallpanel.multiroom.RoomVolume.set(this,value);if(player!=null){float gain=com.wallpanel.multiroom.RoomVolume.gain();player.setVolume(gain,gain);}
    }
    private String requestId="";
    private boolean intercomHeld,intercomResume;private String intercomState;private volatile int focusGeneration;
    private final String audioSession=java.util.UUID.randomUUID().toString();
    private PowerManager.WakeLock wake;
    private final AudioManager.OnAudioFocusChangeListener focus=new AudioManager.OnAudioFocusChangeListener(){
        public void onAudioFocusChange(int change){final int generation=focusGeneration;if(change<0)handler.post(new Runnable(){public void run(){if(running&&generation==focusGeneration&&!intercomHeld&&player!=null)pause();}});}
    };

    void start()throws Exception {
        com.wallpanel.multiroom.RoomVolume.initialize(this);
        final android.content.SharedPreferences routes=getSharedPreferences("audio_routes",MODE_PRIVATE);
        routeMemory=new AudioRouteMemory(new AudioRouteMemory.Store(){
            public boolean contains(String key){return routes.contains(key);}
            public boolean get(String key){return routes.getBoolean(key,false);}
            public void save(String key,boolean value)throws Exception {if(!routes.edit().putBoolean(key,value).commit())throw new java.io.IOException("Audio setting could not be saved");}
        },new AudioRouteMemory.Board(){
            public boolean external()throws Exception{return VendorAudio.external();}
            public void external(boolean value)throws Exception{VendorAudio.external(value);}
            public boolean line()throws Exception{return VendorAudio.aux();}
            public void line(boolean value)throws Exception{VendorAudio.source(value);}
        });
        boolean restoreSource=!IntercomSession.active&&!com.wallpanel.multiroom.GroupRuntime.active()&&!com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.streaming();
        routeMemory.restore(restoreSource);
        if(restoreSource&&VendorAudio.aux())listener.hardware("audio power 1");
        running=true;
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        VendorAudio.init((android.app.Application)getApplicationContext());
        syncSource();
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:audio");
        session=new MediaSession(this,"SmartWay Panel Audio");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            public void onPlay(){play();} public void onPause(){pause();} public void onStop(){stop();}
            public void onSeekTo(long pos){if(player!=null&&prepared)player.seekTo((int)Math.min(Integer.MAX_VALUE,Math.max(0,pos)));}
        });
        handler.post(tick);

    }
    void close(){
        if(source.equals("AirPlay"))ReceiverRuntime.stoppedLocally();
        running=false;handler.removeCallbacksAndMessages(null);releasePlayer();
        if(wake!=null&&wake.isHeld())wake.release();if(session!=null)session.release();
    }
    private final Runnable tick=new Runnable(){public void run(){if(!running)return;if(IntercomSession.active&&!intercomHeld)beginIntercom();if(!IntercomSession.active&&intercomHeld)endIntercom();if(!intercomHeld)syncSource();report();handler.postDelayed(this,1000);}};
    boolean powered(){return power;}
    boolean hasLocalItem(){return player!=null||source.equals("Line Input");}
    void beginIntercom(){if(intercomHeld)return;focusGeneration++;intercomHeld=true;intercomState=state;intercomResume=player!=null&&((prepared&&player.isPlaying())||state.equals("buffering"));if(intercomResume&&prepared)player.pause();if(state.equals("playing")||state.equals("buffering"))state="paused";}
    void endIntercom(){if(!intercomHeld)return;focusGeneration++;intercomHeld=false;state=intercomState;if(intercomResume&&player!=null&&prepared){focus();player.start();state="playing";}intercomResume=false;}
    private void syncSource(){
        if(source.equals("AirPlay")){
            if(ReceiverRuntime.enabled(this))return;
            try{selectSource("Android playback");}catch(Exception failed){error="Cannot restore Android source";}
        }
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
        if(!chosen.equals("Android playback")&&!chosen.equals("Line Input")&&!chosen.equals("AirPlay"))throw new IllegalArgumentException("Unknown source");
        if(chosen.equals("AirPlay")&&!ReceiverRuntime.enabled(this))throw new IllegalStateException("Enable the AirPlay receiver first");
        com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.select(false);
        if(!chosen.equals("AirPlay"))ReceiverRuntime.stoppedLocally();
        VendorAudio.source(chosen.equals("Line Input"));
        releasePlayer();source=chosen;power=true;title="";error="";listener.hardware("audio power 1");
        receiverLocalStopped=false;
        state=chosen.equals("Line Input")?"playing":"idle";
        com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.select(chosen.equals("AirPlay"));
    }
    boolean receiverStarted(){
        if(com.wallpanel.multiroom.GroupRuntime.active())return false;
        if(!running||intercomHeld)return false;
        try{selectSource("AirPlay");state="playing";report();return true;}
        catch(Exception failed){error="Could not select AirPlay output";report();return false;}
    }
    void receiverEnded(){if(source.equals("AirPlay")){state="idle";title="";report();}}
    boolean groupOutput(){
        if(!running||intercomHeld||IntercomSession.active)return false;
        try{releasePlayer();selectSource("Android playback");state="idle";return true;}
        catch(Exception failed){error="Could not select group output";report();return false;}
    }
    void groupEnded(){state="idle";title="";if(intercomHeld){intercomState="idle";intercomResume=false;}report();}
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
        if(source.equals("AirPlay")){
            if(com.wallpanel.audioreceiver.ReceiverRemote.request("play")){receiverLocalStopped=false;com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.select(true);}
            else error=com.wallpanel.audioreceiver.ReceiverRemote.lastError();
        }
        else if(source.equals("Line Input")){try{selectSource("Line Input");}catch(Exception failed){error="Cannot select AUX";}}
        else if(player!=null&&prepared){focus();player.start();state="playing";}
        else if(player==null){external(KeyEvent.KEYCODE_MEDIA_PLAY);}
        report();
    }
    private void pause(){
        error="";
        if(source.equals("AirPlay")){if(!com.wallpanel.audioreceiver.ReceiverRemote.request("pause"))error=com.wallpanel.audioreceiver.ReceiverRemote.lastError();}
        else if(source.equals("Line Input")){try{selectSource("Android playback");routeMemory.rememberLine(false);}catch(Exception failed){error="Cannot disconnect AUX";}}
        else if(player!=null&&prepared){player.pause();state="paused";}
        else external(KeyEvent.KEYCODE_MEDIA_PAUSE);
        report();
    }
    private void stop(){
        if(source.equals("AirPlay")){receiverLocalStopped=true;com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.select(false);ReceiverRuntime.stoppedLocally();}
        if(source.equals("Line Input"))try{selectSource("Android playback");routeMemory.rememberLine(false);}catch(Exception failed){error="Cannot disconnect AUX";report();return;}
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
        selectSource("Android playback");routeMemory.rememberLine(false);state="buffering";
        title=data.optString("title","Home Assistant audio");if(title.length()>100)title=title.substring(0,100);
        final MediaPlayer next=new MediaPlayer();player=next;
        float gain=com.wallpanel.multiroom.RoomVolume.gain();next.setVolume(gain,gain);
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
    void knob(String action,int step){
        if(!running)return;
        try{
            VolumePolicy.Snapshot snapshot=new VolumePolicy.Snapshot(1000,(int)Math.round(com.wallpanel.multiroom.RoomVolume.value(this)*1000),com.wallpanel.multiroom.RoomVolume.muted(this));JSONObject command=new JSONObject();
            if(action.equals("up")||action.equals("down"))command.put("command","volume").put("value",LocalMediaKnob.volume(snapshot.index,snapshot.maximum,step,action.equals("up")));
            else if(action.equals("mute"))command.put("command","mute").put("value",!snapshot.muted);
            else if(action.equals("play_pause"))command.put("command","play_pause");
            else return;
            command(command.toString());
        }catch(Exception ignored){}
    }
    void command(String raw){
        if(!running)return;
        try{
            JSONObject data=new JSONObject(raw);String cmd=data.getString("command");
            if((intercomHeld||IntercomSession.active)&&!cmd.equals("status"))throw new IllegalStateException("Intercom owns call audio");
            requestId=data.optString("request_id","");
            if(!requestId.matches("[A-Za-z0-9_-]{0,64}"))throw new IllegalArgumentException("Invalid request id");
            commandSeq++;
            if(com.wallpanel.multiroom.GroupRuntime.active()){
                if(cmd.equals("volume")){com.wallpanel.multiroom.GroupRuntime.volume(this,data.getDouble("value"));report();return;}
                if(cmd.equals("mute")){com.wallpanel.multiroom.GroupRuntime.mute(this,data.getBoolean("value"));report();return;}
                if(cmd.equals("play")||cmd.equals("pause")||cmd.equals("play_pause")){com.wallpanel.multiroom.GroupRuntime.transport(cmd);report();return;}
                if(cmd.equals("next")||cmd.equals("previous"))throw new IllegalStateException("This group source has no track queue");
                if(cmd.equals("stop")||cmd.equals("off")||cmd.equals("source")||cmd.equals("play_url"))com.wallpanel.multiroom.GroupRuntime.stop(this);
            }
            if(cmd.equals("volume")){
                playerVolume(data.getDouble("value"));
            }else if(cmd.equals("receiver_delay")||cmd.equals("receiver_trim")){
                Object rawValue=data.get("value");if(!(rawValue instanceof Number))throw new IllegalArgumentException("Invalid receiver level");
                double rawNumber=((Number)rawValue).doubleValue();int value=(int)rawNumber;
                boolean delay=cmd.equals("receiver_delay");
                if(Double.isNaN(rawNumber)||Double.isInfinite(rawNumber)||rawNumber!=value||value<(delay?-500:0)||value>(delay?500:100))throw new IllegalArgumentException("Invalid receiver level");
                com.wallpanel.audioreceiver.PrefStore prefs=ReceiverRuntime.settings(this);
                prefs.put(delay?com.wallpanel.audioreceiver.PrefStore.KEY_LATENCY_OFFSET_MS:com.wallpanel.audioreceiver.PrefStore.KEY_VOLUME_TRIM,value);
                if(!delay)com.wallpanel.audioreceiver.ReceiverState.INSTANCE.setVolumeTrim(value);
            }else if(cmd.equals("receiver_enabled")){
                Object value=data.get("value");if(!(value instanceof Boolean))throw new IllegalArgumentException("Invalid receiver switch");
                boolean enabled=((Boolean)value).booleanValue();
                if(enabled&&android.os.Build.VERSION.SDK_INT<27)throw new IllegalStateException("Receiver requires Android 8.1 or newer");
                if(enabled&&!com.wallpanel.audioreceiver.PrivilegeChannel.available())throw new IllegalStateException("Receiver helper not prepared");
                com.wallpanel.audioreceiver.PrefStore prefs=ReceiverRuntime.settings(this);
                prefs.put(com.wallpanel.audioreceiver.PrefStore.KEY_ENABLED,enabled);prefs.put(com.wallpanel.audioreceiver.PrefStore.KEY_AUTOSTART,true);
                ReceiverRuntime.reconcile(this);
            }else if(cmd.equals("mute")){
                com.wallpanel.multiroom.RoomVolume.mute(this,data.getBoolean("value"));
            }else if(cmd.equals("source")){
                selectSource(data.getString("value"));
                routeMemory.rememberLine(source.equals("Line Input"));
            }else if(cmd.equals("external_speakers")){
                boolean enabled=data.getBoolean("value");
                routeMemory.external(enabled);
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
            else if(cmd.equals("play_pause")){
                if(source.equals("AirPlay")){
                    error="";
                    if(!com.wallpanel.audioreceiver.ReceiverRemote.request("play_pause"))error=com.wallpanel.audioreceiver.ReceiverRemote.lastError();
                    else if(receiverLocalStopped){receiverLocalStopped=false;com.wallpanel.audioreceiver.AudioOutputGate.INSTANCE.select(true);}
                }
                else if(player==null&&source.equals("Android playback"))external(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                else if(state.equals("playing")||state.equals("buffering"))pause();else play();
            }
            else if(cmd.equals("stop")){if(player==null&&source.equals("Android playback"))external(KeyEvent.KEYCODE_MEDIA_STOP);stop();}
            else if(cmd.equals("off")){stop();power=false;listener.hardware("audio power 0");}
            else if(cmd.equals("on")){power=true;listener.hardware("audio power 1");}
            else if(cmd.equals("next")||cmd.equals("previous")){
                if(source.equals("AirPlay")){
                    error="";
                    if(!com.wallpanel.audioreceiver.ReceiverRemote.request(cmd))error=com.wallpanel.audioreceiver.ReceiverRemote.lastError();
                }else if(source.equals("Android playback"))external(cmd.equals("next")?KeyEvent.KEYCODE_MEDIA_NEXT:KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
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
            if(com.wallpanel.multiroom.GroupRuntime.active()&&!intercomHeld){state=com.wallpanel.multiroom.GroupRuntime.paused()?"paused":"playing";com.wallpanel.multiroom.GroupHub hub=com.wallpanel.multiroom.GroupHub.get();String song=hub==null?"":hub.playingTitle();title=song.isEmpty()?"Panel audio group":song;}
            if(source.equals("AirPlay")&&!intercomHeld&&!receiverLocalStopped){
                String actual=com.wallpanel.audioreceiver.ReceiverRemote.playback();
                if(actual.equals("playing")||actual.equals("paused"))state=actual;
            }
            boolean active=state.equals("playing")||state.equals("buffering");
            if(active&&!wake.isHeld())wake.acquire();else if(!active&&wake.isHeld())wake.release();
            com.wallpanel.multiroom.RoomVolume.observe(this);
            int max=100,volume=(int)Math.round(com.wallpanel.multiroom.RoomVolume.value(this)*100);boolean muted=com.wallpanel.multiroom.RoomVolume.muted(this);
            if(player!=null&&!intercomHeld){float gain=com.wallpanel.multiroom.RoomVolume.gain();player.setVolume(gain,gain);}
            JSONObject data=new JSONObject();data.put("state",power?state:"off");data.put("source",source);
            data.put("command_seq",commandSeq);
            data.put("request_id",requestId);data.put("audio_session",audioSession);data.put("volume_steps",max);
            data.put("volume_policy","slider_unmutes");
            data.put("protocol_version",2);
            org.json.JSONArray sources=new org.json.JSONArray().put("Android playback").put("Line Input");
            if(ReceiverRuntime.enabled(this))sources.put("AirPlay");data.put("sources",sources);
            data.put("receiver_state",ReceiverRuntime.status());
            com.wallpanel.audioreceiver.PrefStore receiverSettings=ReceiverRuntime.settings(this);
            data.put("receiver_supported",android.os.Build.VERSION.SDK_INT>=27);
            data.put("receiver_enabled",receiverSettings.isEnabled());
            data.put("receiver_delay",receiverSettings.getLatencyOffsetMs());data.put("receiver_trim",receiverSettings.getVolumeTrim());
            data.put("transport_control",source.equals("AirPlay")?"airplay_source":"player");
            data.put("transport_available",com.wallpanel.audioreceiver.ReceiverRemote.available());
            data.put("artwork_id",source.equals("AirPlay")?com.wallpanel.audioreceiver.ReceiverState.INSTANCE.getArtworkId():"");
            if(source.equals("AirPlay")){
                com.wallpanel.audioreceiver.ReceiverState receiver=com.wallpanel.audioreceiver.ReceiverState.INSTANCE;
                title=receiver.getTitle()==null?"":receiver.getTitle();
                data.put("artist",receiver.getArtist());data.put("album",receiver.getAlbum());
            }
            data.put("source_control","vendor_aux_mode");
            data.put("volume_scope","Android playback");
            data.put("external_speakers",VendorAudio.external());
            data.put("volume",max==0?0:(double)volume/max);data.put("muted",muted);data.put("title",title);
            String remoteError=source.equals("AirPlay")?com.wallpanel.audioreceiver.ReceiverRemote.lastError():"";
            data.put("error",error.isEmpty()?remoteError:error);
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
