package local.wallpanel.app;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;
final class LauncherSupport {
    static boolean isDefaultHome(Context c){
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r=c.getPackageManager().resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        return r!=null&&r.activityInfo!=null&&c.getPackageName().equals(r.activityInfo.packageName)&&(c.getPackageName()+".HomeActivity").equals(r.activityInfo.name);
    }
    static void chooseHome(Activity activity){
        String[] actions={Settings.ACTION_HOME_SETTINGS,Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS};
        for(String action:actions){Intent intent=new Intent(action);if(intent.resolveActivity(activity.getPackageManager())!=null){
            try{activity.startActivity(intent);return;}catch(android.content.ActivityNotFoundException ignored){}catch(SecurityException ignored){}
        }}
        Toast.makeText(activity,"Open Android Settings > Apps > Default apps > Home app and choose SmartWay Panel",Toast.LENGTH_LONG).show();
    }
    static Intent dashboardIntent(Context c){
        if(isDefaultHome(c))return new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new Intent(c,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    private LauncherSupport(){}
}
