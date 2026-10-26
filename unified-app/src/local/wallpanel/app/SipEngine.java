package local.wallpanel.app;
import android.javax.sip.*;
import android.javax.sip.address.*;
import android.javax.sip.header.*;
import android.javax.sip.message.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** One serialized SIP owner. No Android UI, hardware, stored passwords or logging. */
public final class SipEngine implements SipListener {
    private static final Object STACK_LIFECYCLE=new Object();private static SipEngine stackOwner;
    private Thread scannerThread;
    private static Set<Thread> scanners(){Thread[] all=new Thread[Thread.activeCount()*2+32];int n=Thread.enumerate(all);Set<Thread> result=new HashSet<Thread>();for(int i=0;i<n;i++)if(all[i]!=null&&all[i].getName().equals("EventScannerThread"))result.add(all[i]);return result;}
    public enum State {IDLE,RINGING,IN_CALL,ENDING}
    public interface Media {void start(SipSdp offer)throws Exception;void update(SipSdp offer)throws Exception;void close();}
    public interface Host {SipConfig settings()throws Exception;Media media(String local,SipConfig config);void state(State state,boolean registered,String caller,String detail);}
    public interface NetworkHost extends Host {String networkAddress(SipConfig c)throws Exception;String networkKey()throws Exception;}
    private String networkIdentity="";
    private String localAddress(SipConfig c)throws Exception{return host instanceof NetworkHost?((NetworkHost)host).networkAddress(c):address(c);}
    private String networkKey()throws Exception{return host instanceof NetworkHost?((NetworkHost)host).networkKey():"";}
    private final Host host;private final ScheduledExecutorService work=Executors.newSingleThreadScheduledExecutor();private volatile boolean closed;private volatile State state=State.IDLE;private volatile boolean registered;
    private SipConfig config;private SipStack stack;private SipProvider provider;private AddressFactory addresses;private MessageFactory messages;private HeaderFactory headers;
    private String local="",registrationId,registrationTag,callId,callTag,caller="",detail="Starting SIP";private long cseq,registerAt,registerDeadline,retryAt,ringAt,ackAt,networkAt;private int authAttempts,nonceCount;private boolean proxyAuth,acknowledged,hangupPending;
    private WWWAuthenticateHeader challenge;private ServerTransaction inviteTransaction;private Request invite;private SipSdp offer;private Dialog dialog;private volatile Media media;private long callSerial;
    public SipEngine(Host h){host=h;}
    public State state(){return state;}public boolean registered(){return registered;}
    public void start(){enqueue(new Runnable(){public void run(){initialize();}});work.scheduleWithFixedDelay(new Runnable(){public void run(){if(!closed)tick();}},1,1,TimeUnit.SECONDS);}
    private void enqueue(final Runnable r){if(!closed)try{work.execute(new Runnable(){public void run(){if(!closed)r.run();}});}catch(RejectedExecutionException ignored){}}
    private void report(String text){detail=text;try{host.state(state,registered,caller,detail);}catch(Exception ignored){}}
    private static String random(){return UUID.randomUUID().toString().replace("-","");}
    public static String address(SipConfig c)throws Exception{if(c.localAddress.length()>0)return c.localAddress;DatagramSocket s=new DatagramSocket();try{s.connect(InetAddress.getByName(c.server),c.serverPort);String ip=s.getLocalAddress().getHostAddress();if(!SipConfig.ipv4(ip)||ip.equals("0.0.0.0"))throw new SocketException("No IPv4 route");return ip;}finally{s.close();}}
    private void initialize(){
        synchronized(STACK_LIFECYCLE){if(closed)return;if(stackOwner!=null&&stackOwner!=this){retryAt=System.currentTimeMillis()+2000;report("Waiting for previous SIP owner to stop");return;}stackOwner=this;
        try{config=host.settings();config.validate();local=localAddress(config);networkIdentity=networkKey();Properties p=new Properties();if(host instanceof NetworkHost){p.setProperty("android.gov.nist.javax.sip.NETWORK_LAYER","local.wallpanel.app.SipNetworkLayer");p.setProperty("android.gov.nist.javax.sip.ADDRESS_RESOLVER","local.wallpanel.app.SipNetworkResolver");}p.setProperty("android.javax.sip.STACK_NAME","WallPanel-"+random());p.setProperty("android.javax.sip.IP_ADDRESS",local);p.setProperty("gov.nist.javax.sip.TRACE_LEVEL","0");p.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL","0");p.setProperty("android.gov.nist.javax.sip.REENTRANT_LISTENER","false");
            SipFactory factory=SipFactory.getInstance();factory.setPathName("android.gov.nist");Set<Thread> prior=scanners();try{stack=factory.createSipStack(p);}finally{for(Thread t:scanners())if(!prior.contains(t))scannerThread=t;}addresses=factory.createAddressFactory();headers=factory.createHeaderFactory();messages=factory.createMessageFactory();
            ListeningPoint point=stack.createListeningPoint(local,config.sipPort,"udp");provider=stack.createSipProvider(point);provider.addSipListener(this);provider.setAutomaticDialogSupportEnabled(true);stack.start();
            registrationId=provider.getNewCallId().getCallId();registrationTag=random();cseq=0;challenge=null;authAttempts=0;nonceCount=0;registered=false;sendRegister(3600);report("Registering SIP");
        }catch(Exception failed){stopStack();retryAt=System.currentTimeMillis()+10000;report("SIP connection unavailable ("+failed.getClass().getSimpleName()+")");}}
    }
    private String serverUri(){return "sip:"+config.server+(config.serverPort==5060?"":":"+config.serverPort);}
    private ContactHeader contact()throws Exception{return headers.createContactHeader(addresses.createAddress("sip:"+config.user+"@"+local+":"+config.sipPort));}
    private void sendRegister(int expires)throws Exception{
        ArrayList<ViaHeader> via=new ArrayList<ViaHeader>();ViaHeader v=headers.createViaHeader(local,config.sipPort,"udp","z9hG4bK"+random());v.setRPort();via.add(v);
        Address account=addresses.createAddress("sip:"+config.user+"@"+config.server);Request request=messages.createRequest(addresses.createURI(serverUri()),Request.REGISTER,headers.createCallIdHeader(registrationId),headers.createCSeqHeader(++cseq,Request.REGISTER),headers.createFromHeader(account,registrationTag),headers.createToHeader(account,null),via,headers.createMaxForwardsHeader(70));
        request.addHeader(contact());request.addHeader(headers.createExpiresHeader(expires));request.addHeader(headers.createUserAgentHeader(Arrays.asList("TuyaHAWallPanel")));
        if(challenge!=null){String algorithm=challenge.getAlgorithm();if(algorithm==null)algorithm="MD5";String qop="";if(challenge.getQop()!=null){for(String q:challenge.getQop().replace("\"","").split(","))if(q.trim().equalsIgnoreCase("auth"))qop="auth";if(qop.length()==0)throw new IllegalArgumentException("Unsupported authentication quality");}
            AuthorizationHeader auth=proxyAuth?headers.createProxyAuthorizationHeader("Digest"):headers.createAuthorizationHeader("Digest");String cnonce=random(),nc=String.format(Locale.US,"%08x",++nonceCount);auth.setUsername(config.authenticationUser());auth.setRealm(challenge.getRealm());auth.setNonce(challenge.getNonce());auth.setURI(request.getRequestURI());auth.setAlgorithm(algorithm);auth.setResponse(SipDigest.response(config.authenticationUser(),challenge.getRealm(),config.password,Request.REGISTER,request.getRequestURI().toString(),challenge.getNonce(),algorithm,qop,nc,cnonce));if(challenge.getOpaque()!=null)auth.setOpaque(challenge.getOpaque());if(qop.length()>0){auth.setQop(qop);auth.setNonceCount(nonceCount);auth.setCNonce(cnonce);}else if(algorithm.toLowerCase(Locale.US).endsWith("-sess"))auth.setCNonce(cnonce);request.addHeader(auth);}
        provider.getNewClientTransaction(request).sendRequest();registerDeadline=System.currentTimeMillis()+15000;registerAt=0;
    }
    private void tick(){try{
        long now=System.currentTimeMillis();if(provider==null){if(now>=retryAt)initialize();return;}
        SipConfig latest=host.settings();boolean moved=false;if(now>=networkAt){networkAt=now+10000;moved=!localAddress(config).equals(local)||!networkKey().equals(networkIdentity);}if(moved){if(state!=State.IDLE)clearCall("Intercom network changed");stopStack();initialize();return;}if(!config.sameConnection(latest)){if(state!=State.IDLE){report("End the call before changing SIP connection settings");return;}stopStack();initialize();return;}config=latest;
        if(registerDeadline>0&&now>=registerDeadline){registered=false;registerDeadline=0;registerAt=now+10000;report("SIP registration timed out; retrying");}
        if(registerAt>0&&now>=registerAt){authAttempts=0;sendRegister(3600);}
        if(state==State.RINGING&&now>=ringAt){decline(480);clearCall("Missed call");}
        if((state==State.IN_CALL||state==State.ENDING)&&!acknowledged&&ackAt>0&&now>=ackAt)clearCall("Call was not acknowledged");
    }catch(Exception failure){registered=false;if(state!=State.IDLE)clearCall("SIP connection lost");stopStack();retryAt=System.currentTimeMillis()+10000;report("SIP connection lost; retrying");}}
    private ServerTransaction transaction(RequestEvent event)throws Exception{ServerTransaction t=event.getServerTransaction();return t==null?provider.getNewServerTransaction(event.getRequest()):t;}
    private Response response(Request request,int code,boolean inCall)throws Exception{Response result=messages.createResponse(code,request);ToHeader to=(ToHeader)result.getHeader(ToHeader.NAME);if(to.getTag()==null)to.setTag(inCall?callTag:random());result.addHeader(headers.createUserAgentHeader(Arrays.asList("TuyaHAWallPanel")));return result;}
    private void handleRequest(RequestEvent event){if(provider==null||event.getSource()!=provider)return;Request r=event.getRequest();String method=r.getMethod();String id=((CallIdHeader)r.getHeader(CallIdHeader.NAME)).getCallId();try{
        if(method.equals(Request.INVITE)){
            ServerTransaction t=transaction(event);
            if(state!=State.IDLE){if(!id.equals(callId)){t.sendResponse(response(r,486,false));return;}if(state!=State.IN_CALL){t.sendResponse(response(r,491,true));return;}
                SipSdp update;try{update=SipSdp.parse(r.getRawContent()==null?null:new String(r.getRawContent(),"UTF-8"));}catch(Exception invalid){t.sendResponse(response(r,488,true));return;}media.update(update);Response ok=response(r,200,true);ok.addHeader(contact());ok.setContent(update.answer(local,config.rtpPort),headers.createContentTypeHeader("application","sdp"));t.sendResponse(ok);return;}
            SipSdp offered;try{offered=SipSdp.parse(r.getRawContent()==null?null:new String(r.getRawContent(),"UTF-8"));}catch(Exception invalid){t.sendResponse(response(r,488,false));return;}
            callId=id;callTag=random();caller=((FromHeader)r.getHeader(FromHeader.NAME)).getAddress().getDisplayName();if(caller==null||caller.length()==0)caller=((FromHeader)r.getHeader(FromHeader.NAME)).getAddress().getURI().toString();if(caller.length()>128)caller=caller.substring(0,128);
            invite=r;inviteTransaction=t;offer=offered;acknowledged=false;hangupPending=false;state=State.RINGING;ringAt=System.currentTimeMillis()+config.ringSeconds*1000L;long serial=++callSerial;
            Response ringing=response(r,180,true);ringing.addHeader(contact());t.sendResponse(ringing);report("Incoming intercom call");
            if(host.settings().autoAnswer){final long expected=serial;work.schedule(new Runnable(){public void run(){try{if(!closed&&callSerial==expected&&state==State.RINGING&&host.settings().autoAnswer)answerNow();}catch(Exception ignored){}}},500,TimeUnit.MILLISECONDS);}
        }else if(method.equals(Request.CANCEL)){
            transaction(event).sendResponse(response(r,200,id.equals(callId)));if(id.equals(callId)&&state==State.RINGING){decline(487);clearCall("Call cancelled");}
        }else if(method.equals(Request.BYE)){
            boolean matches=id.equals(callId);transaction(event).sendResponse(response(r,matches?200:481,matches));if(matches)clearCall("Call ended");
        }else if(method.equals(Request.ACK)){if(id.equals(callId)){acknowledged=true;if(hangupPending)byeAndClear();}}
        else if(method.equals(Request.OPTIONS)){Response ok=response(r,200,false);ok.addHeader(headers.createAllowHeader("INVITE, ACK, CANCEL, BYE, OPTIONS"));transaction(event).sendResponse(ok);}
        else transaction(event).sendResponse(response(r,405,false));
    }catch(Exception failed){if(id.equals(callId))clearCall("Call handling failed ("+failed.getClass().getSimpleName()+")");}}
    public void answer(){enqueue(new Runnable(){public void run(){answerNow();}});}
    private void answerNow(){if(state!=State.RINGING||closed)return;try{
        SipConfig live=host.settings();Media pending=host.media(local,live);media=pending;if(closed){pending.close();return;}pending.start(offer);if(closed){pending.close();return;}Response ok=response(invite,200,true);ok.addHeader(contact());ok.setContent(offer.answer(local,config.rtpPort),headers.createContentTypeHeader("application","sdp"));inviteTransaction.sendResponse(ok);dialog=inviteTransaction.getDialog();state=State.IN_CALL;ackAt=System.currentTimeMillis()+32000;report("Intercom call active");
    }catch(Exception failed){try{decline(500);}catch(Exception ignored){}clearCall("Call audio unavailable ("+failed.getClass().getSimpleName()+")");}}
    public void hangup(){enqueue(new Runnable(){public void run(){try{if(state==State.RINGING){decline(603);clearCall("Call declined");}else if(state==State.IN_CALL){if(acknowledged)byeAndClear();else{hangupPending=true;if(media!=null){media.close();media=null;}state=State.ENDING;report("Ending call");}}}catch(Exception failed){clearCall("Call ended");}}});}
    private void decline(int code)throws Exception{if(inviteTransaction!=null)inviteTransaction.sendResponse(response(invite,code,true));}
    private void byeAndClear(){try{if(dialog!=null){Request bye=dialog.createRequest(Request.BYE);dialog.sendRequest(provider.getNewClientTransaction(bye));}}catch(Exception ignored){}clearCall("Call ended");}
    public void mediaFailed(){if(state==State.IN_CALL)hangup();else enqueue(new Runnable(){public void run(){clearCall("Call audio failed");}});}
    private void clearCall(String reason){if(media!=null){media.close();media=null;}callSerial++;state=State.IDLE;callId=null;callTag=null;invite=null;inviteTransaction=null;dialog=null;offer=null;caller="";ringAt=ackAt=0;hangupPending=false;report(reason);}
    private void handleResponse(ResponseEvent event){if(provider==null||event.getSource()!=provider)return;Response r=event.getResponse();CSeqHeader seq=(CSeqHeader)r.getHeader(CSeqHeader.NAME);CallIdHeader id=(CallIdHeader)r.getHeader(CallIdHeader.NAME);if(seq==null||id==null||!seq.getMethod().equals(Request.REGISTER)||!id.getCallId().equals(registrationId)||seq.getSeqNumber()!=cseq)return;int code=r.getStatusCode();try{
        if(code==401||code==407){WWWAuthenticateHeader fresh=(WWWAuthenticateHeader)r.getHeader(code==401?WWWAuthenticateHeader.NAME:ProxyAuthenticateHeader.NAME);if(fresh==null||++authAttempts>3)throw new IllegalArgumentException("Authentication rejected");if(challenge==null||!fresh.getNonce().equals(challenge.getNonce()))nonceCount=0;challenge=fresh;proxyAuth=code==407;sendRegister(3600);}
        else if(code>=200&&code<300){registered=true;registerDeadline=0;authAttempts=0;int expiry=3600;ContactHeader c=(ContactHeader)r.getHeader(ContactHeader.NAME);ExpiresHeader ex=(ExpiresHeader)r.getHeader(ExpiresHeader.NAME);if(c!=null&&c.getExpires()>0)expiry=c.getExpires();else if(ex!=null)expiry=ex.getExpires();registerAt=System.currentTimeMillis()+Math.max(10,Math.min(1800,expiry*2/3))*1000L;report(state==State.IDLE?"SIP registered":detail);}
        else if(code>=300){registered=false;registerDeadline=0;registerAt=System.currentTimeMillis()+30000;report("SIP registration rejected ("+code+")");}
    }catch(Exception failed){registered=false;registerDeadline=0;registerAt=System.currentTimeMillis()+30000;report("SIP authentication failed");}}
    private void stopStack(){synchronized(STACK_LIFECYCLE){registered=false;registerDeadline=registerAt=0;if(provider!=null)try{provider.removeSipListener(this);}catch(Exception ignored){}if(stack!=null)try{stack.stop();}catch(Exception ignored){}
        // This pinned RI version sets isStopped but leaves its queue consumer in take().
        // Wake only the event thread created by this stack, after stopping the stack.
        if(scannerThread!=null){scannerThread.interrupt();try{scannerThread.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}scannerThread=null;}
        provider=null;stack=null;if(stackOwner==this){SipFactory.getInstance().resetFactory();stackOwner=null;}
    }}
    public void close(){if(closed)return;closed=true;Media active=media;if(active!=null)active.close();try{work.execute(new Runnable(){public void run(){try{if(state==State.RINGING)decline(480);else if(state==State.IN_CALL&&acknowledged)byeAndClear();if(provider!=null&&registered)sendRegister(0);}catch(Exception ignored){}finally{clearCall("SIP stopped");stopStack();work.shutdown();}}});}catch(RejectedExecutionException ignored){}}
    public void processRequest(final RequestEvent e){enqueue(new Runnable(){public void run(){handleRequest(e);}});}
    public void processResponse(final ResponseEvent e){enqueue(new Runnable(){public void run(){handleResponse(e);}});}
    public void processTimeout(final TimeoutEvent e){enqueue(new Runnable(){public void run(){if(e.isServerTransaction()&&e.getServerTransaction()==inviteTransaction&&state==State.RINGING)clearCall("Call timed out");}});}
    public void processIOException(IOExceptionEvent e){}
    public void processTransactionTerminated(TransactionTerminatedEvent e){}
    public void processDialogTerminated(final DialogTerminatedEvent e){enqueue(new Runnable(){public void run(){if(dialog!=null&&dialog.equals(e.getDialog())&&state!=State.IDLE)clearCall("Call ended");}});}
}
