package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
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
import com.example.myapplication.BuildConfig;
import com.example.myapplication.api.Chat;
import com.example.myapplication.api.KeyBundleResponse;
import com.example.myapplication.api.MessageRequest;
import com.example.myapplication.api.MessageResponse;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.util.Prefs;
import com.example.myapplication.util.SignalManager;
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

    private interface SharedSecretCallback {
        void onReady();
    }

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

    private Handler pollHandler = new Handler(Looper.getMainLooper());
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

    private String resolvePeerUserId(String senderId) {
        String myUserId = Prefs.getUserId();
        if (senderId == null) return targetUserId;
        if (myUserId != null && myUserId.equals(senderId)) return targetUserId;
        return senderId;
    }

    private String truncateKey(String value) {
        if (value == null) return "null";
        if (value.length() <= 16) return value;
        return value.substring(0, 8) + "..." + value.substring(value.length() - 8);
    }

    private void deriveAndCacheSharedSecret(String peerUserId, String peerPublicKey, SharedSecretCallback onReady, Runnable onFailure) {
        try {
            String myIdentityPriv = Prefs.getIdentityPrivKey();
            if (myIdentityPriv == null) {
                throw new IllegalStateException("Local identity private key is missing");
            }

            byte[] secret = SignalManager.computeSharedSecret(myIdentityPriv, peerPublicKey);
            String secretB64 = Base64.encodeToString(secret, Base64.NO_WRAP);
            Prefs.saveSharedSecret(peerUserId, secretB64);
            Log.d(TAG, "Derived shared secret for " + peerUserId + " = " + truncateKey(secretB64));
            if (onReady != null) onReady.onReady();
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive shared secret for " + peerUserId, e);
            if (onFailure != null) onFailure.run();
        }
    }

    private void fetchPeerIdentityAndCacheSecret(String peerUserId, SharedSecretCallback onReady, Runnable onFailure) {
        String token = Prefs.getToken();
        if (token == null) {
            if (onFailure != null) onFailure.run();
            return;
        }

        RetrofitClient.getApiService().getPublicKey(peerUserId).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String peerPublicKey = response.body().get("public_key");
                    if (peerPublicKey != null && !peerPublicKey.isEmpty()) {
                        deriveAndCacheSharedSecret(peerUserId, peerPublicKey, onReady, onFailure);
                        return;
                    }
                }

                Log.w(TAG, "Public key endpoint did not return a usable key for " + peerUserId + ", falling back to bundle endpoint");
                fetchPeerBundleAndCacheSecret(peerUserId, onReady, onFailure);
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Log.e(TAG, "Failed to fetch public key for " + peerUserId, t);
                fetchPeerBundleAndCacheSecret(peerUserId, onReady, onFailure);
            }
        });
    }

    private void fetchPeerBundleAndCacheSecret(String peerUserId, SharedSecretCallback onReady, Runnable onFailure) {
        String token = Prefs.getToken();
        if (token == null) {
            if (onFailure != null) onFailure.run();
            return;
        }

        RetrofitClient.getApiService().getKeyBundle("Bearer " + token, peerUserId).enqueue(new Callback<KeyBundleResponse>() {
            @Override
            public void onResponse(Call<KeyBundleResponse> call, Response<KeyBundleResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getIdentityKey() != null) {
                    deriveAndCacheSharedSecret(peerUserId, response.body().getIdentityKey(), onReady, onFailure);
                } else {
                    Log.e(TAG, "Failed to fetch key bundle for " + peerUserId + ", responseCode=" + response.code());
                    if (onFailure != null) onFailure.run();
                }
            }

            @Override
            public void onFailure(Call<KeyBundleResponse> call, Throwable t) {
                Log.e(TAG, "Failed to fetch key bundle for " + peerUserId, t);
                if (onFailure != null) onFailure.run();
            }
        });
    }

    private void ensureSharedSecretForPeer(String peerUserId, SharedSecretCallback onReady, Runnable onFailure) {
        if (peerUserId == null) {
            if (onFailure != null) onFailure.run();
            return;
        }

        String secretB64 = Prefs.getSharedSecret(peerUserId);
        if (secretB64 != null) {
            if (onReady != null) onReady.onReady();
            return;
        }

        fetchPeerIdentityAndCacheSecret(peerUserId, onReady, onFailure);
    }

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
                        fetchMessages(oldestMessageTimestamp, false);
                    }
                }
            }
        });

        if (chatId != null) {
            if (targetUserId != null && Prefs.getSharedSecret(targetUserId) == null) {
                updateStatusUI("Establishing secure connection...", true);
                ensureSharedSecretForPeer(targetUserId, () -> {
                    updateStatusUI("", false);
                    fetchMessages(null, true);
                    startWebSocket();
                }, () -> {
                    updateStatusUI("", false);
                    fetchMessages(null, true);
                    startWebSocket();
                });
            } else {
                fetchMessages(null, true);
                startWebSocket();
            }
        }

        buttonSend.setOnClickListener(v -> {
            String text = editTextMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                prepareAndSendMessage(text);
                editTextMessage.setText("");
            }
        });

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (chatId != null && !isLoading && !isWebSocketConnected) {
                    fetchMessages(null, false);
                }
                pollHandler.postDelayed(this, POLL_INTERVAL);
            }
        };
    }

    private void prepareAndSendMessage(String text) {
        if (targetUserId == null) {
            Toast.makeText(this, "Missing chat participant", Toast.LENGTH_SHORT).show();
            return;
        }

        ensureSharedSecretForPeer(targetUserId, () -> {
            String secretB64 = Prefs.getSharedSecret(targetUserId);
            if (secretB64 == null) {
                Toast.makeText(ChatActivity.this, "Failed to establish secure connection", Toast.LENGTH_SHORT).show();
                return;
            }

            updateStatusUI("", false);
            encryptAndSendMessage(text, Base64.decode(secretB64, Base64.NO_WRAP));
        }, () -> {
            updateStatusUI("", false);
            Toast.makeText(ChatActivity.this, "Failed to fetch security keys", Toast.LENGTH_SHORT).show();
        });
    }

    private void encryptAndSendMessage(String plaintext, byte[] secret) {
        try {
            String ciphertext = SignalManager.encrypt(plaintext, secret);
            if (chatId == null) {
                createChatAndSendCiphertext(ciphertext, plaintext);
            } else {
                sendCiphertext(ciphertext, plaintext);
            }
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            Toast.makeText(this, "Failed to encrypt message", Toast.LENGTH_SHORT).show();
        }
    }
    private String decryptSafely(String peerUserId, String ciphertext) {
        String secretB64 = Prefs.getSharedSecret(peerUserId);
        if (secretB64 == null) return "[Encrypted Message]";

        try {
            if (BuildConfig.DEBUG) {
                Log.d("CryptoDebug", "1. Incoming Ciphertext (B64): " + ciphertext);
                Log.d("CryptoDebug", "2. Shared Secret (B64): " + secretB64);
            }

            byte[] keyBytes = Base64.decode(secretB64, Base64.NO_WRAP);
            return SignalManager.decrypt(ciphertext, keyBytes);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);

            if (BuildConfig.DEBUG) {
                Log.e("CryptoDebug", "FAIL: Check if key or ciphertext above was modified/truncated.");
            }

            return "[Decryption Error]";
        }
    }

    private void handleNewMessage(MessageResponse res) {
        if (res == null || res.getId() == null) {
            return;
        }

        if (!loadedMessageIds.contains(res.getId())) {
            boolean isMe = Prefs.getUserId() != null && Prefs.getUserId().equals(res.getSenderId());
            String peerUserId = resolvePeerUserId(res.getSenderId());
            if (!isMe && !res.isRead()) {
                markAsRead(res.getId());
                res.setRead(true);
            }

            ensureSharedSecretForPeer(peerUserId, () -> runOnUiThread(() -> {
                String decryptedContent = decryptSafely(peerUserId, res.getCiphertext());
                Message msg = new Message(res.getId(), decryptedContent, isMe, parseIsoDate(res.getCreatedAt()), res.isRead());
                messageList.add(msg);
                loadedMessageIds.add(res.getId());
                adapter.updateMessages(messageList);
                recyclerView.smoothScrollToPosition(messageList.size() - 1);
            }), () -> runOnUiThread(() -> {
                Message msg = new Message(res.getId(), "[Encrypted Message]", isMe, parseIsoDate(res.getCreatedAt()), res.isRead());
                messageList.add(msg);
                loadedMessageIds.add(res.getId());
                adapter.updateMessages(messageList);
                recyclerView.smoothScrollToPosition(messageList.size() - 1);
            }));
        } else {
            updateExistingMessageReadStatus(res.getId(), res.isRead());
            adapter.updateMessages(messageList);
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

                    List<Message> processedMessages = new ArrayList<>();
                    for (int i = newResponses.size() - 1; i >= 0; i--) {
                        MessageResponse res = newResponses.get(i);
                        if (!loadedMessageIds.contains(res.getId())) {
                            boolean isMe = res.getSenderId().equals(Prefs.getUserId());
                            String peerUserId = resolvePeerUserId(res.getSenderId());
                            if (!isMe && !res.isRead()) {
                                markAsRead(res.getId());
                                res.setRead(true);
                            }
                            String content = decryptSafely(peerUserId, res.getCiphertext());
                            processedMessages.add(new Message(res.getId(), content, isMe, parseIsoDate(res.getCreatedAt()), res.isRead()));
                            loadedMessageIds.add(res.getId());
                        } else {
                            updateExistingMessageReadStatus(res.getId(), res.isRead());
                        }
                    }

                    if (before == null) {
                        messageList.addAll(processedMessages);
                        adapter.updateMessages(messageList);
                        recyclerView.smoothScrollToPosition(messageList.size() - 1);
                        if (oldestMessageTimestamp == null || isInitialLoad) {
                            oldestMessageTimestamp = newResponses.get(newResponses.size() - 1).getCreatedAt();
                        }
                    } else {
                        Collections.reverse(processedMessages);
                        messageList.addAll(0, processedMessages);
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

    private void sendCiphertext(String ciphertext, String originalPlaintext) {
        String token = "Bearer " + Prefs.getToken();
        MessageRequest request = new MessageRequest(chatId, Prefs.getUserId(), ciphertext, "text");
        RetrofitClient.getApiService().sendMessage(token, chatId, request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MessageResponse res = response.body();
                    if (!loadedMessageIds.contains(res.getId())) {
                        messageList.add(new Message(res.getId(), originalPlaintext, true, parseIsoDate(res.getCreatedAt()), false));
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

    private void createChatAndSendCiphertext(String ciphertext, String originalPlaintext) {
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
                    sendCiphertext(ciphertext, originalPlaintext);
                }
            }
            @Override
            public void onFailure(Call<Chat> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
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

    private void startWebSocket() {
        if (chatId == null || isWebSocketConnected || isWebSocketConnecting) return;
        
        isWebSocketConnecting = true;
        updateStatusUI("Connecting to Realtime...", true);
        
        String token = Prefs.getToken();
        String wsUrl = "wss://secra.top/ws/chats/" + chatId;

        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull okhttp3.Response response) {
                isWebSocketConnected = true;
                isWebSocketConnecting = false;
                updateStatusUI("Connected", false);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                runOnUiThread(() -> {
                    try {
                        JsonObject json = gson.fromJson(text, JsonObject.class);
                        String type = json.has("type") ? json.get("type").getAsString() : "";
                        if ("message.new".equals(type)) {
                            if (json.has("message")) {
                                handleNewMessage(gson.fromJson(json.get("message"), MessageResponse.class));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "WebSocket error", e);
                    }
                });
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                isWebSocketConnected = false;
                isWebSocketConnecting = false;
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, okhttp3.Response response) {
                isWebSocketConnected = false;
                isWebSocketConnecting = false;
            }
        });
    }

    private void updateStatusUI(String message, boolean visible) {
        runOnUiThread(() -> {
            if (textViewStatus != null) {
                textViewStatus.setText(message);
                textViewStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    private long parseIsoDate(String isoDate) {
        try {
            if (isoDate == null) return System.currentTimeMillis();
            Date date = isoFormat.parse(isoDate);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
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

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}