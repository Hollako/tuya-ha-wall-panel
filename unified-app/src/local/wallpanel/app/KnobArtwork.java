package local.wallpanel.app;
import com.wallpanel.audioreceiver.ReceiverState;

/** Fixed private bitmap file; native IPC carries only its content identity. */
final class KnobArtwork {
    private final android.content.Context context;private NativeWorker previous;private String sent="";
    KnobArtwork(android.content.Context context){this.context=context;}
    synchronized void sync(NativeWorker worker,boolean show,String expected){
        if(worker==null||!worker.isRunning())return;
        ReceiverState state=ReceiverState.INSTANCE;String id;byte[] pixels;
        synchronized(state){id=state.getArtworkId();pixels=state.getKnobArtwork();}
        if(!show||!id.equals(expected)||pixels==null||pixels.length!=19200)id="";
        if(worker==previous&&id.equals(sent))return;
        try{
            if(!id.isEmpty()){
                android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(context.getFilesDir(),"knob-art.rgb"));java.io.FileOutputStream out=null;
                try{out=file.startWrite();out.write(id.getBytes("US-ASCII"));out.write(pixels);file.finishWrite(out);out=null;}finally{if(out!=null)file.failWrite(out);}
            }
            if(worker.send(KnobTopics.encode("artwork",id.isEmpty()?"OFF":id))){sent=id;previous=worker;}
        }catch(Exception ignored){}
    }
}
