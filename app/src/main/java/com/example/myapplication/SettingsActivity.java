package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import com.bumptech.glide.Glide;
import com.example.myapplication.util.Prefs;

public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private ImageView imageViewAvatar;
    private RadioGroup radioGroupTheme;
    private SwitchCompat switchOnlineIndicator;
    private SwitchCompat switchAutoDownload;
    private TextView textViewUsernameDisplay;
    private Button buttonSwitchAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        imageViewAvatar = findViewById(R.id.imageViewAvatar);
        Button buttonUploadAvatar = findViewById(R.id.buttonUploadAvatar);
        radioGroupTheme = findViewById(R.id.radioGroupTheme);
        switchOnlineIndicator = findViewById(R.id.switchOnlineIndicator);
        switchAutoDownload = findViewById(R.id.switchAutoDownload);
        textViewUsernameDisplay = findViewById(R.id.textViewUsernameDisplay);
        buttonSwitchAccount = findViewById(R.id.buttonSwitchAccount);

        // Display current username
        String username = Prefs.getUsername();
        if (username != null) {
            textViewUsernameDisplay.setText(username);
        }

        // Load current profile picture based on username
        Glide.with(this)
                .load("https://i.pravatar.cc/150?u=" + username)
                .circleCrop()
                .into(imageViewAvatar);

        // Avatar Upload
        buttonUploadAvatar.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
        });

        // Theme Selection
        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioLight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.radioDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        // Switch Account (Logout)
        buttonSwitchAccount.setOnClickListener(v -> {
            Prefs.clear();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            Glide.with(this).load(imageUri).circleCrop().into(imageViewAvatar);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}