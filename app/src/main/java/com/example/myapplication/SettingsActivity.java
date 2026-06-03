package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.util.Prefs;
import com.example.myapplication.util.ProfileUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivityDebug";
    private static final int PICK_IMAGE_REQUEST = 1;
    
    private ImageView imageViewAvatar;
    private SwitchCompat switchTheme;
    private TextView textViewUsernameDisplay;
    private Button buttonSwitchAccount;
    private Button buttonDeleteProfile;

    private void applyStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure currentUserId is set for proper namespacing of user-scoped preferences
        // This is critical for account-bound encryption on shared devices
        String userId = Prefs.getUserId();
        if (userId != null) {
            Prefs.setCurrentUser(userId);
        }

        setContentView(R.layout.activity_settings);
        applyStatusBar();

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        imageViewAvatar = findViewById(R.id.imageViewAvatar);
        switchTheme = findViewById(R.id.switchTheme);
        textViewUsernameDisplay = findViewById(R.id.textViewUsernameDisplay);
        buttonSwitchAccount = findViewById(R.id.buttonSwitchAccount);
        buttonDeleteProfile = findViewById(R.id.buttonDeleteProfile);

        // Theme Switch Logic
        boolean isDark = Prefs.isDarkMode();
        switchTheme.setChecked(isDark);
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != Prefs.isDarkMode()) {
                Prefs.saveThemeMode(isChecked);
                AppCompatDelegate.setDefaultNightMode(
                        isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                );
                applyStatusBar();
                recreate(); 
            }
        });

        String username = Prefs.getUsername();
        if (username != null) {
            textViewUsernameDisplay.setText(username);
        }

        ProfileUtils.loadProfilePicture(this, username, imageViewAvatar);

        // Profile Picture Upload
        imageViewAvatar.setOnLongClickListener(v -> {
            AlertDialog avatarDialog = new AlertDialog.Builder(this)
                    .setTitle("Profile Picture")
                    .setMessage("Do you want to upload a new profile picture?")
                    .setPositiveButton("Yes", (dialog, which) -> openGallery())
                    .setNegativeButton("No", null)
                    .show();
            tintDialogButtons(avatarDialog);
            return true;
        });

        // Switch Account Logic
        buttonSwitchAccount.setOnClickListener(v -> {
            // Preserves namespaced keys of current user but clears active session
            Prefs.clearSessionOnly();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        buttonDeleteProfile.setOnClickListener(v -> showDeleteConfirmationDialog());
    }

    private void showDeleteConfirmationDialog() {
        EditText input = new EditText(this);
        input.setHint("Type DELETE to confirm");

        AlertDialog confirmDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Profile")
                .setMessage("This will permanently delete your account and all associated data. Type DELETE (all caps) to confirm.")
                .setView(input)
                .setPositiveButton("Delete", (dialog, which) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if ("DELETE".equals(text)) {
                        performAccountDeletion();
                    } else {
                        Toast.makeText(SettingsActivity.this, "Confirmation text mismatch. Type DELETE to confirm.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        tintDialogButtons(confirmDialog);
    }

    private void performAccountDeletion() {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Deleting account")
                .setMessage("Please wait while your account is being deleted...")
                .setCancelable(false)
                .create();
        progress.show();

        String token = "Bearer " + Prefs.getToken();
        RetrofitClient.getApiService().deleteMe(token).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progress.dismiss();
                if (response.isSuccessful()) {
                    AlertDialog deletedDialog = new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Account Deleted")
                            .setMessage("Your account has been deleted successfully.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                Prefs.clear(); // Complete wipe as account is gone
                                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                    tintDialogButtons(deletedDialog);
                } else {
                    Toast.makeText(SettingsActivity.this, "Failed to delete account", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progress.dismiss();
                Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void tintDialogButtons(AlertDialog dialog) {
        if (dialog == null) return;
        int color = ContextCompat.getColor(this, R.color.dialog_button_text);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(color);
        if (negative != null) negative.setTextColor(color);
    }

    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadProfilePicture(data.getData());
        }
    }

    private void uploadProfilePicture(final Uri uri) {
        String username = Prefs.getUsername();
        if (username == null) return;

        String type = getContentResolver().getType(uri);
        final String contentType = (type != null) ? type : "image/jpeg";

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();
        String token = "Bearer " + Prefs.getToken();
        
        Map<String, String> body = new HashMap<>();
        body.put("content_type", contentType);

        RetrofitClient.getApiService().getUploadUrl(token, username, body).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    performActualUpload(response.body().get("upload_url"), uri, contentType);
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performActualUpload(final String uploadUrl, final Uri uri, final String contentType) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            byte[] bytes = getBytes(inputStream);
            RequestBody requestBody = RequestBody.create(MediaType.parse(contentType), bytes);

            RetrofitClient.getApiService().uploadImage(uploadUrl, requestBody).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        markComplete(bytes.length);
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "Error reading file", e);
        }
    }

    private void markComplete(int size) {
        final String username = Prefs.getUsername();
        String token = "Bearer " + Prefs.getToken();
        Map<String, Object> body = new HashMap<>();
        body.put("size", size);

        RetrofitClient.getApiService().markUploadComplete(token, username, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SettingsActivity.this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                    ProfileUtils.clearCache(username);
                    ProfileUtils.loadProfilePicture(SettingsActivity.this, username, imageViewAvatar);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    public byte[] getBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
