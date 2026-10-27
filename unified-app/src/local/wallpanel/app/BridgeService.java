package local.wallpanel.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Foreground owner of the app's optional buttons/relay worker and MQTT transport. */
public final class BridgeService extends Service implements NativeWorker.Listener {
    static boolean receiverStarted(){BridgeService s=instance;return s!=null&&s.audioEngine!=null&&s.audioEngine.receiverStarted();}
    static void receiverEnded(){BridgeService s=instance;if(s!=null&&s.audioEngine!=null)s.audioEngine.receiverEnded();}
    private static volatile BridgeService instance;private long intercomWake;
    static Boolean beginIntercom(){BridgeService s=instance;if(s==null)return null;Boolean power=s.audioEngine==null?null:Boolean.valueOf(s.audioEngine.powered());if(s.audioEngine!=null)s.audioEngine.beginIntercom();if(s.worker!=null&&s.config!=null){if(s.config.selected.contains("amplifier"))s.worker.send("audio power 1");if(s.config.selected.contains("display"))s.worker.send("display power 1");}return power;}
    static void endIntercom(Boolean power){BridgeService s=instance;if(s==null)return;if(s.audioEngine!=null)s.audioEngine.endIntercom();if(power!=null&&s.worker!=null&&s.config!=null&&s.config.selected.contains("amplifier"))s.worker.send("audio power "+(power.booleanValue()?1:0));}
    public static final String STOP="local.wallpanel.app.STOP_HARDWARE";
    public static final String START="local.wallpanel.app.START_HARDWARE";
    private ConfigStore store;
    private volatile PanelConfig config;
    private volatile NativeWorker worker;
    private volatile MqttSession mqtt;
    private volatile JSONObject state;
    private volatile ManagementRuntime management;
    private volatile LocalMediaKnob localMedia=LocalMediaKnob.off();
    private NativeWorker muteWorker;private Boolean muteSent;
    private NativeWorker localFrameWorker;private String localFrameSent="";
    private final ThreadLocal<Boolean> nativeCallback=new ThreadLocal<Boolean>();
    private final java.util.concurrent.ThreadPoolExecutor feedback=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(128),new java.util.concurrent.ThreadFactory(){public Thread newThread(Runnable work){return new Thread(work,"wallpanel-native-feedback");}});
    private volatile AudioEngine audioEngine;
    private volatile JSONObject audioState;
    private volatile boolean audioOnline;
    private int audioGeneration;
    private final java.util.concurrent.atomic.AtomicInteger audioRequests=new java.util.concurrent.atomic.AtomicInteger();
    private volatile int[][] bindings=new int[4][2];
    private volatile boolean stopping,ready,seenOnline,bindingsKnown,mqttOnline,verified;
    private long started;
    private boolean initialized;private volatile int workerGeneration;private int retryDelay=5000;private PowerManager.WakeLock cpu;
    private MqttSession presenceSession;private boolean presenceOnline;
    private String recoveryReason;
    private java.util.Timer transportDeadline;
    private final Handler handler=new Handler();
    private final Runnable watchdog=new Runnable(){public void run(){
        if(stopping)return;MqttSession session=mqtt;if(session!=null)session.checkWriteDeadline();
        boolean complete=ready&&(!config.selected.contains("audio")||audioOnline);
        if(worker!=null&&ActivationPolicy.timedOut(verified,complete,mqttOnline,android.os.SystemClock.elapsedRealtime()-started))failed("Hardware startup timed out");
        handler.postDelayed(this,1000);
    }};
    private String haNetworkKey="";
    private final NetworkRouter.Listener networkChanges=new NetworkRouter.Listener(){public void changed(){NetworkRouter router=NetworkRouter.get(BridgeService.this);String next=router.key(false);if(!next.equals(haNetworkKey)){haNetworkKey=next;MqttSession current=mqtt;if(current!=null)current.close();}}};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();NetworkRouter router=NetworkRouter.get(this);haNetworkKey=router.key(false);router.add(networkChanges);store=new ConfigStore(this);loadLocalMedia();instance=this;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){publish("/app/availability","offline",true);store.setRuntimeEnabled(false);store.runtimeStatus("Hardware stopped by administrator");stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&START.equals(intent.getAction()))store.setRuntimeEnabled(true);
        if(initialized){
            try{PanelConfig next=store.load();
                if(!sameConnection(config,next))throw new IllegalArgumentException("Stop hardware before changing its connection/profile");
                boolean changed=config.selected.contains("buttons")!=next.selected.contains("buttons")||config.selected.contains("relays")!=next.selected.contains("relays");
                boolean knobChanged=config.selected.contains("knob_input")!=next.selected.contains("knob_input")||config.selected.contains("knob_display")!=next.selected.contains("knob_display");
                boolean audioChanged=config.selected.contains("audio")!=next.selected.contains("audio")||config.selected.contains("amplifier")!=next.selected.contains("amplifier");
                boolean displayChanged=config.selected.contains("display")!=next.selected.contains("display");
                if(changed||knobChanged||audioChanged||displayChanged)started=android.os.SystemClock.elapsedRealtime();
                config=next;NativeWorker active=worker;if(active!=null){if(changed)active.send("modules "+(next.selected.contains("buttons")?1:0)+" "+(next.selected.contains("relays")?1:0));
                if(knobChanged)active.send("knobmodules "+(next.selected.contains("knob_input")?1:0)+" "+(next.selected.contains("knob_display")?1:0));
                if(audioChanged){stopAudio();active.send("audiomodules "+(next.selected.contains("audio")?1:0)+" "+(next.selected.contains("amplifier")?1:0));}
                if(displayChanged)active.send("display enabled "+(next.selected.contains("display")?1:0));}
            }catch(Exception e){store.runtimeStatus("Stop hardware before changing connection/profile");}
            return START_STICKY;
        }
        try{config=store.load();config.validate();config.validateBroker();HardwareTopics.base(config.mqttBase);if(!"g10".equals(config.profile))throw new IllegalArgumentException("Select G10 first");}
        catch(Exception e){store.runtimeStatus("Cannot start hardware: check saved profile and MQTT settings");store.setRuntimeEnabled(false);stopSelf();return START_NOT_STICKY;}
        if(!store.runtimeEnabled()){stopSelf();return START_NOT_STICKY;}
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder notice;
        if(Build.VERSION.SDK_INT>=26){notifications.createNotificationChannel(new NotificationChannel("wallpanel_hardware","Wall Panel hardware",NotificationManager.IMPORTANCE_LOW));notice=new Notification.Builder(this,"wallpanel_hardware");}else notice=new Notification.Builder(this);
        PendingIntent setup=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(41,notice.setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Tuya HA Wall Panel").setContentText("Wall Panel hardware and audio").setOngoing(true).setContentIntent(setup).build());
        stopping=false;verified=store.runtimeVerified();started=android.os.SystemClock.elapsedRealtime();store.runtimeStatus("Starting hardware worker…");
        initialized=true;PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);cpu=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"wallpanel:hardware");cpu.setReferenceCounted(false);cpu.acquire();
        transportDeadline=new java.util.Timer("wallpanel-transport-deadline",true);transportDeadline.schedule(new java.util.TimerTask(){public void run(){MqttSession current=mqtt;if(current!=null)current.checkWriteDeadline();}},1000,1000);
        startWorker();handler.postDelayed(watchdog,1000);
        management=new ManagementRuntime(new ManagementRuntime.Host(){
            public boolean online(){return !stopping&&mqttOnline;}
            public boolean owned(){NativeWorker active=worker;JSONObject current=state;return !stopping&&active!=null&&active.isRunning()&&current!=null&&current.optBoolean("management_owned",false);}
            public int workerPid(){JSONObject current=state;return current==null?0:current.optInt("worker_pid",0);}
            public Object connection(){return mqtt;}
            public PanelConfig config(){return config;}
            public void publish(String suffix,String payload,boolean retained){BridgeService.this.publish(suffix,payload,retained);}
        },new java.io.File(getFilesDir(),"g10-runtime/wallpanel-capture"));management.start();
        new Thread(new Runnable(){public void run(){networkLoop();}},"wallpanel-mqtt-runtime").start();
        new Thread(new Runnable(){public void run(){audioPublisher();}},"wallpanel-audio-state").start();return START_STICKY;
    }
    private void networkLoop(){
        int retry=1000;
        while(!stopping){MqttSession session=new MqttSession(NetworkRouter.get(this).haSockets());mqtt=session;
            try{
                String base=config.mqttBase;
                String client="wp-"+config.deviceId.replace("wp_","");if(client.length()>23)client=client.substring(0,23);
                session.open(config,client,base+"/availability",new String[]{base+"/relay/+/set",base+"/button/+/binding/set",base+"/app/command/set",base+"/app/dashboard/set",base+"/knob/display/+/set",base+"/knob/media/set",base+"/knob/led/color/set",base+"/audio/command/set",base+"/audio/hardware_level/set",base+"/device/command/set",base+"/device/brightness/set",base+"/display/policy/+/set","homeassistant/status"});
                if(stopping){session.close();break;}
                mqttOnline=true;refreshRuntimeStatus();synchronizeState();retry=1000;
                while(!stopping){MqttSession.Message m=session.next();
                    if(m!=null){
                        if(m.topic.equals("homeassistant/status")&&m.payload.equals("online"))synchronizeState();
                        else if(m.topic.equals(base+"/app/dashboard/set"))applyDashboard(m.payload);
                        else if(m.topic.equals(base+"/knob/media/set")){if(!m.retained)configureLocalMedia(m.payload);}
                        else if(m.topic.equals(base+"/audio/command/set")){if(!m.retained)audioCommand(m.payload);}
                        else if(m.topic.equals(base+"/audio/hardware_level/set")){
                            String op=AudioPolicy.level(m.payload);NativeWorker active=worker;if(!m.retained&&config.selected.contains("amplifier")&&op!=null&&active!=null)active.send(op);
                        }
                        else{String operation=HardwareTopics.command(config,m);if(operation==null)operation=KnobTopics.command(config,m);if(operation==null)operation=DisplayTopics.command(config,m);if(operation==null)operation=ManagementPolicy.command(config,m);
                            if("OPEN_SETUP".equals(operation))startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            else if("RELOAD_DASHBOARD".equals(operation))DashboardActivity.reloadFromRemote(this);
                            else if("RESTART_APP".equals(operation)){
                                AppRestartReceiver.request(this,new Runnable(){public void run(){publish("/app/availability","offline",true);}});
                            }
                            else if("SCREENSHOT".equals(operation)){ManagementRuntime current=management;if(current!=null)current.screenshot();}
                            else if(operation!=null&&(operation.startsWith("knob frame ")||operation.startsWith("knob media_frame "))){
                                final String frame=operation;handler.post(new Runnable(){public void run(){NativeWorker active=worker;if(!localMedia.active&&active!=null&&active.isRunning())active.send(frame);}});
                            }
                            else if(operation!=null&&worker!=null&&(operation.startsWith("bind ")||operation.startsWith("knob ")||operation.startsWith("display ")||ready))worker.send(operation);
                        }
                    }
                    Thread.sleep(50);
                }
            }catch(Exception ignored){}finally{session.close();if(mqtt==session)mqtt=null;mqttOnline=false;refreshRuntimeStatus();}
            if(!stopping)try{Thread.sleep(retry);retry=Math.min(retry*2,30000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
    }
    private void publish(final String suffix,final String value,final boolean retained){
        final MqttSession session=mqtt;if(session==null||!mqttOnline)return;
        if(Boolean.TRUE.equals(nativeCallback.get())||Thread.holdsLock(this)){
            final long at=SystemClock.elapsedRealtime();
            try{feedback.execute(new Runnable(){public void run(){
                if(stopping||mqtt!=session||!mqttOnline||!retained&&SystemClock.elapsedRealtime()-at>1500)return;
                try{session.publish(config.mqttBase+suffix,value,retained);}catch(Exception e){session.close();}
            }});}catch(java.util.concurrent.RejectedExecutionException ignored){}
            return;
        }
        try{session.publish(config.mqttBase+suffix,value,retained);}catch(Exception e){session.close();}
    }
    private void dashboardState(){try{JSONObject ack=new JSONObject();ack.put("v",1);ack.put("url",store.dashboardSettings().dashboard);publish("/app/dashboard/state",ack.toString(),true);}catch(JSONException ignored){}}
    private void applyDashboard(String payload){
        try{JSONObject request=new JSONObject(payload);if(request.getInt("v")!=1)throw new IllegalArgumentException();
            store.applyDashboard(request.getString("url"));dashboardState();
        }catch(Exception e){try{JSONObject error=new JSONObject();error.put("v",1);error.put("error","Dashboard URL rejected or could not be saved");publish("/app/dashboard/state",error.toString(),true);}catch(JSONException ignored){}}
    }
    private synchronized void synchronizeState(){
        NativeWorker active=worker;JSONObject current=state;if(current==null||active==null||!active.isRunning())return;publishState(current);
        int[][] copy=bindings;if(bindingsKnown)for(int b=0;b<4;b++)publish("/button/"+(b+1)+"/binding/state",copy[b][0]+","+copy[b][1],true);
        active.send("knob_status");active.send("audio_status");active.send("display status 0");active.send("management status 0");dashboardState();
    }
    private void loadLocalMedia(){
        android.content.SharedPreferences prefs=getSharedPreferences("local_media_knob",0);
        try{localMedia=new LocalMediaKnob(prefs.getBoolean("active",false),prefs.getInt("step",5),prefs.getString("entity",""),prefs.getString("title",""));}
        catch(Exception ignored){localMedia=LocalMediaKnob.off();}
    }
    private void saveLocalMedia(LocalMediaKnob route){
        getSharedPreferences("local_media_knob",0).edit().putBoolean("active",route.active).putInt("step",route.step).putString("entity",route.entity).putString("title",route.title).apply();
    }
    private void configureLocalMedia(String payload){
        try{
            if(payload.length()>1024)return;JSONObject data=new JSONObject(payload);
            if(data.getInt("v")!=1)return;
            final LocalMediaKnob next=new LocalMediaKnob(data.getBoolean("active"),data.getInt("step"),data.getString("entity"),data.getString("title"));
            handler.post(new Runnable(){public void run(){
                LocalMediaKnob old=localMedia;
                if(old.active!=next.active||old.step!=next.step||!old.entity.equals(next.entity)||!old.title.equals(next.title)){localMedia=next;localFrameSent="";saveLocalMedia(next);}
                drawLocalMedia();
            }});
        }catch(Exception ignored){}
    }
    private boolean localMediaEvent(final String event){
        final LocalMediaKnob route=localMedia;
        if(!route.active)return false;
        // Navigation remains HA-owned. Keep the local route until HA actually
        // selects another page, including when HA is offline or this is the only page.
        if(event.equals("double_press"))return false;
        final String action=route.action(event);final AudioEngine engine=audioEngine;
        if(action==null||engine==null||!audioOnline||!config.selected.contains("audio"))return false;
        final long at=SystemClock.elapsedRealtime();
        handler.post(new Runnable(){public void run(){
            if(!stopping&&localMedia==route&&audioEngine==engine&&config.selected.contains("knob_input")&&SystemClock.elapsedRealtime()-at<500)engine.knob(action,route.step);
        }});
        return true;
    }
    private void syncKnobMute(){
        NativeWorker active=worker;JSONObject audio=audioState;
        if(active==null||!active.isRunning()||audio==null||!config.selected.contains("knob_display"))return;
        boolean muted=audio.optBoolean("muted",false);
        if(active!=muteWorker||muteSent==null||muteSent.booleanValue()!=muted){
            try{if(active.send(KnobTopics.encode("speaker_mute",muted?"ON":"OFF"))){muteWorker=active;muteSent=Boolean.valueOf(muted);}}catch(Exception ignored){}
        }
    }
    private void drawLocalMedia(){
        LocalMediaKnob route=localMedia;JSONObject audio=audioState;NativeWorker active=worker;
        if(!route.active||audio==null||active==null||!active.isRunning()||config==null||!config.selected.contains("knob_display")||!config.selected.contains("audio"))return;
        try{
            String frame=KnobTopics.encode("media_frame",route.frame(audio.optDouble("volume",0),audio.optBoolean("muted",false),audio.optString("state","idle"),audio.optString("title",""))+"\n"+(audio.optBoolean("muted",false)?"ON":"OFF"));
            if(active!=localFrameWorker||!frame.equals(localFrameSent))if(active.send(frame)){localFrameWorker=active;localFrameSent=frame;}
        }catch(Exception ignored){}
    }
    private void audioCommand(final String raw){
        NativeWorker active=worker;if(raw.length()>4096||!config.selected.contains("audio")||active==null||!active.isRunning()||!audioOnline)return;
        try{if(!AudioPolicy.command(new JSONObject(raw).getString("command")))return;}catch(JSONException e){return;}
        if(audioRequests.incrementAndGet()>32){audioRequests.decrementAndGet();return;}
        final long at=SystemClock.elapsedRealtime();handler.post(new Runnable(){public void run(){try{
            NativeWorker active=worker;AudioEngine engine=audioEngine;
            if(!stopping&&active!=null&&active.isRunning()&&engine!=null&&SystemClock.elapsedRealtime()-at<500)engine.command(raw);
        }finally{audioRequests.decrementAndGet();}}});
    }
    private void stopAudio(){audioGeneration++;AudioEngine old=audioEngine;audioEngine=null;audioOnline=false;audioState=null;if(old!=null)old.close();}
    private void syncAudio(){
        if(stopping)return;JSONObject current=state;
        boolean wanted=current!=null&&current.optBoolean("audio_owned",false)&&config.selected.contains("audio");
        if(!wanted){if(audioEngine!=null)stopAudio();return;}if(audioEngine!=null)return;
        final int generation=++audioGeneration;
        AudioEngine engine=new AudioEngine(this,new AudioEngine.Listener(){
            public void state(JSONObject data){if(generation!=audioGeneration||stopping)return;audioState=data;audioOnline=true;syncKnobMute();drawLocalMedia();}
            public boolean hardware(String op){NativeWorker active=worker;
                if(op.startsWith("audio power ")&&!config.selected.contains("amplifier"))return true;
                return generation==audioGeneration&&!stopping&&active!=null&&active.isRunning()&&active.send(op);
            }
        });
        audioEngine=engine;try{engine.start();}catch(Exception e){stopAudio();failed("Audio initialization failed");}
    }
    private void audioPublisher(){
        String sent="";MqttSession previous=null;boolean previousOnline=false;
        while(!stopping){
            refreshRuntimeStatus();
            NativeWorker active=worker;JSONObject data=audioState;boolean online=audioOnline&&config.selected.contains("audio")&&active!=null&&active.isRunning();
            MqttSession session=mqtt;String json=data==null?"":data.toString();
            if(mqttOnline&&(session!=previous||online!=previousOnline||!json.equals(sent))){
                publish("/audio/availability",online?"online":"offline",true);if(online&&data!=null)publish("/audio/state",json,true);
                previous=session;previousOnline=online;sent=json;
            }
            try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
        }
    }
    private synchronized void publishState(JSONObject value){
        if(value!=state)return;
        NativeWorker active=worker;if(active==null||!active.isRunning())return;
        try{
            boolean online=value.getBoolean("online");boolean enabled=value.getBoolean("buttons_enabled")||value.getBoolean("relays_enabled");
            publish("/module/capabilities","{\"buttons_enabled\":"+value.getBoolean("buttons_enabled")+",\"relays_enabled\":"+value.getBoolean("relays_enabled")+"}",true);
            publish("/module/availability",online&&enabled?"online":"offline",true);
            publish("/knob/input/availability",value.getBoolean("knob_input_enabled")&&value.getBoolean("knob_input_ready")?"online":"offline",true);
            publish("/knob/display/availability",value.getBoolean("knob_display_enabled")&&value.getBoolean("knob_display_ready")?"online":"offline",true);
            publish("/knob/controller/capabilities","{\"frames\":"+value.getBoolean("knob_display_enabled")+",\"local_buttons\":true,\"local_media\":true,\"media_icons\":true,\"knob_input_enabled\":"+value.getBoolean("knob_input_enabled")+"}",true);
            publish("/audio/amplifier/availability",value.optBoolean("audio_owned",false)&&config.selected.contains("amplifier")?"online":"offline",true);
            if(value.optBoolean("audio_owned",false))publish("/audio/owner","app",true);
            JSONArray relays=value.getJSONArray("relays");
            if(online&&value.getBoolean("relays_enabled"))for(int i=0;i<2;i++){int state=relays.getInt(i);if(state==0||state==1)publish("/relay/"+(i+1)+"/state",state==1?"ON":"OFF",true);}
        }catch(JSONException e){failed("Invalid native status");}
    }
    @Override public void message(JSONObject data){
        nativeCallback.set(Boolean.TRUE);try{nativeMessage(data);}finally{nativeCallback.remove();}
    }
    private void nativeMessage(JSONObject data){
        if(stopping)return;
        try{
            String type=data.getString("type");
            if(type.equals("status")){
                if(!config.deviceId.equals(data.getString("device_id")))throw new JSONException("Wrong identity");
                state=data;ready=(data.getBoolean("online")||!config.selected.contains("buttons")&&!config.selected.contains("relays"))
                    &&(!config.selected.contains("knob_input")||data.getBoolean("knob_input_ready"))&&(!config.selected.contains("knob_display")||data.getBoolean("knob_display_ready"))
                    &&(!(config.selected.contains("audio")||config.selected.contains("amplifier"))||data.optBoolean("audio_owned",false))
                    &&(!config.selected.contains("display")||data.optBoolean("display_ready",false))&&data.optBoolean("management_owned",false);
                handler.post(new Runnable(){public void run(){syncAudio();}});
                if(ready)seenOnline=true;refreshRuntimeStatus();publishState(data);
            }else if(type.equals("bindings")){
                JSONArray rows=data.getJSONArray("values");if(rows.length()!=4)throw new JSONException("Wrong binding count");int[][] copy=new int[4][2];
                for(int b=0;b<4;b++){JSONArray row=rows.getJSONArray(b);if(row.length()!=2)throw new JSONException("Invalid binding");for(int g=0;g<2;g++){int value=row.getInt(g);if(value<0||value>2)throw new JSONException("Invalid relay");copy[b][g]=value;}}
                bindings=copy;bindingsKnown=true;synchronizeState();
            }else if(type.equals("button")){
                NativeWorker active=worker;long age=SystemClock.elapsedRealtime()-data.getLong("at_ms");
                if(active==null||!active.isRunning()||age<0||age>1500)return;
                int button=data.getInt("button");String event=data.getString("event");int local=data.getInt("local_binding");
                if(button<1||button>4||!(event.equals("press")||event.equals("double_press"))||local<0||local>2)throw new JSONException("Invalid button");
                if(local==0&&ready){JSONObject payload=new JSONObject();payload.put("event_type",event);payload.put("button",button);payload.put("module_key_id",data.getInt("module_id"));publish("/button/"+button+"/event",payload.toString(),false);}
            }else if(type.equals("knob")){
                NativeWorker active=worker;long age=SystemClock.elapsedRealtime()-data.getLong("at_ms");String event=data.getString("event");
                if(active==null||!active.isRunning()||age<0||age>1500||!config.selected.contains("knob_input")||!event.matches("rotate_left|rotate_right|press|double_press|long_press"))return;
                boolean handled=localMediaEvent(event);
                JSONObject payload=new JSONObject();payload.put("local_handled",handled);payload.put("event_type",event);payload.put("steps",1);payload.put("key_code",data.getInt("key_code"));
                JSONObject current=state;if(current!=null&&current.optBoolean("knob_input_ready",false)){publish("/knob/event",payload.toString(),false);publish("/event",payload.toString(),false);}
            }else if(type.equals("knob_state")){
                String topic=data.getString("topic");if(config.selected.contains("knob_display")&&topic.matches("/knob/(?:display/(?:availability|(?:title|value|detail|power|follow_main|activity|ring|color|actual)/state)|led/color/state)"))publish(topic,data.getString("payload"),true);
            }else if(type.equals("management_state")){ManagementRuntime current=management;if(current!=null)current.backend(data);}
            else if(type.equals("display_state")){if(config.selected.contains("display"))publish("/display/state",data.getJSONObject("data").toString(),true);}
            else if(type.equals("audio_level")){if(config.selected.contains("amplifier")){int value=data.getInt("value");if(value>=0&&value<=100)publish("/audio/hardware_level/state",Integer.toString(value),true);}}
            else if(type.equals("audio_result")){
                final String command=data.getString("command");final boolean ok=data.getBoolean("ok");
                handler.post(new Runnable(){public void run(){AudioEngine engine=audioEngine;if(engine!=null)engine.hardwareResult(command,ok);}});
            }else if(type.equals("error")){store.runtimeStatus("Hardware: "+data.getString("error"));}
            else if(type.equals("result")&&!data.getBoolean("ok")){store.runtimeStatus("Hardware command rejected: "+data.getString("error"));}
        }catch(JSONException e){failed("Invalid native response");}
    }
    @Override public void exited(String reason){if(!stopping)failed(reason);}
    private synchronized void refreshRuntimeStatus(){
        if(IntercomSession.active&&worker!=null&&config!=null&&config.selected.contains("display")&&SystemClock.elapsedRealtime()-intercomWake>20000){worker.send("display power 1");intercomWake=SystemClock.elapsedRealtime();}
        if(stopping)return;
        boolean complete=ready&&(!config.selected.contains("audio")||audioOnline);
        MqttSession current=mqtt;boolean online=complete&&mqttOnline;
        if(mqttOnline&&(current!=presenceSession||online!=presenceOnline)){
            publish("/availability",online?"online":"offline",true);publish("/app/availability",online?"online":"offline",true);presenceSession=current;presenceOnline=online;
        }
        if(complete&&mqttOnline){
            retryDelay=5000;
            if(!verified){try{store.markRuntimeVerified();verified=true;}catch(Exception e){failed("Hardware verification could not be saved");return;}}
            store.runtimeStatus("App hardware and MQTT connected");
        }else if(ready&&!complete)store.runtimeStatus("Starting audio engine…");
        else if(ready)store.runtimeStatus("Local hardware active; waiting for MQTT");
        else store.runtimeStatus(recoveryReason==null?"Waiting for hardware worker…":recoveryReason);
    }
    private synchronized void startWorker(){
        if(stopping||!store.runtimeEnabled())return;recoveryReason=null;started=SystemClock.elapsedRealtime();final int generation=++workerGeneration;
        worker=new NativeWorker();worker.start(this,config,new NativeWorker.Listener(){
            public void message(JSONObject data){synchronized(BridgeService.this){if(generation==workerGeneration&&!stopping)BridgeService.this.message(data);}}
            public void exited(String reason){synchronized(BridgeService.this){if(generation==workerGeneration&&!stopping)BridgeService.this.exited(reason);}}
        });
    }
    private void failed(final String reason){handler.post(new Runnable(){public void run(){synchronized(BridgeService.this){
        if(stopping||worker==null)return;workerGeneration++;NativeWorker old=worker;worker=null;old.close();ready=false;state=null;stopAudio();
        ManagementRuntime owner=management;if(owner!=null){owner.cancelCapture();owner.backend(null);}recoveryReason=reason+"; retrying hardware in "+(retryDelay/1000)+" seconds";refreshRuntimeStatus();
        handler.postDelayed(new Runnable(){public void run(){startWorker();}},retryDelay);retryDelay=ActivationPolicy.nextRetry(retryDelay);
    }}});}
    @Override public void onDestroy(){
        feedback.shutdownNow();
        NetworkRouter.get(this).remove(networkChanges);if(instance==this)instance=null;
        stopping=true;handler.removeCallbacksAndMessages(null);MqttSession session=mqtt;mqtt=null;if(session!=null)session.close();
        if(transportDeadline!=null)transportDeadline.cancel();
        ManagementRuntime previous=management;management=null;if(previous!=null)previous.close();
        stopAudio();
        NativeWorker old=worker;worker=null;if(old!=null)old.close();stopForeground(true);super.onDestroy();
        if(cpu!=null&&cpu.isHeld())cpu.release();
    }
    public static void ensureStarted(android.content.Context context){
        if(!new ConfigStore(context).runtimeEnabled())return;
        Intent intent=new Intent(context,BridgeService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);
    }
    static boolean sameConnection(PanelConfig a,PanelConfig b){return a.profile.equals(b.profile)&&a.deviceId.equals(b.deviceId)&&a.broker.equals(b.broker)&&a.port==b.port&&a.tls==b.tls&&a.username.equals(b.username)&&a.password.equals(b.password)&&a.mqttBase.equals(b.mqttBase);}
}
