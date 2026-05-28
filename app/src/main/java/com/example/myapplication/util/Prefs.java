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
    private static String currentUserId = null;

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
            // CRITICAL: Do NOT fall back to unencrypted storage
            // This would silently lose private key encryption and expose keys to compromise
            android.util.Log.e("Prefs", "CRITICAL: Failed to initialize EncryptedSharedPreferences. " +
                    "This indicates either a compromised device, corrupted storage, or corrupted AndroidKeystore.", e);
            throw new RuntimeException(
                    "Failed to initialize secure storage. Cannot continue. " +
                    "Your device may be compromised or device storage may be corrupted. " +
                    "Please uninstall and reinstall the app or perform a factory reset.",
                    e
            );
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
        setCurrentUser(userId);
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

    // Signal Key Storage - Per-User
    public static void saveIdentityKeys(String pub, String priv) {
        sharedPreferences.edit()
                .putString(getUserKey(KEY_IDENTITY_PUB), pub)
                .putString(getUserKey(KEY_IDENTITY_PRIV), priv)
                .apply();
    }

    public static String getIdentityPubKey() { 
        return sharedPreferences.getString(getUserKey(KEY_IDENTITY_PUB), null); 
    }
    
    public static String getIdentityPrivKey() { 
        String key = sharedPreferences.getString(getUserKey(KEY_IDENTITY_PRIV), null);
        if (key == null) {
            android.util.Log.e("Prefs", "WARNING: Identity private key is null. Keys may have been lost during encryption initialization failure.");
        }
        return key;
    }

    public static void saveSignedPrekey(String pub, String priv) {
        sharedPreferences.edit()
                .putString(getUserKey(KEY_SIGNED_PREKEY_PUB), pub)
                .putString(getUserKey(KEY_SIGNED_PREKEY_PRIV), priv)
                .apply();
    }

    public static String getSignedPrekeyPub() {
        return sharedPreferences.getString(getUserKey(KEY_SIGNED_PREKEY_PUB), null);
    }

    public static String getSignedPrekeyPriv() {
        String key = sharedPreferences.getString(getUserKey(KEY_SIGNED_PREKEY_PRIV), null);
        if (key == null) {
            android.util.Log.e("Prefs", "WARNING: Signed prekey private key is null. Keys may have been lost during encryption initialization failure.");
        }
        return key;
    }

    public static void saveRegistrationId(int id) {
        sharedPreferences.edit().putInt(getUserKey(KEY_REGISTRATION_ID), id).apply();
    }

    public static int getRegistrationId() {
        return sharedPreferences.getInt(getUserKey(KEY_REGISTRATION_ID), 0);
    }
    
    public static void saveSharedSecret(String userId, String secret) {
        String key = currentUserId != null ? currentUserId + "_shared_secret_" + userId : "shared_secret_" + userId;
        sharedPreferences.edit().putString(key, secret).apply();
    }
    
    public static String getSharedSecret(String userId) {
        String key = currentUserId != null ? currentUserId + "_shared_secret_" + userId : "shared_secret_" + userId;
        return sharedPreferences.getString(key, null);
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
        clearCurrentUser();
    }
    public static void saveThemeMode(boolean darkMode) {
        sharedPreferences.edit().putBoolean("dark_mode", darkMode).apply();
    }

    public static boolean isDarkMode() {
        return sharedPreferences.getBoolean("dark_mode", false);
    }

    /**
     * Returns per-user namespaced key. When currentUserId is set, keys are stored per-user.
     * Falls back to legacy key name if currentUserId is null (backward compatibility).
     */
    private static String getUserKey(String baseKey) {
        if (currentUserId != null && !currentUserId.isEmpty()) {
            return currentUserId + "_" + baseKey;
        }
        return baseKey;
    }

    /**
     * Call this after login to set the current user context.
     * All cryptographic keys will be stored per-user after this call.
     * Also handles migration of legacy keys to per-user namespace.
     */
    public static void setCurrentUser(String userId) {
        currentUserId = userId;
        android.util.Log.d("Prefs", "Current user context set to: " + userId);

        // Migrate legacy keys to per-user namespace if they exist
        migrateLegacyKeysToPerUser();
    }

    /**
     * Clear current user context (on logout).
     */
    public static void clearCurrentUser() {
        currentUserId = null;
        android.util.Log.d("Prefs", "Current user context cleared");
    }

    /**
     * Migrates keys from legacy (non-namespaced) storage to per-user storage.
     * This ensures compatibility when upgrading from non-per-user key storage.
     */
    private static void migrateLegacyKeysToPerUser() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            return;
        }

        // Check if legacy keys exist and per-user keys don't
        String legacyIdentityPriv = sharedPreferences.getString(KEY_IDENTITY_PRIV, null);
        String legacySignedPrekeyPriv = sharedPreferences.getString(KEY_SIGNED_PREKEY_PRIV, null);
        int legacyRegistrationId = sharedPreferences.getInt(KEY_REGISTRATION_ID, 0);

        String perUserIdentityPriv = sharedPreferences.getString(currentUserId + "_" + KEY_IDENTITY_PRIV, null);

        // Only migrate if legacy keys exist and per-user keys don't
        if (legacyIdentityPriv != null && perUserIdentityPriv == null) {
            android.util.Log.d("Prefs", "Migrating legacy keys to per-user namespace for: " + currentUserId);

            // Migrate all cryptographic keys
            String legacyIdentityPub = sharedPreferences.getString(KEY_IDENTITY_PUB, null);
            String legacySignedPrekeyPub = sharedPreferences.getString(KEY_SIGNED_PREKEY_PUB, null);

            SharedPreferences.Editor editor = sharedPreferences.edit();

            if (legacyIdentityPub != null) {
                editor.putString(currentUserId + "_" + KEY_IDENTITY_PUB, legacyIdentityPub);
            }
            if (legacyIdentityPriv != null) {
                editor.putString(currentUserId + "_" + KEY_IDENTITY_PRIV, legacyIdentityPriv);
            }
            if (legacySignedPrekeyPub != null) {
                editor.putString(currentUserId + "_" + KEY_SIGNED_PREKEY_PUB, legacySignedPrekeyPub);
            }
            if (legacySignedPrekeyPriv != null) {
                editor.putString(currentUserId + "_" + KEY_SIGNED_PREKEY_PRIV, legacySignedPrekeyPriv);
            }
            if (legacyRegistrationId > 0) {
                editor.putInt(currentUserId + "_" + KEY_REGISTRATION_ID, legacyRegistrationId);
            }

            editor.apply();
            android.util.Log.d("Prefs", "Legacy key migration completed for: " + currentUserId);
        }
    }

}