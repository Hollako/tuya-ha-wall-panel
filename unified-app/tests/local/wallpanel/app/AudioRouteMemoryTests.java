package local.wallpanel.app;
import java.util.HashMap;
public final class AudioRouteMemoryTests {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static final class Saved implements AudioRouteMemory.Store {
        final HashMap<String,Boolean> values=new HashMap<String,Boolean>();boolean fail;
        public boolean contains(String key){return values.containsKey(key);}
        public boolean get(String key){return values.get(key);}
        public void save(String key,boolean value)throws Exception{if(fail)throw new Exception("Storage failure");values.put(key,value);}
    }
    static final class Board implements AudioRouteMemory.Board {
        boolean external,line,fail;int writes;
        public boolean external(){return external;}
        public void external(boolean value)throws Exception{if(fail)throw new Exception("Board rejected write");external=value;writes++;}
        public boolean line(){return line;}
        public void line(boolean value){line=value;writes++;}
    }
    public static void main(String[] args)throws Exception {
        Saved saved=new Saved();Board board=new Board();board.external=true;
        new AudioRouteMemory(saved,board).restore(true);check(board.writes==0&&saved.get(AudioRouteMemory.EXTERNAL)&&!saved.get(AudioRouteMemory.LINE));
        AudioRouteMemory memory=new AudioRouteMemory(saved,board);memory.rememberLine(true);memory.external(true);
        board.external=board.line=false;int before=board.writes;
        new AudioRouteMemory(saved,board).restore(true);check(board.external&&board.line&&board.writes==before+2);
        new AudioRouteMemory(saved,board).restore(true);check(board.writes==before+2);
        memory.external(false);memory.rememberLine(false);board.external=board.line=true;
        new AudioRouteMemory(saved,board).restore(true);check(!board.external&&!board.line);
        memory.rememberLine(true);board.line=false;new AudioRouteMemory(saved,board).restore(false);check(!board.line&&saved.get(AudioRouteMemory.LINE));
        new AudioRouteMemory(saved,board).restore(true);check(board.line);
        board.fail=true;boolean failed=false;try{memory.external(true);}catch(Exception expected){failed=true;}check(failed&&!saved.get(AudioRouteMemory.EXTERNAL));board.fail=false;
        saved.fail=true;failed=false;try{memory.rememberLine(false);}catch(Exception expected){failed=true;}check(failed&&saved.get(AudioRouteMemory.LINE));
        Saved fresh=new Saved();new AudioRouteMemory(fresh,board).restore(false);check(!fresh.contains(AudioRouteMemory.LINE));
        System.out.println("PASS: first-install adoption, cold-start restore of ON and OFF, idempotent restore, temporary-route isolation, board-write failure and storage-failure reporting.");
    }
}
