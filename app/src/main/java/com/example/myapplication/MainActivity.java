package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import com.example.myapplication.api.Chat;
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

    private RecyclerView recyclerView;
    private ConversationsAdapter adapter;
    private List<Conversation> chatConversations = new ArrayList<>();
    private List<User> friendsList = new ArrayList<>();
    private Map<String, String> friendNames = new HashMap<>();
    private Map<String, String> friendToChatId = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                return true;
            }
        });
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            adapter.updateData(chatConversations);
        } else {
            List<Conversation> searchResults = new ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();
            for (User friend : friendsList) {
                if (friend.getUsername().toLowerCase().contains(lowerQuery)) {
                    Conversation conv = new Conversation(friend.getUsername(), "Tap to chat", "", "https://i.pravatar.cc/150?u=" + friend.getUsername(), false);
                    conv.setTargetUserId(friend.getId());
                    if (friendToChatId.containsKey(friend.getId())) {
                        conv.setChatId(friendToChatId.get(friend.getId()));
                    }
                    searchResults.add(conv);
                }
            }
            adapter.updateData(searchResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
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
                    for (User user : response.body()) {
                        friendNames.put(user.getId(), user.getUsername());
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
                    
                    // Sort descending by created_at
                    Collections.sort(chats, (c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));

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

                        String lastMsg = "Tap to view messages";
                        String avatar = "https://i.pravatar.cc/150?u=" + name;
                        
                        Conversation conv = new Conversation(name, lastMsg, "", avatar, false);
                        conv.setChatId(chat.getId());
                        conv.setTargetUserId(targetId);
                        chatConversations.add(conv);
                    }
                    adapter.updateData(chatConversations);
                }
            }

            @Override
            public void onFailure(Call<List<Chat>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        });
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