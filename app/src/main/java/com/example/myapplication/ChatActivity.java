package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
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
import com.example.myapplication.util.ProfileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivityDebug";

    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private List<String> loadedMessageIds = new ArrayList<>(); 
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private TextView textViewStatus;
    
    private String chatId;
    private String targetUserId;
    private String contactName;
    
    private boolean isLoading = false;
    private String oldestMessageTimestamp = null;
    private final int PAGE_SIZE = 20;

    private Handler pollHandler = new Handler();
    private Runnable pollRunnable;
    private final int POLL_INTERVAL = 2000;

    private WebSocket webSocket;
    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build();
            
    private boolean isWebSocketConnected = false;
    private boolean isWebSocketConnecting = false;
    private Gson gson = new Gson();

    private SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        chatId = getIntent().getStringExtra("chatId");
        targetUserId = getIntent().getStringExtra("targetUserId");
        contactName = getIntent().getStringExtra("contactName");

        Log.d(TAG, "onCreate: chatId=" + chatId + ", targetUserId=" + targetUserId + ", contactName=" + contactName);

        Toolbar toolbar = findViewById(R.id.chatToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(contactName != null ? contactName : "Chat");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewMessages);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        textViewStatus = findViewById(R.id.textViewStatus);

        adapter = new MessagesAdapter(messageList);
        if (contactName != null) {
            adapter.setOtherUsername(contactName);
        }
        
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
                        Log.d(TAG, "onScrolled: Fetching older messages. oldestTimestamp=" + oldestMessageTimestamp);
                        fetchMessages(oldestMessageTimestamp, false);
                    }
                }
            }
        });

        if (chatId != null) {
            Log.d(TAG, "Initial load: Fetching messages and starting WebSocket");
            fetchMessages(null, true);
            startWebSocket();
        } else {
            Log.d(TAG, "No chatId yet. Waiting for first message to create chat.");
        }

        buttonSend.setOnClickListener(v -> {
            String text = editTextMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                if (chatId == null) {
                    Log.d(TAG, "Send button: Creating chat and sending message");
                    createChatAndSendMessage(text);
                } else {
                    Log.d(TAG, "Send button: Sending message");
                    sendMessage(text);
                }
                editTextMessage.setText("");
            }
        });


        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (chatId != null && !isLoading && !isWebSocketConnected) {
                    Log.d(TAG, "Polling Fallback (2s): WebSocket is disconnected. Fetching messages via HTTP.");
                    fetchMessages(null, false);
                } else if (isWebSocketConnected) {
                    Log.v(TAG, "Polling: WebSocket is connected. Skipping polling fetch.");
                }
                pollHandler.postDelayed(this, POLL_INTERVAL);
            }
        };
    }

    private void updateStatusUI(String message, boolean visible) {
        runOnUiThread(() -> {
            if (textViewStatus != null) {
                textViewStatus.setText(message);
                textViewStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void startWebSocket() {
        if (chatId == null || isWebSocketConnected || isWebSocketConnecting) {
            Log.d(TAG, "startWebSocket: Skipping connection. chatId=" + chatId + ", connected=" + isWebSocketConnected + ", connecting=" + isWebSocketConnecting);
            return;
        }
        
        isWebSocketConnecting = true;
        updateStatusUI("Connecting to Realtime...", true);
        
        String token = Prefs.getToken();
        String wsUrl = "wss://secra.top/ws/chats/" + chatId;

        Log.d(TAG, "WebSocket: Connecting to " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull okhttp3.Response response) {
                isWebSocketConnected = true;
                isWebSocketConnecting = false;
                Log.i(TAG, "WebSocket: Connection OPENED");
                updateStatusUI("Connected", false);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "WebSocket: MESSAGE RECEIVED: " + text);
                runOnUiThread(() -> {
                    try {
                        JsonObject json = gson.fromJson(text, JsonObject.class);
                        String type = json.has("type") ? json.get("type").getAsString() : "";
                        
                        if ("message.new".equals(type)) {
                            Log.d(TAG, "WebSocket: New message notification received");
                            if (json.has("message")) {
                                MessageResponse res = gson.fromJson(json.get("message"), MessageResponse.class);
                                handleNewMessage(res);
                            } else {
                                MessageResponse res = gson.fromJson(json, MessageResponse.class);
                                if (res.getId() != null) {
                                    handleNewMessage(res);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "WebSocket: Error parsing message JSON", e);
                    }
                });
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                isWebSocketConnected = false;
                isWebSocketConnecting = false;
                updateStatusUI("Realtime Disconnected. Polling active (2s).", true);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, okhttp3.Response response) {
                isWebSocketConnected = false;
                isWebSocketConnecting = false;
                updateStatusUI("Realtime Connection Failed. Polling active (2s).", true);
            }
        });
    }

    private void handleNewMessage(MessageResponse res) {
        if (res == null || res.getId() == null) return;

        if (!loadedMessageIds.contains(res.getId())) {
            boolean isMe = res.getSenderId().equals(Prefs.getUserId());
            if (!isMe && !res.isRead()) {
                markAsRead(res.getId());
                res.setRead(true);
            }

            Message msg = new Message(res.getId(), res.getCiphertext(), isMe, parseIsoDate(res.getCreatedAt()), res.isRead());
            messageList.add(msg);
            loadedMessageIds.add(res.getId());
            adapter.updateMessages(messageList);
            recyclerView.smoothScrollToPosition(messageList.size() - 1);
        } else {
            updateExistingMessageReadStatus(res.getId(), res.isRead());
            adapter.updateMessages(messageList);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
        if (chatId != null && !isWebSocketConnected && !isWebSocketConnecting) {
            startWebSocket();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "Activity paused");
            isWebSocketConnected = false;
            isWebSocketConnecting = false;
        }
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
                            if (!isMe && !res.isRead()) {
                                markAsRead(res.getId());
                                res.setRead(true);
                            }
                            if (!loadedMessageIds.contains(res.getId())) {
                                Message msg = new Message(res.getId(), res.getCiphertext(), isMe, parseIsoDate(res.getCreatedAt()), res.isRead());
                                messageList.add(msg);
                                loadedMessageIds.add(res.getId());
                                addedAny = true;
                            } else {
                                updateExistingMessageReadStatus(res.getId(), res.isRead());
                            }
                        }

                        if (addedAny) {
                            adapter.updateMessages(messageList);
                            recyclerView.smoothScrollToPosition(messageList.size() - 1);
                        } else {
                            adapter.updateMessages(messageList);
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
                m.setRead(isRead);
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
                    startWebSocket();
                    sendMessage(text);
                }
            }
            @Override
            public void onFailure(Call<Chat> call, Throwable t) {}
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
            public void onFailure(Call<MessageResponse> call, Throwable t) {}
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
