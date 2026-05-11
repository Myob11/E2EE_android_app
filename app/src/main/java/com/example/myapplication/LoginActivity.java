package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.api.AuthRequest;
import com.example.myapplication.api.AuthResponse;
import com.example.myapplication.api.KeyBundleRequest;
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.api.User;
import com.example.myapplication.util.Prefs;
import com.example.myapplication.util.SignalManager;
import com.google.android.material.textfield.TextInputEditText;
import java.security.SecureRandom;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextPassword;
    private Button buttonLogin;
    private TextView textViewRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Prefs.getToken() != null) {
            if (Prefs.getUserId() != null && Prefs.getUsername() != null) {
                goToMain();
                return;
            } else {
                fetchUserProfile(Prefs.getToken());
            }
        }

        setContentView(R.layout.activity_login);

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        textViewRegister = findViewById(R.id.textViewRegister);

        buttonLogin.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            login(username, password);
        });

        textViewRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void login(String username, String password) {
        AuthRequest request = new AuthRequest(username, password);
        RetrofitClient.getApiService().login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String token = response.body().getAccess_token();
                    Prefs.saveToken(token);
                    fetchUserProfile(token);
                } else {
                    Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchUserProfile(String token) {
        RetrofitClient.getApiService().getMe("Bearer " + token).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Prefs.saveUserId(response.body().getId());
                    Prefs.saveUsername(response.body().getUsername());
                    
                    // Check if keys exist on this device, if not generate and upload
                    if (Prefs.getIdentityPubKey() == null) {
                        initializeKeysAndGoToMain(token, response.body().getId());
                    } else {
                        goToMain();
                    }
                } else {
                    Prefs.clear();
                    setContentView(R.layout.activity_login); 
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeKeysAndGoToMain(String token, String userId) {
        try {
            // Generate Identity Keys
            SignalManager.KeyPairStrings identityKeys = SignalManager.generateKeyPair();
            Prefs.saveIdentityKeys(identityKeys.publicKey, identityKeys.privateKey);
            
            // Generate Registration ID
            int registrationId = new SecureRandom().nextInt(10000) + 1000;
            Prefs.saveRegistrationId(registrationId);

            // Generate Signed Prekey
            SignalManager.KeyPairStrings signedPrekey = SignalManager.generateKeyPair();
            Prefs.saveSignedPrekey(signedPrekey.publicKey, signedPrekey.privateKey);
            
            // Generate One-time Prekeys
            List<String> otps = SignalManager.generateOneTimePrekeys(10);
            
            KeyBundleRequest bundleRequest = new KeyBundleRequest(
                    identityKeys.publicKey,
                    signedPrekey.publicKey,
                    otps,
                    registrationId,
                    "android-" + android.os.Build.MODEL
            );

            RetrofitClient.getApiService().uploadKeys("Bearer " + token, userId, bundleRequest).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    goToMain();
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    goToMain(); // Proceed anyway
                }
            });
        } catch (Exception e) {
            Log.e("LoginActivity", "Key generation failed", e);
            goToMain();
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}