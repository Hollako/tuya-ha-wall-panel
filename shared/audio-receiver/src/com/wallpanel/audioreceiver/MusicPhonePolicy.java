package com.wallpanel.audioreceiver;
/** One connected/connecting music owner. Pausing does not release ownership. */
public final class MusicPhonePolicy {
 private String owner;private boolean established;
 public synchronized boolean reserve(String phone){if(phone==null||phone.isEmpty())return false;if(owner==null){owner=phone;established=false;}return owner.equals(phone);}
 public synchronized boolean observe(String phone,int state){
  if(state==0){disconnected(phone);return true;}
  if(state==1||state==2){boolean accepted=reserve(phone);if(accepted&&state==2)established=true;return accepted;}
  return owner==null||owner.equals(phone);
 }
 public synchronized void disconnected(String phone){if(owner!=null&&owner.equals(phone)){owner=null;established=false;}}
 public synchronized void requestFailed(String phone){if(!established)disconnected(phone);}
 public synchronized void clear(){owner=null;established=false;}
 public synchronized boolean allows(String phone){return owner==null||owner.equals(phone);}
 public synchronized String owner(){return owner;}
}
