package com.example.myapplication.api;

public class AuthResponse {
    private String access_token;
    private String token_type;
    private String id;
    private String username;
    private String public_key;

    public String getAccess_token() { return access_token; }
    public String getToken_type() { return token_type; }
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPublic_key() { return public_key; }
}