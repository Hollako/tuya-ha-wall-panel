package android.content;
public final class Context {
    public static final int MODE_PRIVATE=0;
    private final SharedPreferences prefs=new SharedPreferences();
    public java.io.File getFilesDir(){return new java.io.File("no-such-isolated-test-files");}
    public SharedPreferences getSharedPreferences(String name,int mode){if(!"wall_panel".equals(name)||mode!=MODE_PRIVATE)throw new AssertionError();return prefs;}
}
