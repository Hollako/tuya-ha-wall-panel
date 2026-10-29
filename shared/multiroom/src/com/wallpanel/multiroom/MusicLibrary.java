package com.wallpanel.multiroom;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only Android audio index. Clients receive media IDs, never filesystem paths. */
public final class MusicLibrary {
 private final Context context;private List<Track> cached;private long scanned;private boolean truncated;
 private static final int MAX_TRACKS=10000;
 private static class Track {String id,title,artist,album,folder,folderName;long duration;}
 public MusicLibrary(Context context){this.context=context.getApplicationContext();}
 public static String permission(){return Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_AUDIO":"android.permission.READ_EXTERNAL_STORAGE";}
 public static boolean allowed(Context context){return context.checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED;}
 public static String trackId(String value){if(value==null||!value.matches("audio-[1-9][0-9]{0,17}"))throw new IllegalArgumentException("Choose a valid local audio track");return value;}
 public static String folderId(String value){if(!"root".equals(value)&&!"all".equals(value)&&!value.matches("folder-[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid music folder");return value;}
 private static String text(Cursor c,int column){String value=c.isNull(column)?"":c.getString(column);return value.equals("<unknown>")?"":value;}
 private static String key(String path)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder("folder-");for(int i=0;i<16;i++)key.append(String.format(Locale.ROOT,"%02x",digest[i]&255));return key.toString();}
 private synchronized List<Track> scan(boolean refresh)throws Exception{
  if(!allowed(context))throw new SecurityException("Allow music access in the panel app's group settings");
  if(!refresh&&cached!=null&&SystemClock.elapsedRealtime()-scanned<15000)return cached;
  ArrayList<Track> tracks=new ArrayList<>();String[] fields={"_id","title","artist","album","duration","_data","_display_name"};truncated=false;
  try(Cursor cursor=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,fields,"_size > 0 AND mime_type LIKE ?",new String[]{"audio/%"},"title COLLATE NOCASE ASC, _id ASC")){
   if(cursor!=null)while(cursor.moveToNext()){
    if(tracks.size()>=MAX_TRACKS){truncated=true;break;}
    Track track=new Track();track.id="audio-"+cursor.getLong(0);track.title=text(cursor,1);if(track.title.isEmpty())track.title=text(cursor,6);track.artist=text(cursor,2);track.album=text(cursor,3);track.duration=Math.max(0,cursor.getLong(4));
    String path=text(cursor,5);File parent=path.isEmpty()?null:new File(path).getParentFile();String directory=parent==null?"Unknown folder":parent.getAbsolutePath();track.folder=key(directory);
    String root=Environment.getExternalStorageDirectory().getAbsolutePath()+"/";track.folderName=directory.startsWith(root)?directory.substring(root.length()):parent==null?"Other audio":parent.getName();if(track.folderName.isEmpty())track.folderName="Music";
    tracks.add(track);
   }
  }
  cached=tracks;scanned=SystemClock.elapsedRealtime();return tracks;
 }
 public synchronized JSONObject browse(String folder,int offset,int limit,String query,boolean refresh)throws Exception{
  folderId(folder);if(offset<0||offset>MAX_TRACKS||limit<1||limit>200||query.length()>128)throw new IllegalArgumentException("Invalid library page");
  JSONArray folders=new JSONArray(),songs=new JSONArray();JSONObject result=new JSONObject().put("folder",folder).put("offset",offset).put("folders",folders).put("tracks",songs);
  if(!allowed(context))return result.put("permissionRequired",true).put("message","Allow music access in the panel app's group settings").put("total",0).put("nextOffset",JSONObject.NULL);
  List<Track> all=scan(refresh);String search=query.trim().toLowerCase(Locale.ROOT);ArrayList<Track> selected=new ArrayList<>();TreeMap<String,JSONObject> names=new TreeMap<>();
  for(Track track:all){
   if(folder.equals("root")&&search.isEmpty()){
    String nameKey=track.folderName+"|"+track.folder;JSONObject info=names.get(nameKey);if(info==null){info=new JSONObject().put("id",track.folder).put("name",track.folderName).put("count",0);names.put(nameKey,info);}info.put("count",info.getInt("count")+1);
   }else if((folder.equals("all")||folder.equals("root")||folder.equals(track.folder))&&(search.isEmpty()||(track.title+" "+track.artist+" "+track.album).toLowerCase(Locale.ROOT).contains(search)))selected.add(track);
  }
  ArrayList<JSONObject> folderList=new ArrayList<>(names.values());for(int i=offset;i<Math.min(folderList.size(),offset+limit);i++)folders.put(folderList.get(i));
  for(int i=offset;i<Math.min(selected.size(),offset+limit);i++){Track track=selected.get(i);songs.put(new JSONObject().put("id",track.id).put("title",track.title).put("artist",track.artist).put("album",track.album).put("durationMs",track.duration));}
  int total=folder.equals("root")&&search.isEmpty()?all.size():selected.size();
  int count=folder.equals("root")&&search.isEmpty()?folderList.size():selected.size();return result.put("permissionRequired",false).put("total",total).put("truncated",truncated).put("nextOffset",offset+limit<count?offset+limit:JSONObject.NULL);
 }
 public JSONObject resolve(String id)throws Exception{
  trackId(id);if(!allowed(context))throw new IllegalStateException("Allow music access on the source panel first");
  Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,Long.parseLong(id.substring(6)));
  try(Cursor cursor=context.getContentResolver().query(uri,new String[]{"title","_display_name","mime_type"},null,null,null)){
   if(cursor==null||!cursor.moveToFirst()||!text(cursor,2).startsWith("audio/"))throw new IllegalArgumentException("Track is no longer available; refresh the library");
   String title=text(cursor,0);if(title.isEmpty())title=text(cursor,1);
   return new JSONObject().put("source",uri.toString()).put("title",title);
  }
 }
}
