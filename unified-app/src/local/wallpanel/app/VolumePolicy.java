package local.wallpanel.app;

/** Android's implicit mute at index zero is distinct from the user's Mute button. */
final class VolumePolicy {
    interface Backend {
        int maximum();
        int index();
        boolean muted();
        void setIndex(int value);
        void setMuted(boolean value);
    }
    static final class Snapshot {
        final int maximum,index;final boolean muted;
        Snapshot(int maximum,int index,boolean muted){this.maximum=maximum;this.index=index;this.muted=muted;}
    }
    private final Backend backend;
    private boolean explicitZeroMute;
    VolumePolicy(Backend backend){this.backend=backend;}

    void volume(double value){
        if(Double.isNaN(value)||value<0||value>1)throw new IllegalArgumentException("Invalid volume");
        int target=(int)Math.round(value*backend.maximum());
        explicitZeroMute=false;
        backend.setIndex(target);
        // Moving the slider above zero restores audible playback, including
        // when Android had marked the stream muted at zero volume.
        if(target>0&&backend.muted())backend.setMuted(false);
    }
    void mute(boolean value){
        explicitZeroMute=value&&backend.index()==0;
        backend.setMuted(value);
    }
    Snapshot snapshot(){
        int maximum=backend.maximum(),index=Math.max(0,Math.min(maximum,backend.index()));
        if(index>0)explicitZeroMute=false;
        return new Snapshot(maximum,index,explicitZeroMute||(index>0&&backend.muted()));
    }
}
