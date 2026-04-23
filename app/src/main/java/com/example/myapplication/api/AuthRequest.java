package com.example.myapplication.api;

public class AuthRequest {
    private String username;
    private String password;
    private String public_key;

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public AuthRequest(String username, String password, String public_key) {
        this.username = username;
        this.password = password;
        this.public_key = public_key;
    }
}