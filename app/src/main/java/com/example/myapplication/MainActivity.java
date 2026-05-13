package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import com.example.myapplication.api.Chat;
import com.example.myapplication.api.MessageResponse;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.api.User;
import com.example.myapplication.util.Prefs;
import com.example.myapplication.util.SignalManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity implements
        ConversationsAdapter.OnConversationClickListener,
        ConversationsAdapter.OnConversationLongClickListener {

    private static final String TAG = "MainActivityDebug";

    private RecyclerView recyclerView;
    private ConversationsAdapter adapter;
    private List<Conversation> chatConversations = new ArrayList<>();
    private List<User> friendsList = new ArrayList<>();
    private Map<String, String> friendNames = new HashMap<>();
    private Map<String, String> friendToChatId = new HashMap<>();
    private boolean isSearching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);

        // Set status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary));
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
        }


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chats");
        }

        recyclerView = findViewById(R.id.recyclerViewConversations);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConversationsAdapter(new ArrayList<>(), this, this);
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        
        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isSearching = true;
                performSearch("");
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (isSearching) {
                    performSearch(newText);
                }
                return true;
            }
        });

        int closeButtonId = searchView.getContext().getResources().getIdentifier("android:id/search_close_btn", null, null);
        View closeButton = searchView.findViewById(closeButtonId);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                searchView.setQuery("", false);
                searchView.clearFocus();
                isSearching = false;
                adapter.updateData(chatConversations);
            });
        }
    }

    private void performSearch(String query) {
        List<User> sortedFriends = new ArrayList<>(friendsList);
        Collections.sort(sortedFriends, (u1, u2) -> u1.getUsername().compareToIgnoreCase(u2.getUsername()));

        List<Conversation> searchResults = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (User friend : sortedFriends) {
            if (lowerQuery.isEmpty() || friend.getUsername().toLowerCase().contains(lowerQuery)) {
                Conversation conv = new Conversation(friend.getUsername(), "Tap to chat", "", null, false);
                conv.setTargetUserId(friend.getId());
                if (friendToChatId.containsKey(friend.getId())) {
                    conv.setChatId(friendToChatId.get(friend.getId()));
                }
                searchResults.add(conv);
            }
        }
        adapter.updateData(searchResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSearching) {
            loadData();
        }
    }

    private void loadData() {
        String token = "Bearer " + Prefs.getToken();
        String userId = Prefs.getUserId();
        
        if (token == null || userId == null) return;

        RetrofitClient.getApiService().getFriends(token, userId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    friendsList = response.body();
                    friendNames.clear();
                    for (User user : friendsList) {
                        friendNames.put(user.getId(), user.getUsername());
                        
                        // Establish shared secret if we have their public key and don't have a secret yet
                        if (user.getPublic_key() != null && Prefs.getSharedSecret(user.getId()) == null) {
                            try {
                                byte[] secret = SignalManager.computeSharedSecret(Prefs.getIdentityPrivKey(), user.getPublic_key());
                                Prefs.saveSharedSecret(user.getId(), Base64.encodeToString(secret, Base64.NO_WRAP));
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to compute secret for " + user.getUsername(), e);
                            }
                        }
                    }
                }
                fetchChats();
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                fetchChats();
            }
        });
    }

    private void fetchChats() {
        String token = "Bearer " + Prefs.getToken();
        String userId = Prefs.getUserId();
        
        RetrofitClient.getApiService().getChats(token, userId).enqueue(new Callback<List<Chat>>() {
            @Override
            public void onResponse(Call<List<Chat>> call, Response<List<Chat>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Chat> chats = response.body();
                    chatConversations.clear();
                    friendToChatId.clear();
                    for (Chat chat : chats) {
                        String name = chat.getName();
                        String targetId = null;
                        
                        if (!chat.isGroup() && chat.getMemberIds() != null) {
                            for (String memberId : chat.getMemberIds()) {
                                if (!memberId.equals(userId)) {
                                    targetId = memberId;
                                    if (friendNames.containsKey(memberId)) {
                                        name = friendNames.get(memberId);
                                    }
                                    break;
                                }
                            }
                        }
                        
                        if (name == null || name.isEmpty() || name.contains("_")) {
                             name = "Chat";
                        }

                        if (targetId != null) {
                            friendToChatId.put(targetId, chat.getId());
                        }

                        Conversation conv = new Conversation(name, "No messages yet", "", null, false);
                        conv.setChatId(chat.getId());
                        conv.setTargetUserId(targetId);
                        conv.setLastMessageTime(chat.getCreatedAt());
                        chatConversations.add(conv);
                        
                        fetchLastMessage(conv);
                    }
                    sortAndDisplayChats();
                }
            }

            @Override
            public void onFailure(Call<List<Chat>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchLastMessage(Conversation conv) {
        String token = "Bearer " + Prefs.getToken();
        RetrofitClient.getApiService().getMessages(token, conv.getChatId(), 1, null).enqueue(new Callback<List<MessageResponse>>() {
            @Override
            public void onResponse(Call<List<MessageResponse>> call, Response<List<MessageResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    MessageResponse lastMsg = response.body().get(0);
                    conv.setLastMessage(lastMsg.getCiphertext());
                    conv.setLastMessageTime(lastMsg.getCreatedAt());
                    sortAndDisplayChats();
                }
            }

            @Override
            public void onFailure(Call<List<MessageResponse>> call, Throwable t) {}
        });
    }

    private void sortAndDisplayChats() {
        Collections.sort(chatConversations, (c1, c2) -> {
            String t1 = c1.getLastMessageTime();
            String t2 = c2.getLastMessageTime();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });
        
        if (!isSearching) {
            adapter.updateData(chatConversations);
        }
    }

    @Override
    public void onConversationClick(Conversation conversation) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chatId", conversation.getChatId());
        intent.putExtra("targetUserId", conversation.getTargetUserId());
        intent.putExtra("contactName", conversation.getContactName());
        startActivity(intent);
    }

    @Override
    public void onConversationLongClick(Conversation conversation) {
        if (conversation == null || conversation.getChatId() == null || conversation.getChatId().isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete chat")
                .setMessage("Delete this chat with " + conversation.getContactName() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteChat(conversation))
                .show();
    }

    private void deleteChat(Conversation conversation) {
        String token = "Bearer " + Prefs.getToken();
        String chatId = conversation.getChatId();

        RetrofitClient.getApiService().deleteChat(token, chatId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    handleChatDeleted(conversation, chatId);
                    return;
                }

                // Some deployments expose chat deletion as POST action routes.
                if (response.code() == 405 || response.code() == 404) {
                    tryDeleteChatViaPostAction(token, chatId, conversation);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to delete chat (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to delete chat", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void tryDeleteChatViaPostAction(String token, String chatId, Conversation conversation) {
        RetrofitClient.getApiService().deleteChatPostAction(token, chatId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    handleChatDeleted(conversation, chatId);
                    return;
                }

                if (response.code() == 404 || response.code() == 405) {
                    tryDeleteChatViaPostRemove(token, chatId, conversation);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to delete chat (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to delete chat", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void tryDeleteChatViaPostRemove(String token, String chatId, Conversation conversation) {
        RetrofitClient.getApiService().deleteChatPostRemove(token, chatId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    handleChatDeleted(conversation, chatId);
                } else {
                    Toast.makeText(MainActivity.this, "Delete endpoint not supported on server", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to delete chat", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleChatDeleted(Conversation conversation, String chatId) {
        chatConversations.removeIf(c -> chatId.equals(c.getChatId()));
        if (conversation.getTargetUserId() != null) {
            friendToChatId.remove(conversation.getTargetUserId());
        }

        if (isSearching) {
            performSearch("");
        } else {
            adapter.updateData(chatConversations);
        }
        Toast.makeText(MainActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_add_friend) {
            startActivity(new Intent(this, SearchUsersActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}