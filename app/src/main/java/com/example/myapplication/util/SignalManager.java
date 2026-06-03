package com.example.myapplication.util;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
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

    /**
     * Generates a deterministic X25519 keypair derived from account credentials.
     * This allows the same account to reconstruct the same identity keypair on any device.
     */
    public static KeyPairStrings generateAccountBoundKeyPair(String username, String password) throws Exception {
        try {
            byte[] seed = deriveAccountSeed(username, password);
            android.util.Log.d("SignalManager", "Generating deterministic identity keypair for " + username);

            // PKCS#8 header for X25519 private key (Scalar)
            byte[] pkcs8Header = {
                0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20
            };
            byte[] encodedPriv = new byte[pkcs8Header.length + seed.length];
            System.arraycopy(pkcs8Header, 0, encodedPriv, 0, pkcs8Header.length);
            System.arraycopy(seed, 0, encodedPriv, pkcs8Header.length, seed.length);
            
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            PrivateKey privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(encodedPriv));
            
            // Derive public key by performing DH with the X25519 base point (9)
            byte[] basePoint = new byte[32];
            basePoint[0] = 9;
            
            // X.509 SubjectPublicKeyInfo header for X25519 public key
            byte[] x509Header = {
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
            };
            byte[] encodedBasePoint = new byte[x509Header.length + basePoint.length];
            System.arraycopy(x509Header, 0, encodedBasePoint, 0, x509Header.length);
            System.arraycopy(basePoint, 0, encodedBasePoint, x509Header.length, basePoint.length);
            
            PublicKey basePointPub = kf.generatePublic(new X509EncodedKeySpec(encodedBasePoint));
            
            KeyAgreement ka = KeyAgreement.getInstance(ALGORITHM);
            ka.init(privKey);
            ka.doPhase(basePointPub, true);
            byte[] pubKeyRaw = ka.generateSecret();
            
            byte[] encodedPub = new byte[x509Header.length + pubKeyRaw.length];
            System.arraycopy(x509Header, 0, encodedPub, 0, x509Header.length);
            System.arraycopy(pubKeyRaw, 0, encodedPub, x509Header.length, pubKeyRaw.length);

            KeyPairStrings kps = new KeyPairStrings();
            kps.publicKey = Base64.encodeToString(encodedPub, Base64.NO_WRAP);
            kps.privateKey = Base64.encodeToString(encodedPriv, Base64.NO_WRAP);
            
            android.util.Log.d("SignalManager", "Deterministic keys generated successfully");
            return kps;
        } catch (Exception e) {
            android.util.Log.e("SignalManager", "Error generating account-bound keypair", e);
            throw e;
        }
    }

     private static byte[] deriveAccountSeed(String username, String password) throws Exception {
         try {
             String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
             String normalizedPassword = password == null ? "" : password;
             byte[] salt = ("com.example.myapplication.identity:" + normalizedUsername).getBytes(StandardCharsets.UTF_8);

             PBEKeySpec spec = new PBEKeySpec(
                     normalizedPassword.toCharArray(),
                     salt,
                     120_000,
                     256
             );

             try {
                 SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                 return factory.generateSecret(spec).getEncoded();
             } finally {
                 spec.clearPassword();
             }
         } catch (Exception e) {
             android.util.Log.e("SignalManager", "Error deriving account seed", e);
             throw e;
         }
     }

     /**
      * Encrypts identity private key with a password-derived encryption key.
      * Used for secure backup/restore of account-bound keys across devices.
      */
     public static String encryptIdentityPrivateKey(String identityPrivateKeyB64, String username, String password) throws Exception {
         try {
             byte[] encryptionKeyBytes = deriveAccountSeed(username, password);
             byte[] aesKey = new byte[16];
             System.arraycopy(encryptionKeyBytes, 0, aesKey, 0, 16);

             Cipher cipher = Cipher.getInstance(AES_GCM);
             byte[] iv = new byte[GCM_IV_LENGTH];
             new SecureRandom().nextBytes(iv);

             SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
             GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

             cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
             byte[] privateKeyBytes = Base64.decode(identityPrivateKeyB64, Base64.NO_WRAP);
             byte[] encryptedKey = cipher.doFinal(privateKeyBytes);

             byte[] combined = new byte[iv.length + encryptedKey.length];
             System.arraycopy(iv, 0, combined, 0, iv.length);
             System.arraycopy(encryptedKey, 0, combined, iv.length, encryptedKey.length);

             return Base64.encodeToString(combined, Base64.NO_WRAP);
         } catch (Exception e) {
             android.util.Log.e("SignalManager", "Error encrypting identity private key", e);
             throw e;
         }
     }

     /**
      * Decrypts identity private key encrypted with password-derived key.
      * Used for restoring account-bound keys from backup on new device.
      */
     public static String decryptIdentityPrivateKey(String encryptedKeyB64, String username, String password) throws Exception {
         try {
             byte[] decryptionKeyBytes = deriveAccountSeed(username, password);
             byte[] aesKey = new byte[16];
             System.arraycopy(decryptionKeyBytes, 0, aesKey, 0, 16);

             byte[] combined = Base64.decode(encryptedKeyB64, Base64.NO_WRAP);
             byte[] iv = new byte[GCM_IV_LENGTH];
             System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

             Cipher cipher = Cipher.getInstance(AES_GCM);
             SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
             GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

             cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
             byte[] decryptedKeyBytes = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);

             return Base64.encodeToString(decryptedKeyBytes, Base64.NO_WRAP);
         } catch (Exception e) {
             android.util.Log.e("SignalManager", "Error decrypting identity private key", e);
             throw e;
         }
     }

    public static List<String> generateOneTimePrekeys(int count) throws Exception {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(generateKeyPair().publicKey);
        }
        return keys;
    }

    private static byte[] deriveAESKeyHKDF(byte[] sharedSecret) throws Exception {
        byte[] salt = new byte[32];
        Mac hkdfExtract = Mac.getInstance("HmacSHA256");
        hkdfExtract.init(new SecretKeySpec(salt, 0, salt.length, "HmacSHA256"));
        byte[] prk = hkdfExtract.doFinal(sharedSecret);
        
        byte[] info = "AES_ENCRYPTION_KEY".getBytes();
        byte[] hashInput = new byte[info.length + 1];
        System.arraycopy(info, 0, hashInput, 0, info.length);
        hashInput[info.length] = 0x01;

        Mac hkdfExpand = Mac.getInstance("HmacSHA256");
        hkdfExpand.init(new SecretKeySpec(prk, 0, prk.length, "HmacSHA256"));
        byte[] t = hkdfExpand.doFinal(hashInput);

        byte[] aesKey = new byte[16];
        System.arraycopy(t, 0, aesKey, 0, 16);
        return aesKey;
    }

    private static byte[] deriveAESKeyLegacy(byte[] sharedSecret) {
        byte[] aesKey = new byte[16];
        System.arraycopy(sharedSecret, 0, aesKey, 0, 16);
        return aesKey;
    }

    public static String encrypt(String plaintext, byte[] sharedSecret) throws Exception {
        byte[] aesKey = deriveAESKeyHKDF(sharedSecret);

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
        byte[] combined = Base64.decode(base64Ciphertext, Base64.NO_WRAP);
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        try {
            byte[] aesKey = deriveAESKeyHKDF(sharedSecret);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
            return new String(plaintext);
        } catch (Exception hkdfException) {
            try {
                byte[] aesKey = deriveAESKeyLegacy(sharedSecret);
                Cipher cipher = Cipher.getInstance(AES_GCM);
                SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
                byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
                return new String(plaintext);
            } catch (Exception legacyException) {
                throw hkdfException;
            }
        }
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

    public static byte[] computeX3DHSharedSecret(
            String senderIdentityPrivate,
            String recipientIdentityPublic,
            String recipientSignedPrekeyPublic,
            String recipientOneTimePrekeyPublic) throws Exception {
        
        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        PrivateKey senderIdPriv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(senderIdentityPrivate, Base64.NO_WRAP)));
        PublicKey recipientIdPub = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientIdentityPublic, Base64.NO_WRAP)));
        PublicKey recipientSignedPrekeyPub = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientSignedPrekeyPublic, Base64.NO_WRAP)));
        PublicKey recipientOneTimePrekeyPub = recipientOneTimePrekeyPublic != null ? 
            kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientOneTimePrekeyPublic, Base64.NO_WRAP))) : null;
        
        KeyAgreement dh1 = KeyAgreement.getInstance(ALGORITHM);
        dh1.init(senderIdPriv);
        dh1.doPhase(recipientSignedPrekeyPub, true);
        byte[] secret1 = dh1.generateSecret();
        
        KeyAgreement dh2 = KeyAgreement.getInstance(ALGORITHM);
        dh2.init(senderIdPriv);
        dh2.doPhase(recipientIdPub, true);
        byte[] secret2 = dh2.generateSecret();
        
        byte[] secret3 = secret2;
        if (recipientOneTimePrekeyPub != null) {
            KeyAgreement dh3 = KeyAgreement.getInstance(ALGORITHM);
            dh3.init(senderIdPriv);
            dh3.doPhase(recipientOneTimePrekeyPub, true);
            secret3 = dh3.generateSecret();
        }
        
        byte[] combined = new byte[secret1.length + secret2.length + secret3.length];
        System.arraycopy(secret1, 0, combined, 0, secret1.length);
        System.arraycopy(secret2, 0, combined, secret1.length, secret2.length);
        System.arraycopy(secret3, 0, combined, secret1.length + secret2.length, secret3.length);
        
        byte[] salt = new byte[32];
        Mac kdf = Mac.getInstance("HmacSHA256");
        kdf.init(new SecretKeySpec(salt, 0, salt.length, "HmacSHA256"));
        return kdf.doFinal(combined);
    }
}