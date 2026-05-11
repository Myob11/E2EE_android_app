package com.example.myapplication.api;

public class KeyBundleResponse {
    private String user_id;
    private String identity_key;
    private String signed_prekey;
    private String one_time_prekey;
    private int registration_id;
    private String device_id;

    public String getUserId() { return user_id; }
    public String getIdentityKey() { return identity_key; }
    public String getSignedPrekey() { return signed_prekey; }
    public String getOneTimePrekey() { return one_time_prekey; }
    public int getRegistrationId() { return registration_id; }
    public String getDeviceId() { return device_id; }
}