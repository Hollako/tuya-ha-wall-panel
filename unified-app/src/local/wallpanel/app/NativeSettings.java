package local.wallpanel.app;

/** Hardware-only staging format. Rendering this does not start any worker. */
public final class NativeSettings {
    public static String render(PanelConfig c,int[][] bindings){
        c.validate();if(!"g10".equals(c.profile)&&!"a133".equals(c.profile))throw new IllegalArgumentException("Select a supported hardware profile");
        for(String id:c.selected)if(!ModuleCatalog.find(id).supported(c.profile))throw new IllegalArgumentException("Module is not supported by this profile");
        if(!c.deviceId.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Missing panel identity");
        if(bindings==null||bindings.length!=4)throw new IllegalArgumentException("Four button bindings are required");
        StringBuilder b=new StringBuilder("version=1\nprofile=").append(c.profile).append("\ndevice_id=").append(c.deviceId)
            .append("\nbuttons=").append(c.selected.contains("buttons")?1:0)
            .append("\nrelays=").append(c.selected.contains("relays")?1:0)
            .append("\nknob_input=").append(c.selected.contains("knob_input")?1:0)
            .append("\nknob_display=").append(c.selected.contains("knob_display")?1:0)
            .append("\naudio=").append(c.selected.contains("audio")?1:0)
            .append("\namplifier=").append(c.selected.contains("amplifier")?1:0)
            .append("\ndisplay=").append(c.selected.contains("display")?1:0).append('\n');
        for(int button=0;button<4;button++){
            if(bindings[button]==null||bindings[button].length!=2)throw new IllegalArgumentException("Single/double bindings required");
            for(int gesture=0;gesture<2;gesture++){
                int target=bindings[button][gesture];if(target<0||target>2||c.profile.equals("a133")&&target!=0)throw new IllegalArgumentException("Only relay 1, relay 2 or no binding is supported");
                b.append("button_").append(button+1).append(gesture==0?"_single=":"_double=").append(target).append('\n');
            }
        }
        return b.toString();
    }
    private NativeSettings(){}
}
