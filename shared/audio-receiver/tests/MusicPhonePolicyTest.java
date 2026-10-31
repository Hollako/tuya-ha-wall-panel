package com.wallpanel.audioreceiver;
import java.util.concurrent.atomic.*;
public class MusicPhonePolicyTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  MusicPhonePolicy p=new MusicPhonePolicy();ok(!p.reserve(null));ok(!p.reserve(""));ok(p.observe("phone",1));ok(p.observe("phone",2));ok(p.owner().equals("phone"));
  for(String other:new String[]{"second-phone","laptop","tablet"}){ok(!p.reserve(other));ok(!p.observe(other,1));ok(!p.observe(other,2));p.observe(other,0);ok(p.owner().equals("phone"));}
  p.requestFailed("laptop");ok(p.owner().equals("phone"));p.requestFailed("phone");ok(p.owner().equals("phone"));
  ok(p.observe("phone",3));ok(!p.reserve("laptop"));p.observe("phone",0);ok(p.reserve("laptop"));ok(p.observe("laptop",2));p.observe("phone",0);ok(p.owner().equals("laptop"));
  p.clear();ok(p.owner()==null);ok(p.reserve("phone"));p.requestFailed("phone");ok(p.owner()==null);ok(p.reserve("tablet"));ok(p.allows("tablet"));ok(!p.allows("phone"));
  final MusicPhonePolicy race=new MusicPhonePolicy();final AtomicInteger winners=new AtomicInteger();Thread[] threads=new Thread[20];for(int i=0;i<20;i++){final String key="sender"+i;threads[i]=new Thread(()->{if(race.reserve(key))winners.incrementAndGet();});threads[i].start();}for(Thread t:threads)t.join();ok(winners.get()==1);ok(race.owner()!=null);
  System.out.println("PASS: "+checks+" music admission checks: phones, laptops, tablets, first connection retained until disconnect, stale events, reservations and concurrent attempts");
 }
}
