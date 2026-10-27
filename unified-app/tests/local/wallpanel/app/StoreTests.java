package local.wallpanel.app;
import android.content.Context;
import android.content.SharedPreferences;
public final class StoreTests {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Store check "+checks);}
    public static void main(String[] args)throws Exception {
        Context context=new Context();SharedPreferences prefs=context.getSharedPreferences("wall_panel",0);
        // Existing pre-revision preferences must survive a new store/app instance.
        prefs.edit().putString("dashboard","http://ha:8098/?kiosk&noedit").putString("broker","ha")
            .putString("username","user").putString("password",Secrets.encrypt("saved secret")).commit();
        ConfigStore a=new ConfigStore(context);PanelConfig old=a.load();check(old.dashboard.contains("?kiosk&noedit"));check(old.password.equals("saved secret"));
        String id=old.deviceId;ConfigStore b=new ConfigStore(context);check(b.load().deviceId.equals(id));check(b.load().revision==0);
        String draft="unfinished form: partial URL, 18a port, draft secret";
        a.saveDraft(draft,old.revision);check(!prefs.getString("draft_payload","").contains("draft secret"));
        check(b.loadDraft().equals(draft));check(b.load().password.equals("saved secret"));check(b.load().dashboard.equals(old.dashboard));
        old.selected.add("dashboard");old.profile="g10";a.save(old,"");check(old.revision==1);check(b.loadDraft()==null);check(b.lastSavedAt()>0);
        check(b.load().selected.contains("dashboard"));check(b.load().password.equals("saved secret"));check(b.load().deviceId.equals(id));
        PanelConfig stale=b.load(),fresh=a.load();fresh.dashboard="https://new.example/dashboard";a.save(fresh,"");
        boolean refused=false;try{b.save(stale,"");}catch(IllegalStateException e){refused=true;}check(refused);check(a.load().dashboard.equals(fresh.dashboard));
        refused=false;try{b.saveDraft("stale form",stale.revision);}catch(IllegalStateException e){refused=true;}check(refused);
        prefs.edit().putString("draft_payload",Secrets.encrypt("old draft")).putLong("draft_revision",0).commit();check(b.loadDraft()==null);
        fresh=a.load();a.saveDraft(draft,fresh.revision);Secrets.fail=true;fresh.dashboard="https://should-not-save.example/";
        refused=false;try{a.save(fresh,"");}catch(Exception e){refused=true;}check(refused);Secrets.fail=false;
        check(!b.load().dashboard.equals(fresh.dashboard));check(b.loadDraft().equals(draft));
        PanelConfig failure=a.load();prefs.failCommit=true;refused=false;try{a.save(failure,"");}catch(IllegalStateException e){refused=true;}check(refused);check(b.loadDraft().equals(draft));
        a.clearDraft();check(b.loadDraft()==null);
        check(!prefs.data.values().toString().contains("saved secret"));
        PanelConfig beforeRemote=a.load();String secret=beforeRemote.password;
        a.applyDashboard("https://ha.example/new?kiosk#room");PanelConfig applied=b.load();
        check(applied.dashboard.equals("https://ha.example/new?kiosk#room"));check(applied.revision==beforeRemote.revision+1);
        check(applied.password.equals(secret)&&applied.deviceId.equals(id));
        a.applyDashboard(applied.dashboard);check(b.load().revision==applied.revision);
        refused=false;try{a.save(beforeRemote,"");}catch(IllegalStateException e){refused=true;}check(refused);
        refused=false;try{a.applyDashboard("javascript:bad");}catch(IllegalArgumentException e){refused=true;}check(refused);check(b.load().dashboard.equals(applied.dashboard));
        a.applyDashboard("");check(b.load().dashboard.equals(""));
        PanelConfig app=a.load();check(app.displayMode.equals("web")&&app.appPackage.equals(""));
        app.displayMode="app";app.appPackage="io.homeassistant.companion.android";a.save(app,"");
        check(b.load().displayMode.equals("app")&&b.load().appPackage.equals(app.appPackage));
        check(b.dashboardSettings().displayMode.equals("app")&&b.dashboardSettings().appPackage.equals(app.appPackage));
        a.applyDashboard("https://ha.example/saved-web");check(b.load().displayMode.equals("app"));
        app=a.load();app.displayMode="web";a.save(app,"");check(b.load().displayMode.equals("web"));
        System.out.println("PASS: "+checks+" production-store checks: existing settings/identity, encrypted-draft restart recovery, explicit apply, stale-form protection, failed encryption/commit, and no credential plaintext.");
    }
}
