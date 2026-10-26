package local.wallpanel.app;
import java.util.*;
/** Fake Keystore: opaque tokens only in preferences. Not a cryptography test. */
final class Secrets {
    private static final Map<String,String> values=new HashMap<String,String>();
    static boolean fail;
    static String encrypt(String value)throws Exception {if(fail)throw new Exception("Fake keystore failure");if(value.length()==0)return "";String token="opaque_"+UUID.randomUUID();values.put(token,value);return token;}
    static String decrypt(String value)throws Exception {if(value.length()==0)return "";if(!values.containsKey(value))throw new Exception("Missing key");return values.get(value);}
    static String pinRecord(String value){return "fake_pin_hash";}
    static boolean verifyPin(String pin,String record){return false;}
}
