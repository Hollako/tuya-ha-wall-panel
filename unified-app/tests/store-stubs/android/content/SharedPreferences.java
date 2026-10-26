package android.content;
import java.util.*;
/** In-memory Android facade for exercising production ConfigStore on the host JVM. */
public final class SharedPreferences {
    public final Map<String,Object> data=new HashMap<String,Object>();
    public boolean failCommit;
    public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
    public int getInt(String k,int d){return data.containsKey(k)?(Integer)data.get(k):d;}
    public long getLong(String k,long d){return data.containsKey(k)?(Long)data.get(k):d;}
    public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
    @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String>d){return data.containsKey(k)?(Set<String>)data.get(k):d;}
    public Editor edit(){return new Editor();}
    public final class Editor {
        private final Map<String,Object> changes=new HashMap<String,Object>();
        private final Set<String> removed=new HashSet<String>();
        public Editor putString(String k,String v){changes.put(k,v);return this;}
        public Editor putInt(String k,int v){changes.put(k,Integer.valueOf(v));return this;}
        public Editor putLong(String k,long v){changes.put(k,Long.valueOf(v));return this;}
        public Editor putBoolean(String k,boolean v){changes.put(k,Boolean.valueOf(v));return this;}
        public Editor putStringSet(String k,Set<String>v){changes.put(k,new HashSet<String>(v));return this;}
        public Editor remove(String k){removed.add(k);return this;}
        public void apply(){commit();}
        public boolean commit(){if(failCommit){failCommit=false;return false;}for(String k:removed)data.remove(k);data.putAll(changes);return true;}
    }
}
