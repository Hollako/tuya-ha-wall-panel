package com.wallpanel.multiroom;

/** Receiver address and bounded retry policy. Does not own audio or group membership. */
final class GroupReconnect {
 private final String leaderId;private String host;private long retryAt;private int failures;
 GroupReconnect(String id,String address){leaderId=id;host=GroupPolicy.host(address);}
 synchronized String host(){return host;}
 synchronized boolean confirmed(String id,String address){
  if(leaderId.isEmpty()||!leaderId.equals(id))return false;
  String next=GroupPolicy.host(address);if(host.equals(next))return false;
  host=next;connected();return true;
 }
 synchronized boolean due(long now){return now>=retryAt;}
 synchronized void failed(long now){retryAt=now+Math.min(15000L,500L<<Math.min(failures,5));failures=Math.min(failures+1,6);}
 synchronized void connected(){failures=0;retryAt=0;}
}
