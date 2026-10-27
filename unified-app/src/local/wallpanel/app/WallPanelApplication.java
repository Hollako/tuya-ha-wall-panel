package local.wallpanel.app;

/** Installs shared output policy before any service, including SIP, can start. */
public final class WallPanelApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();ReceiverImport.pending(this);ReceiverRuntime.initialize(this);}
}
