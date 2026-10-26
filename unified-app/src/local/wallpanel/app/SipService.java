package local.wallpanel.app;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Optional SIP owner; ringtone and call presentation do not depend on a dashboard activity. */
public final class SipService extends Service {
    volatile SipEngine engine;volatile SipEngine.State callState=SipEngine.State.IDLE;volatile boolean registered;volatile String caller="",detail="Starting SIP";
    volatile SipRtpSession activeRtp;volatile boolean microphoneMuted,doorBusy;volatile String doorStatus="";private volatile long callToken;private long lastDoorRequest;private final ExecutorService doorWork=Executors.newSingleThreadExecutor();
    private SipStore store;private final Handler main=new Handler(Looper.getMainLooper());private SipAudioLease audio;private SipRinger ring;private PowerManager.WakeLock wake;private volatile boolean destroyed;
    public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();NetworkRouter.get(this);store=new SipStore(this);SipRuntime.current=this;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!store.enabled()||!store.configured()){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Notification.Builder notification;
        if(Build.VERSION.SDK_INT>=26){manager.createNotificationChannel(new NotificationChannel("wallpanel_sip","Wall Panel intercom",NotificationManager.IMPORTANCE_LOW));notification=new Notification.Builder(this,"wallpanel_sip");}else notification=new Notification.Builder(this);
        PendingIntent settings=PendingIntent.getActivity(this,42,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(42,notification.setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Tuya HA Wall Panel intercom").setContentText("SIP service enabled").setOngoing(true).setContentIntent(settings).build());
        if(engine!=null)return START_STICKY;
        try{store.load().validate();wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:sip");wake.setReferenceCounted(false);wake.acquire();
            engine=new SipEngine(new SipEngine.NetworkHost(){
                public String networkKey()throws Exception{return NetworkRouter.get(SipService.this).key(true);}
                public String networkAddress(SipConfig c)throws Exception{NetworkRouter r=NetworkRouter.get(SipService.this);android.net.Network n=r.require(true);if(c.localAddress.length()>0&&"auto".equals(r.load().intercom)){if(!r.owns(n,c.localAddress))throw new java.io.IOException("Clear SIP local-IP override or select its interface");return c.localAddress;}return r.ipv4(n);}
                public SipConfig settings()throws Exception{return store.load();}
                public SipEngine.Media media(final String local,final SipConfig config){return new SipEngine.Media(){private volatile SipRtpSession rtp;
                    public void start(SipSdp offered)throws Exception{if(destroyed)throw new IllegalStateException("SIP stopped");onMain(new Runnable(){public void run(){try{if(destroyed)throw new IllegalStateException("SIP stopped");if(audio==null)audio=new SipAudioLease(SipService.this);stopRing();audio.answer(config);}catch(Exception e){throw new IllegalStateException(e);}}});if(destroyed)throw new IllegalStateException("SIP stopped");SipRtpSession created=new SipRtpSession(local,config,new SipRtpSession.Failure(){public void failed(){SipEngine e=engine;if(e!=null)e.mediaFailed();}});final NetworkRouter routes=NetworkRouter.get(SipService.this);final android.net.Network selected=routes.require(true);created.networkBinding(new SipRtpSession.NetworkBinding(){public void bind(java.net.DatagramSocket socket)throws java.io.IOException{selected.bindSocket(socket);}public java.net.InetAddress resolve(String host)throws java.io.IOException{return routes.resolve(selected,host);}});rtp=created;activeRtp=created;created.microphoneMuted(microphoneMuted);if(destroyed){created.close();throw new IllegalStateException("SIP stopped");}created.start(offered);if(destroyed){created.close();throw new IllegalStateException("SIP stopped");}}
                    public void update(SipSdp offered)throws Exception{if(rtp!=null)rtp.update(offered);}
                    public synchronized void close(){SipRtpSession current=rtp;rtp=null;if(current!=null)current.close();if(activeRtp==current){activeRtp=null;microphoneMuted=false;}}
                };}
                public void state(final SipEngine.State next,final boolean online,final String from,final String status){
                    if(destroyed)return;boolean changed=callState!=next;if(next==SipEngine.State.RINGING&&callState!=next){callToken++;microphoneMuted=false;doorStatus="";}callState=next;registered=online;caller=from;detail=status;store.status(status);record();if(changed)main.post(new Runnable(){public void run(){if(destroyed||callState!=next)return;present(next);}});
                }
            });engine.start();return START_STICKY;
        }catch(Exception failed){store.status("SIP could not start ("+failed.getClass().getSimpleName()+")");stopSelf();return START_NOT_STICKY;}
    }
    void toggleMicrophone(){SipRtpSession r=activeRtp;if(callState!=SipEngine.State.IN_CALL||r==null)return;microphoneMuted=!microphoneMuted;r.microphoneMuted(microphoneMuted);}
    void openDoor(final int door){
        if(destroyed||doorBusy||(callState!=SipEngine.State.RINGING&&callState!=SipEngine.State.IN_CALL)||SystemClock.elapsedRealtime()-lastDoorRequest<2000)return;
        try{final SipConfig config=store.load();if(!config.doorEnabled(door))return;final long token=callToken;doorBusy=true;lastDoorRequest=SystemClock.elapsedRealtime();doorStatus="Opening Door "+door+"...";
            doorWork.execute(new Runnable(){public void run(){String result;try{final android.net.Network doorNetwork=NetworkRouter.get(SipService.this).require(true);IntercomDoor.open(config,door,new IntercomDoor.Guard(){public boolean allowed(){return !destroyed&&callToken==token&&(callState==SipEngine.State.RINGING||callState==SipEngine.State.IN_CALL);}},new IntercomDoor.Connections(){public java.net.HttpURLConnection open(java.net.URL url)throws java.io.IOException{return (java.net.HttpURLConnection)doorNetwork.openConnection(url);}});result="Door "+door+" open command accepted";}catch(Exception failed){result="Door "+door+" request not confirmed";}final String text=result;main.post(new Runnable(){public void run(){doorBusy=false;if(callToken==token)doorStatus=text;}});}});
        }catch(Exception failed){doorBusy=false;doorStatus="Check door settings";}
    }
    private void onMain(Runnable task)throws Exception{if(Looper.myLooper()==Looper.getMainLooper()){task.run();return;}FutureTask<Object> future=new FutureTask<Object>(task,null);main.post(future);try{future.get(3,TimeUnit.SECONDS);}finally{future.cancel(false);}}
    private void present(SipEngine.State state){
        if(state==SipEngine.State.RINGING||state==SipEngine.State.IN_CALL){
            try{if(audio==null)audio=new SipAudioLease(this);}catch(Exception failed){store.status("Intercom audio routing failed");SipRuntime.hangup();return;}
            if(state==SipEngine.State.RINGING){stopRing();try{ring=new SipRinger(this,store.load());}catch(Exception failed){store.status("Incoming call; ringtone unavailable");}}else stopRing();
            startActivity(new Intent(this,IntercomCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }else{stopRing();if(audio!=null){audio.close();audio=null;}}
    }
    private void stopRing(){if(ring!=null)ring.close();ring=null;}
    private void record(){try{JSONObject state=new JSONObject().put("v",1).put("registered",registered).put("call_state",callState.name()).put("detail",detail).put("module_enabled",store.enabled()).put("station_brand",store.load().stationBrand);FileOutputStream out=openFileOutput("sip-runtime-status.json",MODE_PRIVATE);try{out.write(state.toString().getBytes("UTF-8"));}finally{out.close();}}catch(Exception ignored){}}
    @Override public void onDestroy(){destroyed=true;doorWork.shutdownNow();main.removeCallbacksAndMessages(null);SipEngine e=engine;engine=null;if(e!=null)e.close();stopRing();if(audio!=null){audio.close();audio=null;}if(wake!=null&&wake.isHeld())wake.release();if(SipRuntime.current==this)SipRuntime.current=null;registered=false;callState=SipEngine.State.IDLE;store.status("SIP stopped");record();stopForeground(true);super.onDestroy();}
}
