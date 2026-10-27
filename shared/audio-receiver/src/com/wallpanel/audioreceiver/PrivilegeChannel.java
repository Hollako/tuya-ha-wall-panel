package com.wallpanel.audioreceiver;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
public final class PrivilegeChannel {
 static LocalSocket open(String request) throws IOException {
  if(!request.equals("PTP")&&!request.equals("STATUS"))throw new IOException("Invalid operation");
  LocalSocket s=new LocalSocket();try {
   s.connect(new LocalSocketAddress("/data/local/wpar-airplay/ptp.sock",LocalSocketAddress.Namespace.FILESYSTEM));
   s.setSoTimeout(3000);s.getOutputStream().write((request+"\n").getBytes("US-ASCII"));s.getOutputStream().flush();
   return s;
  } catch(IOException e){s.close();throw e;}
 }
 public static boolean available(){try(LocalSocket s=open("STATUS")){String r=new BufferedReader(new InputStreamReader(s.getInputStream(),"US-ASCII")).readLine();return r!=null&&r.startsWith("OK PTP_HELPER 1 UID "+android.os.Process.myUid());}catch(IOException e){return false;}}
}
