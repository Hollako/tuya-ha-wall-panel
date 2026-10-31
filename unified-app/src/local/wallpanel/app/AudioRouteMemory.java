package local.wallpanel.app;

/** Last administrator choices, separate from temporary group/AirPlay routing. */
final class AudioRouteMemory {
    static final String EXTERNAL="external_speakers",LINE="line_input";
    interface Store {boolean contains(String key);boolean get(String key);void save(String key,boolean value)throws Exception;}
    interface Board {boolean external()throws Exception;void external(boolean value)throws Exception;boolean line()throws Exception;void line(boolean value)throws Exception;}
    private final Store store;private final Board board;
    AudioRouteMemory(Store store,Board board){this.store=store;this.board=board;}
    void restore(boolean restoreSource)throws Exception {
        boolean external=board.external();
        if(store.contains(EXTERNAL)){boolean wanted=store.get(EXTERNAL);if(external!=wanted)board.external(wanted);}
        else store.save(EXTERNAL,external);
        if(!restoreSource)return;
        boolean line=board.line();
        if(store.contains(LINE)){boolean wanted=store.get(LINE);if(line!=wanted)board.line(wanted);}
        else store.save(LINE,line);
    }
    void external(boolean value)throws Exception {board.external(value);store.save(EXTERNAL,value);}
    void rememberLine(boolean value)throws Exception {store.save(LINE,value);}
}
