package com.wallpanel.bridge;
/** Keep the capture mix within the firmware's 20-rule limit. */
public final class AppCapturePolicy {
 public static boolean allowed(int uid,int owner){return uid>=10000&&uid<=19999&&uid!=owner;}
 public static int[] exclusions(int owner,int[] installed){
  java.util.TreeSet<Integer> values=new java.util.TreeSet<Integer>();
  for(int uid:new int[]{0,1000,1001,1002,1013,1041,2000,owner})values.add(Integer.valueOf(uid));
  for(int uid:installed)if(!allowed(uid,owner))values.add(Integer.valueOf(uid));
  if(values.size()+2>20)throw new IllegalStateException("Too many system audio identities for this firmware");
  int[] result=new int[values.size()];int i=0;for(Integer uid:values)result[i++]=uid.intValue();return result;
 }
 private AppCapturePolicy(){}
}
