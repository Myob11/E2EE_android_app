package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.api.User;
import com.example.myapplication.util.Prefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchUsersActivity extends AppCompatActivity implements UsersAdapter.OnUserClickListener {

    private RecyclerView recyclerView;
    private UsersAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private Set<String> existingFriendIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users);

        Toolbar toolbar = findViewById(R.id.searchToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Find New Friends");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewUsers);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UsersAdapter(userList, this);
        recyclerView.setAdapter(adapter);

        // First fetch current friends to filter them out later
        fetchExistingFriends();

        SearchView searchView = findViewById(R.id.userSearchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchUsers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() > 2) {
                    searchUsers(newText);
                }
                return true;
            }
        });
    }

    private void fetchExistingFriends() {
        String token = "Bearer " + Prefs.getToken();
        String userId = Prefs.getUserId();
        RetrofitClient.getApiService().getFriends(token, userId).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (User user : response.body()) {
                        existingFriendIds.add(user.getId());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Log.e("SearchUsersActivity", "Failed to fetch friends", t);
            }
        });
    }

    private void searchUsers(String query) {
        String token = "Bearer " + Prefs.getToken();
        String currentUserId = Prefs.getUserId();
        
        RetrofitClient.getApiService().searchUsers(token, query).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    userList.clear();
                    for (User user : response.body()) {
                        // Filter out self AND existing friends
                        if (!user.getId().equals(currentUserId) && !existingFriendIds.contains(user.getId())) {
                            userList.add(user);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(SearchUsersActivity.this, "Error searching users", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAddFriendClick(User user) {
        String token = "Bearer " + Prefs.getToken();
        String currentUserId = Prefs.getUserId();
        
        Map<String, String> body = new HashMap<>();
        body.put("friend_id", user.getId());

        RetrofitClient.getApiService().addFriend(token, currentUserId, body).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SearchUsersActivity.this, "Friend added!", Toast.LENGTH_SHORT).show();
                    existingFriendIds.add(user.getId());
                    // Remove from current search results
                    userList.remove(user);
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(SearchUsersActivity.this, "Failed to add friend", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Toast.makeText(SearchUsersActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}