package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ConversationsAdapter.OnConversationClickListener {

    private RecyclerView recyclerView;
    private ConversationsAdapter adapter;
    private List<Conversation> conversationList;

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

        // Mixed unread and read chats
        conversationList = new ArrayList<>();
        conversationList.add(new Conversation("John Doe", "Hey, how are you?", "10:30 AM", "https://i.pravatar.cc/150?u=john", true));
        conversationList.add(new Conversation("Jane Smith", "See you tonight!", "9:15 AM", "https://i.pravatar.cc/150?u=jane", false));
        conversationList.add(new Conversation("Alex Johnson", "The project is done.", "Yesterday", "https://i.pravatar.cc/150?u=alex", true));
        conversationList.add(new Conversation("Sarah Williams", "Happy Birthday!", "Monday", "https://i.pravatar.cc/150?u=sarah", false));

        adapter = new ConversationsAdapter(conversationList, this);
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return true;
            }
        });
    }

    @Override
    public void onConversationClick(Conversation conversation) {
        Intent intent = new Intent(this, ChatActivity.class);
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
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}