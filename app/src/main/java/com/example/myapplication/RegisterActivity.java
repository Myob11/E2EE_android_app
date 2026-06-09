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
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextPassword;
    private Button buttonRegister;
    private TextView textViewLogin;
    private SignalManager.KeyPairStrings pendingIdentityKeys;
    private int pendingRegistrationId = 0;
    private String pendingUsername;
    private String pendingPassword;

    // Sets up the registration form and starts the account creation flow.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewLogin = findViewById(R.id.textViewLogin);

        buttonRegister.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(RegisterActivity.this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Generate account-bound Identity Key
                pendingIdentityKeys = SignalManager.generateAccountBoundKeyPair(username, password);
                pendingUsername = username;
                pendingPassword = password;

                // Generate Registration ID
                pendingRegistrationId = new SecureRandom().nextInt(10000) + 1000;

                AuthRequest request = new AuthRequest(username, password, pendingIdentityKeys.publicKey);

                buttonRegister.setEnabled(false);
                RetrofitClient.getApiService().register(request).enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Registration Successful! Logging in...", Toast.LENGTH_SHORT).show();
                            loginAfterRegister(username, password);
                        } else {
                            buttonRegister.setEnabled(true);
                            String errorMsg = "Registration failed";
                            try {
                                if (response.errorBody() != null) {
                                    errorMsg += ": " + response.errorBody().string();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Log.e("RegisterActivity", errorMsg);
                            Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        buttonRegister.setEnabled(true);
                        Log.e("RegisterActivity", "Network error", t);
                        Toast.makeText(RegisterActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e("RegisterActivity", "Key generation failed: " + e.getMessage(), e);
                String errorMsg = "Failed to initialize secure keys: " + e.getClass().getSimpleName();
                if (e.getMessage() != null) {
                    errorMsg += " - " + e.getMessage();
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        textViewLogin.setOnClickListener(v -> finish());
    }

    // Logs in immediately after a successful registration.
    private void loginAfterRegister(String username, String password) {
        AuthRequest request = new AuthRequest(username, password);
        RetrofitClient.getApiService().login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Prefs.setCurrentUser(response.body().getId());
                    String token = response.body().getAccess_token();
                    Prefs.saveToken(token);
                    fetchUserProfile(token);
                } else {
                    Toast.makeText(RegisterActivity.this, "Login after registration failed. Please login manually.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Network error during login. Please login manually.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // Saves the new user profile and uploads the generated key bundle.
    private void fetchUserProfile(String token) {
        RetrofitClient.getApiService().getMe("Bearer " + token).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Prefs.setCurrentUser(response.body().getId());
                    Prefs.saveUserId(response.body().getId());
                    Prefs.saveUsername(response.body().getUsername());
                    if (pendingRegistrationId > 0) {
                        Prefs.saveRegistrationId(pendingRegistrationId);
                    }
                    if (pendingIdentityKeys != null) {
                        Prefs.saveIdentityKeys(pendingIdentityKeys.publicKey, pendingIdentityKeys.privateKey);
                    }
                    uploadKeyBundle(token, response.body().getId());
                } else {
                    finish();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                finish();
            }
        });
    }

    // Builds and uploads the account key bundle to the backend.
    private void uploadKeyBundle(String token, String userId) {
        try {
            // Generate Signed Prekey
            SignalManager.KeyPairStrings signedPrekey = SignalManager.generateKeyPair();
            Prefs.saveSignedPrekey(signedPrekey.publicKey, signedPrekey.privateKey);
            
            // Generate One-time Prekeys
            List<String> otps = SignalManager.generateOneTimePrekeys(10);
            
            // Encrypt identity private key for secure backup/restore across devices
            String encryptedPrivateKey = null;
            if (pendingIdentityKeys != null && pendingUsername != null && pendingPassword != null) {
                try {
                    encryptedPrivateKey = SignalManager.encryptIdentityPrivateKey(
                            pendingIdentityKeys.privateKey,
                            pendingUsername,
                            pendingPassword
                    );
                    Log.d("RegisterActivity", "Successfully encrypted identity private key for backup");
                } catch (Exception e) {
                    Log.w("RegisterActivity", "Failed to encrypt identity private key for backup: " + e.getMessage());
                    // Continue anyway - encrypted backup is optional
                }
            }

            KeyBundleRequest bundleRequest = new KeyBundleRequest(
                    Prefs.getIdentityPubKey(),
                    signedPrekey.publicKey,
                    otps,
                    Prefs.getRegistrationId(),
                    "android-" + android.os.Build.MODEL,
                    encryptedPrivateKey
            );

            RetrofitClient.getApiService().uploadKeys("Bearer " + token, userId, bundleRequest).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // Even if key upload fails, we proceed but encryption might fail later
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        } catch (Exception e) {
            Log.e("RegisterActivity", "Key bundle upload failed", e);
            finish();
        }
    }
}