package com.example.myapplication.util;

import android.content.Context;
import android.util.Base64;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SignalManager {
    private static final String ALGORITHM = "X25519";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public static class KeyPairStrings {
        public String publicKey;
        public String privateKey;
    }

    public static KeyPairStrings generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
        KeyPair kp = kpg.generateKeyPair();
        
        KeyPairStrings kps = new KeyPairStrings();
        kps.publicKey = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
        kps.privateKey = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
        return kps;
    }

    public static List<String> generateOneTimePrekeys(int count) throws Exception {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(generateKeyPair().publicKey);
        }
        return keys;
    }

    /**
     * Derives an AES key from a shared secret using HKDF-SHA256
     */
    private static byte[] deriveAESKey(byte[] sharedSecret) throws Exception {
        // HKDF-SHA256 Extract step
        byte[] salt = new byte[32]; // All zeros
        Mac hkdfExtract = Mac.getInstance("HmacSHA256");
        hkdfExtract.init(new SecretKeySpec(salt, 0, salt.length, "HmacSHA256"));
        byte[] prk = hkdfExtract.doFinal(sharedSecret);
        
        // HKDF-SHA256 Expand step (for 16 bytes = 128-bit AES key)
        byte[] info = "AES_ENCRYPTION_KEY".getBytes();
        byte[] hashInput = new byte[info.length + 1];
        System.arraycopy(info, 0, hashInput, 0, info.length);
        hashInput[info.length] = 0x01;

        Mac hkdfExpand = Mac.getInstance("HmacSHA256");
        hkdfExpand.init(new SecretKeySpec(prk, 0, prk.length, "HmacSHA256"));
        byte[] t = hkdfExpand.doFinal(hashInput);

        // Return first 16 bytes for AES-128
        byte[] aesKey = new byte[16];
        System.arraycopy(t, 0, aesKey, 0, 16);
        return aesKey;
    }

    public static String encrypt(String plaintext, byte[] sharedSecret) throws Exception {
        // Derive AES key from shared secret using HKDF
        byte[] aesKey = deriveAESKey(sharedSecret);
        
        Cipher cipher = Cipher.getInstance(AES_GCM);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public static String decrypt(String base64Ciphertext, byte[] sharedSecret) throws Exception {
        // Derive AES key from shared secret using HKDF
        byte[] aesKey = deriveAESKey(sharedSecret);
        
        byte[] combined = Base64.decode(base64Ciphertext, Base64.NO_WRAP);
        
        // Extract the IV from the combined buffer
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
        
        return new String(plaintext);
    }

    public static byte[] computeSharedSecret(String privateKeyStr, String publicKeyStr) throws Exception {
        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateKeyStr, Base64.NO_WRAP)));
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(publicKeyStr, Base64.NO_WRAP)));
        
        KeyAgreement ka = KeyAgreement.getInstance(ALGORITHM);
        ka.init(privateKey);
        ka.doPhase(publicKey, true);
        return ka.generateSecret();
    }
}