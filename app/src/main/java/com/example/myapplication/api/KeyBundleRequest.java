package com.example.myapplication.api;

import java.util.List;

public class KeyBundleRequest {
    private String identity_key;
    private String signed_prekey;
    private List<String> one_time_prekeys;
    private int registration_id;
    private String device_id;

    public KeyBundleRequest(String identity_key, String signed_prekey, List<String> one_time_prekeys, int registration_id, String device_id) {
        this.identity_key = identity_key;
        this.signed_prekey = signed_prekey;
        this.one_time_prekeys = one_time_prekeys;
        this.registration_id = registration_id;
        this.device_id = device_id;
    }
}