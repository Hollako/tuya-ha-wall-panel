package local.wallpanel.app;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;

/** App-owned chime: independent of missing system ringtone files and silent ring stream. */
final class SipRinger {
    private final AudioManager audio;private final int oldVolume;private final boolean oldMute;
    private MediaPlayer player;private boolean closed;
    SipRinger(Context context,SipConfig config)throws Exception{
        audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);oldMute=audio.isStreamMute(AudioManager.STREAM_MUSIC);
        if(!config.ringtoneEnabled||config.ringVolume==0){closed=true;return;}
        try{
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*config.ringVolume+50)/100),0);
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0);
            player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            AssetFileDescriptor source=context.getAssets().openFd("intercom-chime.wav");
            try{player.setDataSource(source.getFileDescriptor(),source.getStartOffset(),source.getLength());}finally{source.close();}
            player.setLooping(true);player.prepare();player.start();
        }catch(Exception failure){close();throw failure;}
    }
    void close(){if(closed)return;closed=true;
        if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
        try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume,0);audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,oldMute?AudioManager.ADJUST_MUTE:AudioManager.ADJUST_UNMUTE,0);}catch(Exception ignored){}
    }
}
