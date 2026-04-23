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
import com.example.myapplication.api.RetrofitClient;
import com.example.myapplication.api.User;
import com.example.myapplication.util.Prefs;
import com.google.android.material.textfield.TextInputEditText;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextPassword;
    private Button buttonRegister;
    private TextView textViewLogin;

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

            // For now, public_key is optional or mock. 
            AuthRequest request = new AuthRequest(username, password, "mock_public_key");
            
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
        });

        textViewLogin.setOnClickListener(v -> finish());
    }

    private void loginAfterRegister(String username, String password) {
        AuthRequest request = new AuthRequest(username, password);
        RetrofitClient.getApiService().login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
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

    private void fetchUserProfile(String token) {
        RetrofitClient.getApiService().getMe("Bearer " + token).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Prefs.saveUserId(response.body().getId());
                    Prefs.saveUsername(response.body().getUsername());
                    
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
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
}