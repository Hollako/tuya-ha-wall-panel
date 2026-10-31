package com.wallpanel.bridge;
import java.util.*;
import java.util.regex.*;
/** Read-only native AudioTrack evidence; Java MediaSessions omit G10's Bluetooth track. */
public final class PlaybackTracks {
 public static final class Result {public final boolean known,active;Result(boolean k,boolean a){known=k;active=a;}}
 private static final Pattern TRACK=Pattern.compile("^\\s*(?:[A-Za-z]\\s+)?\\d+\\s+(yes|no)\\s+(\\d+)\\s+.*",Pattern.CASE_INSENSITIVE);
 public static Result inspect(String dump,int[] pids){
  if(dump==null||pids.length==0)return new Result(false,false);boolean output=false,table=false,recognized=false,active=false,unsupported=false;
  for(String line:dump.split("\\r?\\n")){
   if(line.startsWith("Output thread")){output=true;table=false;}
   else if(line.startsWith("Input thread")){output=false;table=false;}
   if(output&&line.contains("Active")&&line.contains("Client")&&line.contains("Session")){table=true;recognized=true;continue;}
   if(line.contains("Effect Chains")||line.contains("Local log:"))table=false;
   if(!output||!table)continue;Matcher match=TRACK.matcher(line);if(!match.matches()||!match.group(1).equalsIgnoreCase("yes"))continue;
   for(int pid:pids)if(Integer.toString(pid).equals(match.group(2))){String[] tail=line.substring(match.end(2)).trim().split("\\s+");int flags=-1;for(int i=0;i<tail.length;i++)if(tail[i].startsWith("0x")){flags=i;break;}if(flags<0||tail.length<=flags+4)unsupported=true;else if(tail[flags+4].equals("3"))active=true;}
  }
  return new Result(recognized&&(!unsupported||active),active);
 }
 private PlaybackTracks(){}
}
