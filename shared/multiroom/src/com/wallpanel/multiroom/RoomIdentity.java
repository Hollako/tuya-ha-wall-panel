package com.wallpanel.multiroom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Prove a moved room knows its existing API secret before sending that secret to its new IP. */
public final class RoomIdentity {
 public static String challenge(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return hex(bytes);}
 public static String proof(String token,String id,String challenge)throws Exception{
  if(!GroupPlan.validId(id)||challenge==null||!challenge.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid identity challenge");
  if(token.isEmpty())return "";Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return hex(mac.doFinal(("wallpanel-address-v1\n"+id+"\n"+challenge).getBytes(StandardCharsets.US_ASCII)));
 }
 public static boolean verify(String token,String id,String challenge,String proof)throws Exception{return !token.isEmpty()&&proof!=null&&proof.matches("[0-9a-f]{64}")&&MessageDigest.isEqual(proof(token,id,challenge).getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII));}
 private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(java.util.Locale.US,"%02x",value&255));return result.toString();}
 private RoomIdentity(){}
}
