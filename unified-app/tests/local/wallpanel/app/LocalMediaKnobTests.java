package local.wallpanel.app;
public final class LocalMediaKnobTests {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        LocalMediaKnob route=new LocalMediaKnob(true,5,"media_player.renamed_audio","1/2 Panel Audio");
        check("up".equals(route.action("rotate_right")));
        check("down".equals(route.action("rotate_left")));
        check("mute".equals(route.action("press")));
        check("play_pause".equals(route.action("long_press")));
        check(route.action("double_press")==null&&route.action("bad")==null);
        check(LocalMediaKnob.off().action("press")==null);
        check(LocalMediaKnob.volume(6,15,5,true)==7.0/15);
        check(LocalMediaKnob.volume(6,15,1,true)==7.0/15); // Minimum one real volume step.
        check(LocalMediaKnob.volume(15,15,5,true)==1);
        check(LocalMediaKnob.volume(0,15,5,false)==0);
        check(LocalMediaKnob.volume(0,0,5,true)==0);
        check(route.frame(.4,true,"playing","Song").equals("1/2 Panel Audio\n40%\nSong"));
        check(!route.frame(.4,false,"playing","Emoji \ud83c\udfb5\nTrack").contains("\ud83c"));
        check(route.frame(1,false,"paused","x").split("\n").length==3);
        boolean rejected=false;try{new LocalMediaKnob(true,5,"light.fake","Audio");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        rejected=false;try{new LocalMediaKnob(true,0,"media_player.audio","Audio");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        System.out.println("PASS: local volume/mute/hold routing, double-press navigation, offline policy, minimum hardware step, bounds, exact local entity validation and safe native feedback frames.");
    }
}
