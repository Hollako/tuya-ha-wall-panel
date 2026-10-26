package local.wallpanel.app;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** Network-pinned RTSP/TCP with in-band H264 headers and platform surface decoding. */
final class IntercomVideo extends FrameLayout implements SurfaceHolder.Callback {
    private final SurfaceView surface;private final TextView message;private final Handler main=new Handler();private final Context context;
    private volatile RtspVideoSession session;private volatile int generation;private boolean ready,active;private int attempts,width,height;private String url="",network="";
    IntercomVideo(Context c){super(c);context=c;setBackgroundColor(0xff000000);surface=new SurfaceView(c);surface.getHolder().addCallback(this);addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));message=new TextView(c);message.setGravity(Gravity.CENTER);message.setTextColor(-1);message.setTextSize(16);addView(message,new FrameLayout.LayoutParams(-1,-1));}
    void show(SipConfig config){boolean on=config.videoEnabled&&config.videoUrl.length()>0;String selected=NetworkRouter.get(context).key(true);if(active==on&&url.equals(config.videoUrl)&&network.equals(selected))return;stop();url=config.videoUrl;network=selected;active=on;attempts=0;setVisibility(on?VISIBLE:GONE);if(on){message.setText("Connecting video...");if(ready)connect();}}
    public void surfaceCreated(SurfaceHolder holder){ready=true;if(active)connect();}
    public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){}
    public void surfaceDestroyed(SurfaceHolder holder){ready=false;stopPlayer();}
    private void connect(){if(!active||!ready||session!=null)return;final int token=++generation;
        try{NetworkRouter routes=NetworkRouter.get(context);if(routes.selected(true)==null){message.setText("Waiting for intercom network...");return;}attempts++;
            final AvcSurfaceDecoder decoder=new AvcSurfaceDecoder(surface.getHolder().getSurface(),new AvcSurfaceDecoder.Events(){public void size(final int w,final int h){main.post(new Runnable(){public void run(){if(active&&generation==token){width=w;height=h;fit();record("decoding",null);}}});}public void rendered(){main.post(new Runnable(){public void run(){if(active&&generation==token){main.removeCallbacksAndMessages(null);message.setText("");record("playing",null);}}});}});
            final RtspVideoSession client=new RtspVideoSession(url,routes.intercomSockets(),new RtspVideoSession.Listener(){public void phase(final String name){main.post(new Runnable(){public void run(){if(generation==token)record(name,null);}});}public void parameters(byte[] sps,byte[] pps)throws Exception{decoder.parameters(sps,pps);}public void frame(byte[] bytes,long pts)throws Exception{decoder.frame(bytes,pts);}});session=client;
            new Thread(new Runnable(){public void run(){try{client.run();}catch(final Exception error){main.post(new Runnable(){public void run(){failed(token,error);}});}finally{decoder.close();}}},"wallpanel-rtsp-video").start();
            main.postDelayed(new Runnable(){public void run(){failed(token,new java.io.IOException("Video startup timeout"));}},15000);
        }catch(Exception error){failed(token,error);}
    }
    private void failed(int token,Throwable error){if(!active||token!=generation)return;record("error",error);stopPlayer();message.setText("Video unavailable. Audio call is still available.");if(attempts<3)main.postDelayed(new Runnable(){public void run(){if(active&&ready)connect();}},1500);}
    private void stopPlayer(){generation++;main.removeCallbacksAndMessages(null);RtspVideoSession old=session;session=null;if(old!=null)old.close();}
    void stop(){active=false;stopPlayer();}
    private void record(String phase,Throwable error){try{JSONObject d=new JSONObject().put("phase",phase).put("width",width).put("height",height).put("network",network);if(error!=null){StringBuilder chain=new StringBuilder();for(int n=0;error!=null&&n<4;n++,error=error.getCause()){chain.append(error.getClass().getSimpleName());StackTraceElement[] frames=error.getStackTrace();if(frames.length>0)chain.append(" at ").append(frames[0].getClassName()).append(":").append(frames[0].getLineNumber());chain.append("; ");}d.put("error_classes",chain.toString());}FileOutputStream out=context.openFileOutput("video-runtime-status.json",Context.MODE_PRIVATE);try{out.write(d.toString().getBytes("UTF-8"));}finally{out.close();}}catch(Exception ignored){}}
    private void fit(){if(width<=0||height<=0||getWidth()==0||getHeight()==0)return;float scale=Math.min((float)getWidth()/width,(float)getHeight()/height);surface.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),Gravity.CENTER));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}
}
