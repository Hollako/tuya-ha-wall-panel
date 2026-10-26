package local.wallpanel.app;
public final class AudioPolicyTests {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        for(String cmd:new String[]{"volume","mute","source","external_speakers","play_url","play","pause","stop","off","on","next","previous","seek","status"})check(AudioPolicy.command(cmd));
        for(String cmd:new String[]{"speaker_saved","line_status","shell","reboot","play;reboot","",null})check(!AudioPolicy.command(cmd));
        check(AudioPolicy.level("0").equals("audio level 0"));check(AudioPolicy.level("100").equals("audio level 100"));
        for(String bad:new String[]{"01","-1","101","NaN","50;reboot"," 50","50\n",null})check(AudioPolicy.level(bad)==null);
        System.out.println("PASS: audio command allowlist, internal/unknown rejection and canonical 0–100 hardware trim.");
    }
}
