package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.api.Chat;
import com.example.myapplication.api.MessageRequest;
import com.example.myapplication.api.MessageResponse;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.util.Prefs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private EditText editTextMessage;
    private ImageButton buttonSend;
    
    private String chatId;
    private String targetUserId;
    private String contactName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

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

        if (chatId != null) {
            fetchMessages();
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
    }

    private void fetchMessages() {
        String token = "Bearer " + Prefs.getToken();
        RetrofitClient.getApiService().getMessages(token, chatId).enqueue(new Callback<List<MessageResponse>>() {
            @Override
            public void onResponse(Call<List<MessageResponse>> call, Response<List<MessageResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    messageList.clear();
                    for (MessageResponse msg : response.body()) {
                        boolean isMe = msg.getSenderId().equals(Prefs.getUserId());
                        messageList.add(new Message(msg.getCiphertext(), isMe, System.currentTimeMillis()));
                    }
                    adapter.notifyDataSetChanged();
                    if (messageList.size() > 0) {
                        recyclerView.smoothScrollToPosition(messageList.size() - 1);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<MessageResponse>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createChatAndSendMessage(String text) {
        String token = "Bearer " + Prefs.getToken();
        String currentUserId = Prefs.getUserId();
        
        // Create deterministic chat name: user_id1_user_id2 (sorted)
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
        String encryptedText = text; 

        MessageRequest request = new MessageRequest(chatId, Prefs.getUserId(), encryptedText, "text");
        RetrofitClient.getApiService().sendMessage(token, chatId, request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful()) {
                    messageList.add(new Message(text, true, System.currentTimeMillis()));
                    adapter.notifyItemInserted(messageList.size() - 1);
                    recyclerView.smoothScrollToPosition(messageList.size() - 1);
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