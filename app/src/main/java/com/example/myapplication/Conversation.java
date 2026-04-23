package com.example.myapplication;

public class Conversation {
    private String chatId;
    private String targetUserId;
    private String contactName;
    private String lastMessage;
    private String time;
    private String imageUrl;
    private boolean isUnread;

    public Conversation(String contactName, String lastMessage, String time, String imageUrl, boolean isUnread) {
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.time = time;
        this.imageUrl = imageUrl;
        this.isUnread = isUnread;
    }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    
    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public String getContactName() { return contactName; }
    public String getLastMessage() { return lastMessage; }
    public String getTime() { return time; }
    public String getImageUrl() { return imageUrl; }
    public boolean isUnread() { return isUnread; }
    public void setUnread(boolean unread) { isUnread = unread; }
}