package com.example.myapplication.api;

import java.util.List;

public class KeyBundleRequest {
    private String identity_key;
    private String signed_prekey;
    private List<String> one_time_prekeys;
    private int registration_id;
    private String device_id;
    private String encrypted_identity_private_key; // For secure backup/restore across devices

    public KeyBundleRequest(String identity_key, String signed_prekey, List<String> one_time_prekeys, int registration_id, String device_id) {
        this(identity_key, signed_prekey, one_time_prekeys, registration_id, device_id, null);
    }

    public KeyBundleRequest(String identity_key, String signed_prekey, List<String> one_time_prekeys, int registration_id, String device_id, String encrypted_identity_private_key) {
        this.identity_key = identity_key;
        this.signed_prekey = signed_prekey;
        this.one_time_prekeys = one_time_prekeys;
        this.registration_id = registration_id;
        this.device_id = device_id;
        this.encrypted_identity_private_key = encrypted_identity_private_key;
    }

    public String getEncryptedIdentityPrivateKey() { return encrypted_identity_private_key; }
}