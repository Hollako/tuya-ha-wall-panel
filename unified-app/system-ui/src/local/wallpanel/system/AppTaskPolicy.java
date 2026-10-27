package local.wallpanel.system;
/** Pure task boundary rules shared by the root monitor and host tests. */
public final class AppTaskPolicy {
    public static String target(String value){
        if(value==null||value.length()>160||!value.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")||value.equals("local.wallpanel.app"))throw new IllegalArgumentException("Invalid app target");
        return value;
    }
    public static boolean allowed(String foreground,String target){
        return target.equals(foreground)||"local.wallpanel.app".equals(foreground)||
            "com.android.permissioncontroller".equals(foreground)||"com.google.android.permissioncontroller".equals(foreground)||
            "com.android.packageinstaller".equals(foreground)||"com.google.android.packageinstaller".equals(foreground);
    }
    public static String immersive(String previous,String target){
        target(target);String text=previous==null?"":previous;
        String[] entries=text.split(":");StringBuilder out=new StringBuilder();boolean found=false;
        for(String entry:entries){
            if(entry.isEmpty())continue;
            if(entry.startsWith("immersive.full=")){
                StringBuilder list=new StringBuilder();boolean included=false;
                for(String item:entry.substring(15).split(",")){
                    if(item.isEmpty()||item.equals("-"+target))continue;
                    if(item.equals(target)||item.equals("*"))included=true;
                    if(list.length()>0)list.append(',');list.append(item);
                }
                if(!included){if(list.length()>0)list.append(',');list.append(target);}
                entry="immersive.full="+list;found=true;
            }
            if(out.length()>0)out.append(':');out.append(entry);
        }
        if(!found){if(out.length()>0)out.append(':');out.append("immersive.full=").append(target);}
        return out.toString();
    }
    private AppTaskPolicy(){}
}
