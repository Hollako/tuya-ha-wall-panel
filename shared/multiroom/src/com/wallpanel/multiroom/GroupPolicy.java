package com.wallpanel.multiroom;
import java.net.URI;
/** Small, bounded initial group contract. No app-audio capture is implied. */
public final class GroupPolicy {
 public static String role(String value){if(!"leader".equals(value)&&!"receiver".equals(value))throw new IllegalArgumentException("Choose Leader or Receiver");return value;}
 public static String host(String value){String host=value==null?"":value.trim();if(!host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")||host.contains(".."))throw new IllegalArgumentException("Enter the leader IP or hostname without a port");return host;}
 public static String source(String value){
  try{URI uri=new URI(value);String scheme=uri.getScheme();if(!("http".equals(scheme)||"https".equals(scheme)||"content".equals(scheme)))throw new Exception();
   if(value.length()>4096||uri.getFragment()!=null||uri.getUserInfo()!=null||(("http".equals(scheme)||"https".equals(scheme))&&uri.getHost()==null))throw new Exception();return value;
  }catch(Exception error){throw new IllegalArgumentException("Choose an audio file or enter a valid HTTP/HTTPS audio URL");}
 }
 public static String format(int rate,int channels){if(rate!=44100&&rate!=48000||channels<1||channels>2)throw new IllegalArgumentException("Use 44.1 or 48 kHz mono/stereo audio for this first version");return rate+":16:"+channels;}
 public static int buffer(int value){if(value<1000||value>5000)throw new IllegalArgumentException("Group buffer must be 1000 to 5000 ms");return value;}
 public static int roomDelay(int value){if(value<-500||value>500)throw new IllegalArgumentException("Room timing must be -500 to 500 ms");return value;}
 public static String clientLatency(int roomDelay){return Integer.toString(-roomDelay(roomDelay));}
 private GroupPolicy(){}
}
