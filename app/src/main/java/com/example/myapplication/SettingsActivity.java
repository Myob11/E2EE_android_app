package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
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
<<<<<<< Updated upstream
<<<<<<< Updated upstream
        Button buttonUploadAvatar = findViewById(R.id.buttonUploadAvatar);
        radioGroupTheme = findViewById(R.id.radioGroupTheme);
        switchOnlineIndicator = findViewById(R.id.switchOnlineIndicator);
        switchAutoDownload = findViewById(R.id.switchAutoDownload);
=======
=======
>>>>>>> Stashed changes
        SwitchCompat switchTheme = findViewById(R.id.switchTheme);

>>>>>>> Stashed changes
        textViewUsernameDisplay = findViewById(R.id.textViewUsernameDisplay);
        buttonSwitchAccount = findViewById(R.id.buttonSwitchAccount);

        String username = Prefs.getUsername();
        if (username != null) {
            textViewUsernameDisplay.setText(username);
        }

        ProfileUtils.loadProfilePicture(this, username, imageViewAvatar);

        // Long press on avatar to show upload prompt
        imageViewAvatar.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Profile Picture")
                    .setMessage("Do you want to upload a new profile picture?")
                    .setPositiveButton("Yes", (dialog, which) -> openGallery())
                    .setNegativeButton("No", null)
                    .show();
            return true;
        });

        buttonUploadAvatar.setOnClickListener(v -> openGallery());

// Set initial state based on current theme
        boolean isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        switchTheme.setChecked(isDarkMode);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        buttonSwitchAccount.setOnClickListener(v -> {
            Prefs.clear();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
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
            Uri imageUri = data.getData();
            Log.d(TAG, "onActivityResult: Image selected. Uri=" + imageUri);
            uploadProfilePicture(imageUri);
        }
    }

    private void uploadProfilePicture(final Uri uri) {
        String username = Prefs.getUsername();
        if (username == null) {
            Log.e(TAG, "uploadProfilePicture: Username is null, aborting");
            return;
        }

        String type = getContentResolver().getType(uri);
        final String contentType = (type != null) ? type : "image/jpeg";
        Log.d(TAG, "uploadProfilePicture: Detected content type=" + contentType);

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();
        String token = "Bearer " + Prefs.getToken();
        
        Map<String, String> body = new HashMap<>();
        body.put("content_type", contentType);

        Log.d(TAG, "uploadProfilePicture: Requesting upload URL for " + username + " with body=" + body);
        RetrofitClient.getApiService().getUploadUrl(token, username, body).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String uploadUrl = response.body().get("upload_url");
                    Log.d(TAG, "uploadProfilePicture: SUCCESS. Got upload URL=" + uploadUrl);
                    performActualUpload(uploadUrl, uri, contentType);
                } else {
                    Log.e(TAG, "uploadProfilePicture: FAILED. Code=" + response.code() + ", Body=" + response.body());
                    Toast.makeText(SettingsActivity.this, "Upload failed (1)", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Log.e(TAG, "uploadProfilePicture: NETWORK ERROR getting upload URL", t);
                Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performActualUpload(final String uploadUrl, final Uri uri, final String contentType) {
        Log.d(TAG, "performActualUpload: Starting PUT request to MinIO. URL=" + uploadUrl);
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            final byte[] bytes = getBytes(inputStream);
            Log.d(TAG, "performActualUpload: File read success. Size=" + bytes.length + " bytes");
            
            RequestBody requestBody = RequestBody.create(MediaType.parse(contentType), bytes);

            RetrofitClient.getApiService().uploadImage(uploadUrl, requestBody).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "performActualUpload: SUCCESS. MinIO upload complete.");
                        markComplete(bytes.length);
                    } else {
                        Log.e(TAG, "performActualUpload: FAILED. Code=" + response.code());
                        Toast.makeText(SettingsActivity.this, "Upload failed (2)", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e(TAG, "performActualUpload: NETWORK ERROR during MinIO upload", t);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "performActualUpload: ERROR reading file from Uri", e);
        }
    }

    private void markComplete(int size) {
        final String username = Prefs.getUsername();
        String token = "Bearer " + Prefs.getToken();
        Map<String, Object> body = new HashMap<>();
        body.put("size", size);

        Log.d(TAG, "markComplete: Informing backend upload is finished. Username=" + username + ", size=" + size);
        RetrofitClient.getApiService().markUploadComplete(token, username, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "markComplete: SUCCESS. Backend acknowledged upload. Response=" + response.body());
                    Toast.makeText(SettingsActivity.this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                    ProfileUtils.clearCache(username);
                    ProfileUtils.loadProfilePicture(SettingsActivity.this, username, imageViewAvatar);
                } else {
                    Log.e(TAG, "markComplete: FAILED. Code=" + response.code() + ", Body=" + response.body());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "markComplete: NETWORK ERROR", t);
            }
        });
    }

    public byte[] getBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
