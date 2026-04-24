package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.api.Chat;
import com.example.myapplication.api.MessageRequest;
import com.example.myapplication.api.MessageResponse;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.util.Prefs;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private List<String> loadedMessageIds = new ArrayList<>(); 
    private EditText editTextMessage;
    private ImageButton buttonSend;
    
    private String chatId;
    private String targetUserId;
    private String contactName;
    
    private boolean isLoading = false;
    private String oldestMessageTimestamp = null;
    private final int PAGE_SIZE = 20;

    private Handler pollHandler = new Handler();
    private Runnable pollRunnable;
    private final int POLL_INTERVAL = 3000;

    private SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        chatId = getIntent().getStringExtra("chatId");
        targetUserId = getIntent().getStringExtra("targetUserId");
        contactName = getIntent().getStringExtra("contactName");

        Toolbar toolbar = findViewById(R.id.chatToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(contactName != null ? contactName : "Chat");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewMessages);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);

        adapter = new MessagesAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy < 0 && !isLoading && oldestMessageTimestamp != null) {
                    if (layoutManager.findFirstVisibleItemPosition() <= 5) {
                        fetchMessages(oldestMessageTimestamp, false);
                    }
                }
            }
        });

        if (chatId != null) {
            fetchMessages(null, true);
        }

        buttonSend.setOnClickListener(v -> {
            String text = editTextMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                if (chatId == null) {
                    createChatAndSendMessage(text);
                } else {
                    sendMessage(text);
                }
                editTextMessage.setText("");
            }
        });

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (chatId != null && !isLoading) {
                    fetchMessages(null, false);
                }
                pollHandler.postDelayed(this, POLL_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void fetchMessages(String before, boolean isInitialLoad) {
        isLoading = true;
        String token = "Bearer " + Prefs.getToken();
        RetrofitClient.getApiService().getMessages(token, chatId, PAGE_SIZE, before).enqueue(new Callback<List<MessageResponse>>() {
            @Override
            public void onResponse(Call<List<MessageResponse>> call, Response<List<MessageResponse>> response) {
                isLoading = false;
                if (response.isSuccessful() && response.body() != null) {
                    List<MessageResponse> newResponses = response.body();
                    if (newResponses.isEmpty()) return;

                    if (before == null) {
                        boolean addedAny = false;
                        for (int i = newResponses.size() - 1; i >= 0; i--) {
                            MessageResponse res = newResponses.get(i);
                            
                            boolean isMe = res.getSenderId().equals(Prefs.getUserId());
                            
                            // 1. Mark incoming messages as read
                            if (!isMe && !res.isRead()) {
                                markAsRead(res.getId());
                                res.setRead(true); // Locally mark as read for UI
                            }

                            if (!loadedMessageIds.contains(res.getId())) {
                                Message msg = new Message(res.getId(), res.getCiphertext(), isMe, parseIsoDate(res.getCreatedAt()), res.isRead());
                                messageList.add(msg);
                                loadedMessageIds.add(res.getId());
                                addedAny = true;
                            } else {
                                // Update read status for existing messages (for sent message receipts)
                                updateExistingMessageReadStatus(res.getId(), res.isRead());
                            }
                        }

                        if (addedAny) {
                            adapter.updateMessages(messageList);
                            recyclerView.smoothScrollToPosition(messageList.size() - 1);
                        } else {
                            adapter.updateMessages(messageList); // Just refresh read statuses
                        }
                        
                        if (oldestMessageTimestamp == null || isInitialLoad) {
                            oldestMessageTimestamp = newResponses.get(newResponses.size() - 1).getCreatedAt();
                        }

                    } else {
                        List<Message> olderMessages = new ArrayList<>();
                        for (MessageResponse res : newResponses) {
                            if (!loadedMessageIds.contains(res.getId())) {
                                boolean isMe = res.getSenderId().equals(Prefs.getUserId());
                                if (!isMe && !res.isRead()) markAsRead(res.getId());
                                
                                olderMessages.add(new Message(res.getId(), res.getCiphertext(), isMe, parseIsoDate(res.getCreatedAt()), res.isRead()));
                                loadedMessageIds.add(res.getId());
                            }
                        }
                        Collections.reverse(olderMessages);
                        messageList.addAll(0, olderMessages);
                        adapter.updateMessages(messageList);
                        adapter.notifyItemRangeInserted(0, olderMessages.size());
                        oldestMessageTimestamp = newResponses.get(newResponses.size() - 1).getCreatedAt();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<MessageResponse>> call, Throwable t) {
                isLoading = false;
            }
        });
    }

    private void markAsRead(String messageId) {
        String token = "Bearer " + Prefs.getToken();
        RetrofitClient.getApiService().markAsRead(token, messageId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void updateExistingMessageReadStatus(String id, boolean isRead) {
        for (Message m : messageList) {
            if (m.getId() != null && m.getId().equals(id)) {
                if (m.isRead() != isRead) {
                    m.setRead(isRead);
                }
                break;
            }
        }
    }

    private long parseIsoDate(String isoDate) {
        try {
            Date date = isoFormat.parse(isoDate);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (ParseException e) {
            return System.currentTimeMillis();
        }
    }

    private void createChatAndSendMessage(String text) {
        String token = "Bearer " + Prefs.getToken();
        String currentUserId = Prefs.getUserId();
        List<String> ids = Arrays.asList(currentUserId, targetUserId);
        Collections.sort(ids);
        String chatName = ids.get(0) + "_" + ids.get(1);

        Map<String, Object> body = new HashMap<>();
        body.put("name", chatName);
        body.put("member_ids", ids);
        body.put("is_group", false);

        RetrofitClient.getApiService().createChat(token, body).enqueue(new Callback<Chat>() {
            @Override
            public void onResponse(Call<Chat> call, Response<Chat> response) {
                if (response.isSuccessful() && response.body() != null) {
                    chatId = response.body().getId();
                    sendMessage(text);
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to start chat", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Chat> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage(String text) {
        String token = "Bearer " + Prefs.getToken();
        MessageRequest request = new MessageRequest(chatId, Prefs.getUserId(), text, "text");
        RetrofitClient.getApiService().sendMessage(token, chatId, request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MessageResponse res = response.body();
                    if (!loadedMessageIds.contains(res.getId())) {
                        messageList.add(new Message(res.getId(), text, true, parseIsoDate(res.getCreatedAt()), false));
                        loadedMessageIds.add(res.getId());
                        adapter.updateMessages(messageList);
                        recyclerView.smoothScrollToPosition(messageList.size() - 1);
                    }
                }
            }
            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}