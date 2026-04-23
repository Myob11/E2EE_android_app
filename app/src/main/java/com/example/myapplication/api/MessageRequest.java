package com.example.myapplication.api;

public class MessageRequest {
    private String chat_id;
    private String sender_id;
    private String ciphertext;
    private String message_type;

    public MessageRequest(String chat_id, String sender_id, String ciphertext, String message_type) {
        this.chat_id = chat_id;
        this.sender_id = sender_id;
        this.ciphertext = ciphertext;
        this.message_type = message_type;
    }
}