package com.example.myapplication;

public class Message {
    private String content;
    private boolean isSentByMe;
    private long timestamp;

    public Message(String content, boolean isSentByMe, long timestamp) {
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.timestamp = timestamp;
    }

    public String getContent() { return content; }
    public boolean isSentByMe() { return isSentByMe; }
    public long getTimestamp() { return timestamp; }
}