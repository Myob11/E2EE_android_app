package com.example.myapplication;

public class Conversation {
    private String chatId;
    private String targetUserId;
    private String contactName;
    private String lastMessage;
    private String time;
    private String imageUrl;
    private boolean isUnread;
    private String lastMessageTime; // Raw ISO timestamp for sorting

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
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isUnread() { return isUnread; }
    public void setUnread(boolean unread) { isUnread = unread; }

    public String getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(String lastMessageTime) { this.lastMessageTime = lastMessageTime; }
}