package com.wallpanel.audioreceiver;
import java.util.concurrent.atomic.*;
public class IncomingMusicPolicyTest {
 static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  IncomingMusicPolicy p=new IncomingMusicPolicy();IncomingMusicPolicy.Link link=p.connected("phone",1000);
  ok(p.connected("phone",1100)==link);ok(p.claim(link,1750,true,true,0,false));ok(!p.claim(link,1850,true,true,0,false));
  p.disconnected("phone");ok(!p.current(link,1900));link=p.connected("phone",2000);ok(!p.claim(link,2750,true,true,0,false));p.disconnected("phone");link=p.connected("phone",12000);ok(p.claim(link,12750,true,true,0,false));
  for(int mode=0;mode<7;mode++){p=new IncomingMusicPolicy();link=p.connected("phone",1000);if(mode==6)p.disconnected("phone");ok(!p.claim(link,mode==4?7000:mode==5?999:1750,mode!=0,mode!=1,mode==2?2:0,mode==3));}
  for(int state=1;state<=3;state++){p=new IncomingMusicPolicy();link=p.connected("phone",1000);ok(!p.claim(link,1750,true,true,state,false));}
  p=new IncomingMusicPolicy();link=p.connected("phone",1000);p.radioOff();ok(!p.current(link,1750));ok(p.connected("phone",2000)!=link);
  p=new IncomingMusicPolicy();link=p.connected("phone",1000);p.disconnected("phone");IncomingMusicPolicy.Link replacement=p.connected("phone",1100);ok(!p.claim(link,1750,true,true,0,false));ok(p.claim(replacement,1850,true,true,0,false));
  p=new IncomingMusicPolicy();for(int i=0;i<8;i++)ok(p.connected("phone"+i,1000)!=null);ok(p.connected("excess",1000)==null);p.disconnected("phone0");ok(p.connected("new",2000)!=null);
  final IncomingMusicPolicy policy=new IncomingMusicPolicy();final IncomingMusicPolicy.Link same=policy.connected("phone",1000);final AtomicInteger accepted=new AtomicInteger();Thread[] workers=new Thread[16];for(int i=0;i<workers.length;i++){workers[i]=new Thread(()->{if(policy.claim(same,1750,true,true,0,false))accepted.incrementAndGet();});workers[i].start();}for(Thread t:workers)t.join();ok(accepted.get()==1);
  System.out.println("PASS: "+checks+" incoming music guards: one request, bonded live link, cooldown, stale events, other peers, radio state and bounded tracking");
 }
}
