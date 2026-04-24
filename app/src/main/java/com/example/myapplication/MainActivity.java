package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements ConversationsAdapter.OnConversationClickListener {

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
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Initializing MainActivity");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chats");
        }

        recyclerView = findViewById(R.id.recyclerViewConversations);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConversationsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        
        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isSearching = true;
                Log.d(TAG, "SearchView focus gained: isSearching=true");
                performSearch("");
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "onQueryTextSubmit: query=" + query);
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (isSearching) {
                    Log.v(TAG, "onQueryTextChange: text=" + newText);
                    performSearch(newText);
                }
                return true;
            }
        });

        int closeButtonId = searchView.getContext().getResources().getIdentifier("android:id/search_close_btn", null, null);
        View closeButton = searchView.findViewById(closeButtonId);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "SearchView close button clicked");
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
                Conversation conv = new Conversation(friend.getUsername(), "Tap to chat", "", "https://i.pravatar.cc/150?u=" + friend.getUsername(), false);
                conv.setTargetUserId(friend.getId());
                if (friendToChatId.containsKey(friend.getId())) {
                    conv.setChatId(friendToChatId.get(friend.getId()));
                }
                searchResults.add(conv);
            }
        }
        Log.d(TAG, "performSearch: query='" + query + "', results=" + searchResults.size());
        adapter.updateData(searchResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: isSearching=" + isSearching);
        if (!isSearching) {
            loadData();
        }
    }

    private void loadData() {
        String token = "Bearer " + Prefs.getToken();
        String userId = Prefs.getUserId();
        
        Log.d(TAG, "loadData: Fetching friends for userId=" + userId);
        
        if (token == null || userId == null) {
            Log.e(TAG, "loadData: Token or UserId is null. User might be logged out.");
            return;
        }

        RetrofitClient.getApiService().getFriends(token, userId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    friendsList = response.body();
                    Log.d(TAG, "loadData: Friends list loaded, count=" + friendsList.size());
                    friendNames.clear();
                    for (User user : response.body()) {
                        friendNames.put(user.getId(), user.getUsername());
                    }
                } else {
                    Log.e(TAG, "loadData: Response failed. Code=" + response.code());
                }
                fetchChats();
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Log.e(TAG, "loadData: Network error while fetching friends", t);
                fetchChats();
            }
        });
    }

    private void fetchChats() {
        String token = "Bearer " + Prefs.getToken();
        String userId = Prefs.getUserId();
        
        Log.d(TAG, "fetchChats: Fetching chats for userId=" + userId);
        
        RetrofitClient.getApiService().getChats(token, userId).enqueue(new Callback<List<Chat>>() {
            @Override
            public void onResponse(Call<List<Chat>> call, Response<List<Chat>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Chat> chats = response.body();
                    Log.d(TAG, "fetchChats: Received " + chats.size() + " chats");

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

                        Conversation conv = new Conversation(name, "No messages yet", "", "https://i.pravatar.cc/150?u=" + name, false);
                        conv.setChatId(chat.getId());
                        conv.setTargetUserId(targetId);
                        conv.setLastMessageTime(chat.getCreatedAt());
                        chatConversations.add(conv);
                        
                        fetchLastMessage(conv);
                    }
                    
                    sortAndDisplayChats();
                } else {
                    Log.e(TAG, "fetchChats: Response failed. Code=" + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Chat>> call, Throwable t) {
                Log.e(TAG, "fetchChats: Network error", t);
                Toast.makeText(MainActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchLastMessage(Conversation conv) {
        String token = "Bearer " + Prefs.getToken();
        Log.v(TAG, "fetchLastMessage: Fetching for chatId=" + conv.getChatId());
        RetrofitClient.getApiService().getMessages(token, conv.getChatId(), 1, null).enqueue(new Callback<List<MessageResponse>>() {
            @Override
            public void onResponse(Call<List<MessageResponse>> call, Response<List<MessageResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    MessageResponse lastMsg = response.body().get(0);
                    conv.setLastMessage(lastMsg.getCiphertext());
                    conv.setLastMessageTime(lastMsg.getCreatedAt());
                    Log.v(TAG, "fetchLastMessage: Got message for " + conv.getChatId());
                    sortAndDisplayChats();
                }
            }

            @Override
            public void onFailure(Call<List<MessageResponse>> call, Throwable t) {
                Log.e(TAG, "fetchLastMessage: Error for " + conv.getChatId(), t);
            }
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
            Log.d(TAG, "sortAndDisplayChats: Updating adapter with " + chatConversations.size() + " conversations");
            adapter.updateData(chatConversations);
        }
    }

    @Override
    public void onConversationClick(Conversation conversation) {
        Log.d(TAG, "onConversationClick: Opening chat with " + conversation.getContactName() + ", chatId=" + conversation.getChatId());
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chatId", conversation.getChatId());
        intent.putExtra("targetUserId", conversation.getTargetUserId());
        intent.putExtra("contactName", conversation.getContactName());
        startActivity(intent);
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
            Log.d(TAG, "Menu action: Settings");
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_add_friend) {
            Log.d(TAG, "Menu action: Add Friend");
            startActivity(new Intent(this, SearchUsersActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
