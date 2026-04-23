package com.example.myapplication;

public class Message {
    private String id;
    private String content;
    private boolean isSentByMe;
    private long timestamp;
    private boolean isRead;

    public Message(String content, boolean isSentByMe, long timestamp) {
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.timestamp = timestamp;
    }

    public Message(String id, String content, boolean isSentByMe, long timestamp, boolean isRead) {
        this.id = id;
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public boolean isSentByMe() { return isSentByMe; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}