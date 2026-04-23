package com.example.myapplication.api;

public class MessageResponse {
    private String id;
    private String chat_id;
    private String sender_id;
    private String ciphertext;
    private String message_type;
    private String created_at;
    private boolean is_read;

    public String getId() { return id; }
    public String getChatId() { return chat_id; }
    public String getSenderId() { return sender_id; }
    public String getCiphertext() { return ciphertext; }
    public String getMessageType() { return message_type; }
    public String getCreatedAt() { return created_at; }
    public boolean isRead() { return is_read; }
    public void setRead(boolean read) { is_read = read; }
}