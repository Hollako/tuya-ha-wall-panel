package local.wallpanel.app;

/** Installs shared output policy before any service, including SIP, can start. */
public final class WallPanelApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();ReceiverImport.pending(this);ReceiverRuntime.initialize(this);
        com.wallpanel.audioreceiver.SettingsHub.product(new WallPanelSettings(this));
        com.wallpanel.multiroom.MediaActivity.local(new com.wallpanel.multiroom.MediaActivity.Local(){public org.json.JSONObject snapshot(){return BridgeService.localAudioState();}public void transport(String verb)throws Exception{BridgeService.localAudioControl(verb);}});
        com.wallpanel.multiroom.GroupRuntime.host(new com.wallpanel.multiroom.GroupRuntime.Host(){
            public boolean prepare(){return BridgeService.groupOutput();}
            public boolean intercom(){return IntercomSession.active;}
            public void finished(){BridgeService.groupEnded();}
        });
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityResumed(android.app.Activity a){SettingsBackService.resumed(a);}
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
            public void onActivityStarted(android.app.Activity a){}
            public void onActivityPaused(android.app.Activity a){SettingsBackService.paused(a);}
            public void onActivityStopped(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){}
        });
    }
}
