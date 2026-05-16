package com.example.myapplication.util;

import android.content.Context;
import android.util.Base64;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
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
import android.util.Log;

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
    private static byte[] deriveAESKeyHKDF(byte[] sharedSecret) throws Exception {
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

    /**
     * Legacy method: extracts first 16 bytes directly from shared secret
     */
    private static byte[] deriveAESKeyLegacy(byte[] sharedSecret) {
        byte[] aesKey = new byte[16];
        System.arraycopy(sharedSecret, 0, aesKey, 0, 16);
        return aesKey;
    }

    public static String encrypt(String plaintext, byte[] sharedSecret) throws Exception {
        // Use HKDF for new encryptions
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

        // Debugging info (safe: do NOT log raw secrets in production)
        try {
            Log.d("CryptoDebug", "ENCRYPT: ivHex=" + toHex(iv)
                    + " ciphertextLen=" + ciphertext.length
                    + " combinedLen=" + combined.length
                    + " sharedSecretSHA256=" + sha256Hex(sharedSecret)
                    + " aesKeySHA256=" + sha256Hex(aesKey));
        } catch (Exception ignored) {}

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public static String decrypt(String base64Ciphertext, byte[] sharedSecret) throws Exception {
        if (base64Ciphertext == null) {
            throw new IllegalArgumentException("base64Ciphertext is null");
        }

        byte[] combined;
        try {
            combined = Base64.decode(base64Ciphertext, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            Log.e("CryptoDebug", "DECRYPT: Base64.decode failed. inputLen=" + base64Ciphertext.length(), e);
            throw e;
        }

        if (combined == null || combined.length < (GCM_IV_LENGTH + 16)) {
            Log.e("CryptoDebug", "DECRYPT: combined too short. len=" +
                    (combined == null ? -1 : combined.length));
            throw new IllegalArgumentException("Invalid ciphertext payload length for AES-GCM");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        try {
            Log.d("CryptoDebug", "DECRYPT: incomingCombinedLen=" + combined.length
                    + " ivHex=" + toHex(iv)
                    + " sharedSecretSHA256=" + sha256Hex(sharedSecret));
        } catch (Exception ignored) {
        }

        // First try HKDF-derived key
        try {
            byte[] aesKey = deriveAESKeyHKDF(sharedSecret);
            Log.d("CryptoDebug", "DECRYPT: trying HKDF key, aesKeySHA256=" + sha256Hex(aesKey));

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
            return new String(plaintext);
        } catch (Exception hkdfException) {
            Log.e("CryptoDebug", "DECRYPT: HKDF-based decryption failed: "
                    + hkdfException.getClass().getSimpleName()
                    + " msg=" + hkdfException.getMessage(), hkdfException);

            // Fallback to legacy key derivation
            try {
                byte[] aesKey = deriveAESKeyLegacy(sharedSecret);
                Log.d("CryptoDebug", "DECRYPT: trying legacy key, aesKeySHA256=" + sha256Hex(aesKey));

                Cipher cipher = Cipher.getInstance(AES_GCM);
                SecretKeySpec keySpec = new SecretKeySpec(aesKey, 0, 16, "AES");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

                byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
                return new String(plaintext);
            } catch (Exception legacyException) {
                Log.e("CryptoDebug", "DECRYPT: legacy decryption also failed: "
                        + legacyException.getClass().getSimpleName()
                        + " msg=" + legacyException.getMessage(), legacyException);
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

    /**
     * Implements X3DH-style key agreement using recipient's identity, signed prekey, and one-time prekey.
     * This combines multiple ECDH operations for a more robust shared secret.
     */
    public static byte[] computeX3DHSharedSecret(
            String senderIdentityPrivate,
            String recipientIdentityPublic,
            String recipientSignedPrekeyPublic,
            String recipientOneTimePrekeyPublic) throws Exception {
        
        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        
        // Convert keys
        PrivateKey senderIdPriv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(senderIdentityPrivate, Base64.NO_WRAP)));
        PublicKey recipientIdPub = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientIdentityPublic, Base64.NO_WRAP)));
        PublicKey recipientSignedPrekeyPub = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientSignedPrekeyPublic, Base64.NO_WRAP)));
        PublicKey recipientOneTimePrekeyPub = recipientOneTimePrekeyPublic != null ? 
            kf.generatePublic(new X509EncodedKeySpec(Base64.decode(recipientOneTimePrekeyPublic, Base64.NO_WRAP))) : null;
        
        // Perform ECDH operations
        // DH1: sender's identity key with recipient's signed prekey
        KeyAgreement dh1 = KeyAgreement.getInstance(ALGORITHM);
        dh1.init(senderIdPriv);
        dh1.doPhase(recipientSignedPrekeyPub, true);
        byte[] secret1 = dh1.generateSecret();
        
        // DH2: sender's identity key with recipient's identity key (fallback for compatibility)
        KeyAgreement dh2 = KeyAgreement.getInstance(ALGORITHM);
        dh2.init(senderIdPriv);
        dh2.doPhase(recipientIdPub, true);
        byte[] secret2 = dh2.generateSecret();
        
        // DH3: if one-time prekey is available, use it; otherwise just use the above
        byte[] secret3 = secret2; // fallback
        if (recipientOneTimePrekeyPub != null) {
            KeyAgreement dh3 = KeyAgreement.getInstance(ALGORITHM);
            dh3.init(senderIdPriv);
            dh3.doPhase(recipientOneTimePrekeyPub, true);
            secret3 = dh3.generateSecret();
        }
        
        // Combine all secrets: DH1 || DH2 || DH3
        byte[] combined = new byte[secret1.length + secret2.length + secret3.length];
        System.arraycopy(secret1, 0, combined, 0, secret1.length);
        System.arraycopy(secret2, 0, combined, secret1.length, secret2.length);
        System.arraycopy(secret3, 0, combined, secret1.length + secret2.length, secret3.length);
        
        // Apply KDF to the combined secret
        byte[] salt = new byte[32]; // All zeros
        Mac kdf = Mac.getInstance("HmacSHA256");
        kdf.init(new SecretKeySpec(salt, 0, salt.length, "HmacSHA256"));
        return kdf.doFinal(combined);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(data));
        } catch (Exception e) {
            return "sha256-fail";
        }
    }

    // za debugging zbrisi v produkciji
    public static boolean selfTestRoundTrip(String plain, byte[] sharedSecret) {
        try {
            String c = encrypt(plain, sharedSecret);
            String p = decrypt(c, sharedSecret);
            return plain.equals(p);
        } catch (Exception e) {
            Log.e("CryptoDebug", "selfTestRoundTrip failed", e);
            return false;
        }
    }
}