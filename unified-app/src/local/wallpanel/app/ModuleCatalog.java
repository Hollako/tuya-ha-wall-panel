package local.wallpanel.app;

/** Keep profile support, installer selection, and runtime availability distinct. */
public final class ModuleCatalog {
    public static final class Module {
        public final String id,label;
        public final boolean hardware,implemented;
        Module(String i,String l,boolean h,boolean ready){id=i;label=l;hardware=h;implemented=ready;}
        public boolean supported(String profile){return !hardware||"g10".equals(profile);}
        public boolean available(PanelConfig config,boolean adapterVerified,boolean workerReady){
            return implemented&&supported(config.profile)&&config.selected.contains(id)&&(!hardware||adapterVerified)&&workerReady;
        }
    }
    public static final Module[] ALL={
        new Module("dashboard","Dashboard / app",false,true),
        new Module("display","Main display and backlight",true,true),
        new Module("knob_input","Knob input",true,true),
        new Module("knob_display","Knob display",true,true),
        new Module("buttons","Four buttons",true,true),
        new Module("relays","Two relays",true,true),
        new Module("audio","Audio playback and routing",true,true),
        new Module("amplifier","Amplifier",true,true),
        new Module("sip","SIP intercom",false,true)
    };
    public static Module find(String id){for(Module m:ALL)if(m.id.equals(id))return m;return null;}
    private ModuleCatalog(){}
}
