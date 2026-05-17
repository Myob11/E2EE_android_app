package com.example.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class Prefs {
    private static final String PREF_NAME = "secure_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    
    private static final String KEY_IDENTITY_PUB = "identity_pub";
    private static final String KEY_IDENTITY_PRIV = "identity_priv";
    private static final String KEY_SIGNED_PREKEY_PUB = "signed_prekey_pub";
    private static final String KEY_SIGNED_PREKEY_PRIV = "signed_prekey_priv";
    private static final String KEY_REGISTRATION_ID = "registration_id";

    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void saveToken(String token) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public static void saveUserId(String userId) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public static void saveUsername(String username) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply();
    }

    public static String getUsername() {
        return sharedPreferences.getString(KEY_USERNAME, null);
    }

    // Signal Key Storage
    public static void saveIdentityKeys(String pub, String priv) {
        sharedPreferences.edit()
                .putString(KEY_IDENTITY_PUB, pub)
                .putString(KEY_IDENTITY_PRIV, priv)
                .apply();
    }

    public static String getIdentityPubKey() { return sharedPreferences.getString(KEY_IDENTITY_PUB, null); }
    public static String getIdentityPrivKey() { return sharedPreferences.getString(KEY_IDENTITY_PRIV, null); }

    public static void saveSignedPrekey(String pub, String priv) {
        sharedPreferences.edit()
                .putString(KEY_SIGNED_PREKEY_PUB, pub)
                .putString(KEY_SIGNED_PREKEY_PRIV, priv)
                .apply();
    }

    public static String getSignedPrekeyPub() { return sharedPreferences.getString(KEY_SIGNED_PREKEY_PUB, null); }
    public static String getSignedPrekeyPriv() { return sharedPreferences.getString(KEY_SIGNED_PREKEY_PRIV, null); }

    public static void saveRegistrationId(int id) {
        sharedPreferences.edit().putInt(KEY_REGISTRATION_ID, id).apply();
    }

    public static int getRegistrationId() {
        return sharedPreferences.getInt(KEY_REGISTRATION_ID, 0);
    }
    
    public static void saveSharedSecret(String userId, String secret) {
        sharedPreferences.edit().putString("shared_secret_" + userId, secret).apply();
    }
    
    public static String getSharedSecret(String userId) {
        return sharedPreferences.getString("shared_secret_" + userId, null);
    }

    public static void clear() {
        sharedPreferences.edit().clear().apply();
    }

    /**
     * Clear only session data (token, user_id, username) while preserving device keys
     * (identity keys, signed prekey, registration ID) and shared secrets. This ensures
     * that device keys persist across login/logout cycles and messages encrypted with
     * old keys can still be decrypted.
     */
    public static void clearSessionOnly() {
        sharedPreferences.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .apply();
    }
    public static void saveThemeMode(boolean darkMode) {
        sharedPreferences.edit().putBoolean("dark_mode", darkMode).apply();
    }

    public static boolean isDarkMode() {
        return sharedPreferences.getBoolean("dark_mode", false);
    }

}