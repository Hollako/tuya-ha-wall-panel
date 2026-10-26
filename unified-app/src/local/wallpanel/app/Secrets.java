package local.wallpanel.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

final class Secrets {
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="wallpanel.configuration.v1";
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }
    static String encrypt(String value)throws Exception {
        if(value.length()==0)return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);
    }
    static String decrypt(String value)throws Exception {
        if(value.length()==0)return "";String[] p=value.split(":",-1);
        if(p.length!=2)throw new IllegalArgumentException("Invalid credential record");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),"UTF-8");
    }
    static String pinRecord(String pin)throws Exception {
        if(!pin.matches("[0-9]{6,12}"))throw new IllegalArgumentException("Use a PIN of 6 to 12 digits");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(derive(pin,salt),Base64.NO_WRAP);
    }
    static boolean verifyPin(String pin,String record)throws Exception {
        if(!pin.matches("[0-9]{6,12}"))return false;
        String[] p=record.split(":",-1);if(p.length!=2)return false;
        return MessageDigest.isEqual(derive(pin,Base64.decode(p[0],Base64.NO_WRAP)),Base64.decode(p[1],Base64.NO_WRAP));
    }
    private static byte[] derive(String pin,byte[] salt)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,120000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private Secrets(){}
}
