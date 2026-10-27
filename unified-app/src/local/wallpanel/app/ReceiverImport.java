package local.wallpanel.app;

import android.content.Context;
import com.wallpanel.audioreceiver.PrefStore;
import org.json.JSONObject;
import java.io.*;

/** Allowlisted one-time receiver settings only. Never imports HA/SIP credentials. */
final class ReceiverImport {
    static void pending(Context context){
        File file=new File(context.getFilesDir(),"receiver-migration.json");if(!file.isFile())return;
        try{
            if(file.length()>8192)throw new IOException("Import too large");
            android.system.StructStat stat=android.system.Os.lstat(file.getPath());
            if(!android.system.OsConstants.S_ISREG(stat.st_mode)||stat.st_uid!=android.os.Process.myUid()||(stat.st_mode&0077)!=0)throw new IOException("Unsafe import permissions");
            ByteArrayOutputStream data=new ByteArrayOutputStream();try(FileInputStream in=new FileInputStream(file)){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){data.write(b,0,n);if(data.size()>8192)throw new IOException("Import too large");}}
            JSONObject input=new JSONObject(new String(data.toByteArray(),"UTF-8"));
            if(input.getInt("v")!=1||!input.getString("id").matches("[A-Za-z0-9_-]{1,64}"))throw new IOException("Unsupported import");
            String name=input.getString("receiver_name"),iface=input.optString("network_interface","");
            int offset=input.getInt("latency_offset_ms"),trim=input.getInt("volume_trim"),port=input.optInt("rtsp_port",7000);
            if(name.length()<1||name.length()>63||name.indexOf('\n')>=0||name.indexOf('\r')>=0||!iface.matches("[A-Za-z0-9_.-]{0,32}")||offset< -500||offset>500||trim<0||trim>100||port<1024||port>65535)throw new IOException("Invalid receiver settings");
            if(!com.wallpanel.audioreceiver.PrivilegeChannel.available())throw new IOException("Receiver helper UID is not prepared");
            android.content.SharedPreferences prefs=context.getSharedPreferences("wpar_prefs",Context.MODE_PRIVATE);
            String id=input.getString("id");
            if(!id.equals(prefs.getString("migration_id",""))){
                if(!prefs.edit().putString(PrefStore.KEY_RECEIVER_NAME,name).putInt(PrefStore.KEY_LATENCY_OFFSET_MS,offset).putInt(PrefStore.KEY_VOLUME_TRIM,trim)
                    .putInt(PrefStore.KEY_RTSP_PORT,port).putString(PrefStore.KEY_INTERFACE_NAME,iface)
                    .putBoolean(PrefStore.KEY_HA_ENABLED,false).putBoolean(PrefStore.KEY_AUTOSTART,true).putBoolean(PrefStore.KEY_ENABLED,true).putString("migration_id",id).commit())throw new IOException("Could not save receiver import");
            }
            if(!file.delete())throw new IOException("Could not retire imported settings");
            new PrefStore(context); // Mirror only enable/autostart flags after commit.
        }catch(Exception failed){android.util.Log.e("WallPanelReceiver","Receiver import postponed: "+failed.getClass().getSimpleName());}
    }
    private ReceiverImport(){}
}
