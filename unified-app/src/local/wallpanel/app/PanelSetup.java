package local.wallpanel.app;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.*;

/** First-run provisioning: existing authenticated helper or a real app-context root grant. */
final class PanelSetup {
    static final String[] FILES={"wallpanel-core","wallpanel-capture","wallpanel-privilege","wallpanel-system-ui.jar","setup-panel.sh","bootstrap-panel.sh"};
    private static byte[] bytes(InputStream in,int limit)throws IOException{try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))>=0){if(out.size()+n>limit)throw new IOException("Setup data too large");out.write(b,0,n);}return out.toByteArray();}finally{in.close();}}
    private static String sha(byte[] value)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value))out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
    static File stage(Context context)throws Exception{
        File parent=context.getFilesDir().getCanonicalFile(),folder=new File(parent,"panel-setup");if(!folder.mkdirs()&&!folder.isDirectory())throw new IOException("Setup folder unavailable");
        if(!folder.getCanonicalFile().getParentFile().equals(parent))throw new IOException("Unsafe setup folder");
        String abi=NativeAssets.abi(android.os.Build.SUPPORTED_ABIS);String selected=new ConfigStore(context).load().profile;String profile=selected.equals("a133")?"a133":"g10";String prefix=NativeAssets.prefix(profile,android.os.Build.SUPPORTED_ABIS);
        JSONObject manifest=new JSONObject(new String(bytes(context.getAssets().open("provision/manifest.json"),16384),"UTF-8"));JSONObject sums=(profile.equals("g10")?manifest:manifest.getJSONObject("profiles").getJSONObject(profile)).getJSONObject("variants").getJSONObject(abi).getJSONObject("files");
        for(String name:FILES){String asset=name.endsWith(".sh")?"provision/"+(profile.equals("a133")&&name.equals("setup-panel.sh")?"setup-a133.sh":name):prefix+name;byte[] content=bytes(context.getAssets().open(asset),1024*1024);if(!sha(content).equals(sums.getString(name)))throw new IOException("Setup integrity check failed");
            File file=new File(folder,name);FileOutputStream out=new FileOutputStream(file);try{out.write(content);out.getFD().sync();}finally{out.close();}if(!file.setExecutable(true,true))throw new IOException("Setup file permissions failed");
        }return folder;
    }
    private static JSONObject rpc(String mode)throws Exception{
        LocalSocket socket=PrivilegeChannel.open("SETUP "+mode+"\n");try{socket.setSoTimeout(mode.equals("CHECK")?20000:145000);String text=new String(bytes(socket.getInputStream(),16384),"UTF-8").trim();return new JSONObject(text);}finally{socket.close();}
    }
    private static final class Command {int code;String text;Command(int c,String t){code=c;text=t;}}
    private static Command run(final String[] args,int seconds)throws Exception{
        final Process p=new ProcessBuilder(args).redirectErrorStream(true).start();FutureTask<Command> task=new FutureTask<Command>(new Callable<Command>(){public Command call()throws Exception{String text=new String(bytes(p.getInputStream(),16384),"UTF-8");return new Command(p.waitFor(),text);}});
        Thread thread=new Thread(task,"wallpanel-setup-process");thread.setDaemon(true);thread.start();try{return task.get(seconds,TimeUnit.SECONDS);}finally{p.destroy();}
    }
    private static int rootMode(){
        for(int mode=1;mode<=2;mode++)try{Command result=run(mode==1?new String[]{"/system/xbin/su","0","id","-u"}:new String[]{"/system/xbin/su","-c","id -u"},6);if(result.code==0&&result.text.trim().equals("0"))return mode;}catch(Exception ignored){}
        return 0;
    }
    private static String quoted(String text){return "'"+text.replace("'","'\\''")+"'";}
    static JSONObject check(Context context)throws Exception{
        try{JSONObject status=rpc("CHECK");if(status.optInt("setup_protocol",0)>=2){status.put("provider","service");return status;}}catch(Exception ignored){}
        JSONObject status=new JSONObject();status.put("v",1);status.put("ok",true);status.put("root",false);status.put("provider","bootstrap_required");
        try{File folder=stage(context);Command result=run(new String[]{new File(folder,"wallpanel-core").getAbsolutePath(),"--probe"},8);JSONObject probe=new JSONObject(result.text.trim());status.put("candidate_g10",probe.optString("profile").equals("g10")&&probe.optBoolean("layout_matched",false));status.put("candidate_a133",probe.optString("profile").equals("a133")&&probe.optBoolean("layout_matched",false));}catch(Exception ignored){status.put("candidate_g10",false);}
        return status;
    }
    static JSONObject change(Context context,boolean restore)throws Exception{
        JSONObject check=check(context);boolean service=check.optString("provider").equals("service");int su=0;
        if(service&&!check.optBoolean("supported",false))return new JSONObject("{\"v\":1,\"ok\":false,\"error\":\"unsupported_hardware\"}");
        if(service&&!check.optBoolean("policy_ready",false))return new JSONObject("{\"v\":1,\"ok\":false,\"error\":\"selinux_policy_setup_required\"}");
        if(service&&restore&&!check.optBoolean("backup_available",false))return new JSONObject("{\"v\":1,\"ok\":false,\"error\":\"no_previous_setup\"}");
        if(!service){if(restore)throw new IOException("Restore requires installed panel support");su=rootMode();if(su==0)return new JSONObject("{\"v\":1,\"ok\":false,\"error\":\"adb_bootstrap_required\"}");}
        ConfigStore store=new ConfigStore(context);boolean resume=store.runtimeEnabled();store.setRuntimeEnabled(false);context.stopService(new Intent(context,BridgeService.class));
        JSONObject result;
        if(service){
            result=null;for(int attempt=0;attempt<16;attempt++){result=rpc(restore?"RESTORE":"APPLY");if(!result.optString("error").equals("privilege_request_rejected"))break;Thread.sleep(250);}
        }else{
            File folder=stage(context);String script=new File(folder,"bootstrap-panel.sh").getAbsolutePath();String command="/system/bin/sh "+quoted(script)+" "+quoted(folder.getAbsolutePath())+" apply";
            Command response=run(su==1?new String[]{"/system/xbin/su","0","/system/bin/sh",script,folder.getAbsolutePath(),"apply"}:new String[]{"/system/xbin/su","-c",command},145);
            String[] lines=response.text.trim().split("\\r?\\n");result=new JSONObject(lines[lines.length-1]);if(response.code!=0&&result.optBoolean("ok",false))throw new IOException("Root setup did not finish");
        }
        if(result!=null&&result.optBoolean("ok",false)&&resume&&!restore){store.setRuntimeEnabled(true);BridgeService.ensureStarted(context);}
        return result;
    }
    private PanelSetup(){}
}
