package local.wallpanel.app;

/** Immutable page routing. No network or Android dependencies. */
final class LocalMediaKnob {
    final boolean active;final int step;final String entity,title;
    LocalMediaKnob(boolean active,int step,String entity,String title){
        if(step<1||step>25||entity==null||title==null||title.length()>128||
            (active&&!entity.matches("media_player\\.[a-z0-9_]{1,230}")))throw new IllegalArgumentException("Invalid media route");
        this.active=active;this.step=step;this.entity=entity;this.title=text(title,32);
    }
    static LocalMediaKnob off(){return new LocalMediaKnob(false,5,"","");}
    String action(String event){
        if(!active)return null;
        if(event.equals("rotate_right"))return "up";
        if(event.equals("rotate_left"))return "down";
        if(event.equals("press"))return "mute";
        if(event.equals("long_press"))return "play_pause";
        return null;
    }
    static double volume(int index,int maximum,int step,boolean up){
        if(maximum<=0)return 0;
        int delta=Math.max(1,(int)Math.round(maximum*step/100.0));
        return Math.max(0,Math.min(maximum,index+(up?delta:-delta)))/(double)maximum;
    }
    static String text(String value,int maximum){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length()&&out.length()<maximum;i++){
            char c=value.charAt(i);
            if(c>=32&&c<=126||c>=160&&c<=255||c==0x2013||c==0x2014||c==0x2019||c==0x2022||c==0x2212)out.append(c);
            else if(c=='\n'||c=='\r')out.append(' ');
        }
        return out.toString();
    }
    String frame(double volume,boolean muted,String state,String track){
        String detail=muted?"":state;
        if(!track.isEmpty())detail+=(detail.isEmpty()?"":" / ")+track;
        return text(title,32)+"\n"+Math.round(volume*100)+"%\n"+text(detail,32);
    }
}
