package local.wallpanel.app;
import android.content.Context;
import android.media.AudioManager;
/** Temporary call route/focus. Restores the previous AUX, playback and voice settings. */
final class SipAudioLease {
    private final AudioManager audio;private final int oldMode,oldVoice;private final boolean oldSpeaker;private Boolean aux,amplifier;private boolean closed;
    private final AudioManager.OnAudioFocusChangeListener focus=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int value){}};
    SipAudioLease(Context context)throws Exception{
        audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);oldMode=audio.getMode();oldSpeaker=audio.isSpeakerphoneOn();oldVoice=audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        try{aux=Boolean.valueOf(VendorAudio.aux());}catch(Exception ignored){}
        try{IntercomSession.active=true;amplifier=BridgeService.beginIntercom();if(aux!=null&&aux.booleanValue())VendorAudio.source(false);audio.requestAudioFocus(focus,AudioManager.STREAM_RING,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);audio.setMode(AudioManager.MODE_RINGTONE);audio.setSpeakerphoneOn(true);}catch(Exception failed){close();throw failed;}
    }
    void answer(SipConfig config){audio.requestAudioFocus(focus,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);audio.setMode(AudioManager.MODE_IN_COMMUNICATION);audio.setSpeakerphoneOn(true);int max=audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL,(max*config.volume+50)/100,0);}
    void close(){if(closed)return;closed=true;IntercomSession.active=false;
        try{audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL,oldVoice,0);}catch(Exception ignored){}
        try{if(audio.getMode()==AudioManager.MODE_RINGTONE||audio.getMode()==AudioManager.MODE_IN_COMMUNICATION){audio.setSpeakerphoneOn(oldSpeaker);audio.setMode(oldMode);}}catch(Exception ignored){}
        try{audio.abandonAudioFocus(focus);}catch(Exception ignored){}
        if(aux!=null)try{VendorAudio.source(aux.booleanValue());}catch(Exception ignored){}
        BridgeService.endIntercom(amplifier);
    }
}
