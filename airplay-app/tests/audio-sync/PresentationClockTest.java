package com.wallpanel.audioreceiver;
public final class PresentationClockTest {
  private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
  public static void main(String[] args){
    PresentationClock c=new PresentationClock();
    check(c.head(-16)==0xfffffff0L,"unsigned head");
    check(c.head(32)==0x100000020L,"32-bit wrap");
    c=new PresentationClock();c.head(48000);
    check(c.timestamp(48000,1_000_000_000L,1_000_000_000L,100000),"first timestamp");
    check(!c.valid(1_000_000_000L),"warmup is not stable");
    c.head(96000);check(c.timestamp(96000,2_000_000_000L,2_000_000_000L,200000),"second timestamp");
    check(c.valid(2_000_000_000L),"advancing clock");
    check(c.played(2_500_000_000L,200000)==120000,"presentation extrapolation");
    check(c.played(2_500_000_000L,100000)==100000,"never beyond submitted frames");
    check(!c.valid(15_000_000_000L),"stale timestamp invalid");
    c.head(100);check(c.discontinuity,"counter reset distinguished from wrap");check(!c.valid(2_100_000_000L),"counter reset invalidates lock");
    c=new PresentationClock();c.head(48000);c.timestamp(48000,1_000_000_000L,1_000_000_000L,200000);
    c.head(100000);c.timestamp(100000,2_000_000_000L,2_000_000_000L,200000);
    check(!c.valid(2_000_000_000L),"implausible rate rejected");
    c=new PresentationClock();c.head(0xfffffff0);c.timestamp(0xfffffff0L,1_000_000_000L,1_000_000_000L,0x100100000L);
    c.head(47984);check(c.timestamp(47984,2_000_000_000L,2_000_000_000L,0x100100000L),"timestamp wrap");
    check(c.timestampFrame==0x10000bb70L,"timestamp unwrap epoch");
    c.invalidate();check(!c.valid(2_000_000_000L),"explicit invalidate");
    System.out.println("PresentationClock tests passed");
  }
}
