package com.example.myapplication.api;

import java.util.List;

public class Chat {
    private String id;
    private String name;
    private boolean is_group;
    private List<String> member_ids;
    private String created_at;

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isGroup() { return is_group; }
    public List<String> getMemberIds() { return member_ids; }
    public String getCreatedAt() { return created_at; }
}