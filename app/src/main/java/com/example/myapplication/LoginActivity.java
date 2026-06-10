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
import com.example.myapplication.api.KeyBundleResponse;
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

    // Handles auto-login, form setup, and navigation into registration.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Prefs.getToken() != null) {
            if (Prefs.getUserId() != null && Prefs.getUsername() != null) {
                goToMain();
                return;
            } else {
                fetchUserProfile(Prefs.getToken(), null, null);
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

    // Sends the login request and stores the access token on success.
    private void login(String username, String password) {
        AuthRequest request = new AuthRequest(username, password);
        RetrofitClient.getApiService().login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String token = response.body().getAccess_token();
                    Prefs.saveToken(token);
                    fetchUserProfile(token, username, password);
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
                    Prefs.clearSessionOnly();
                    setContentView(R.layout.activity_login);
                }
            }

                     // Check if keys exist on this device, if not try to restore from backup
                     if (Prefs.getIdentityPubKey() == null) {
                         if (username == null || password == null) {
                             Prefs.clearSessionOnly();
                             setContentView(R.layout.activity_login);
                             Toast.makeText(LoginActivity.this, "Please log in again to restore account-bound keys.", Toast.LENGTH_LONG).show();
                             return;
                         }
                         // Try to fetch and decrypt encrypted keys from backup
                         fetchEncryptedKeysAndInitialize(token, response.body().getId(), username, password);
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

      // Restores the identity private key from backup and refreshes device keys.
      private void fetchEncryptedKeysAndInitialize(String token, String userId, String username, String password) {
         RetrofitClient.getApiService().getKeyBundle("Bearer " + token, userId).enqueue(new Callback<KeyBundleResponse>() {
             @Override
             public void onResponse(Call<KeyBundleResponse> call, Response<KeyBundleResponse> response) {
                 if (response.isSuccessful() && response.body() != null && response.body().getEncryptedIdentityPrivateKey() != null) {
                     // Try to decrypt the encrypted private key from backup
                     try {
                         String decryptedPrivateKey = SignalManager.decryptIdentityPrivateKey(
                                 response.body().getEncryptedIdentityPrivateKey(),
                                 username,
                                 password
                         );
                         Log.d("LoginActivity", "Successfully decrypted identity private key from backup");

                         // Restore the account keys using decrypted private key and public key from bundle
                         Prefs.saveIdentityKeys(response.body().getIdentityKey(), decryptedPrivateKey);

                         // Generate new signed prekey and OTPs for this device
                         initializeNewDeviceKeysAndUpload(token, userId);
                     } catch (Exception e) {
                         Log.e("LoginActivity", "Failed to decrypt identity private key: " + e.getMessage(), e);
                         // Decryption failed - generate new keys
                         initializeKeysAndGoToMain(token, userId, username, password);
                     }
                 } else {
                     Log.w("LoginActivity", "No encrypted key backup found, generating new keys");
                     // No encrypted backup available - generate new keys
                     initializeKeysAndGoToMain(token, userId, username, password);
                 }
             }

             @Override
             public void onFailure(Call<KeyBundleResponse> call, Throwable t) {
                 Log.e("LoginActivity", "Failed to fetch key bundle: " + t.getMessage());
                 // If fetch fails, try to generate new keys
                 initializeKeysAndGoToMain(token, userId, username, password);
             }
          });
      }

     // Creates fresh device-specific keys after a successful restore.
     private void initializeNewDeviceKeysAndUpload(String token, String userId) {
         try {
             // Generate new device-specific keys (signed prekey and OTPs)
             // The identity key is already restored from backup
             int registrationId = new SecureRandom().nextInt(10000) + 1000;
             Prefs.saveRegistrationId(registrationId);

             SignalManager.KeyPairStrings signedPrekey = SignalManager.generateKeyPair();
             Prefs.saveSignedPrekey(signedPrekey.publicKey, signedPrekey.privateKey);
             
             List<String> otps = SignalManager.generateOneTimePrekeys(10);
             
             // Upload new device-specific keys while keeping the account identity key
             KeyBundleRequest bundleRequest = new KeyBundleRequest(
                     Prefs.getIdentityPubKey(),
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
                     Log.w("LoginActivity", "Failed to upload new device keys, but proceeding anyway");
                     goToMain(); // Proceed even if upload fails
                 }
             });
         } catch (Exception e) {
             Log.e("LoginActivity", "Failed to initialize new device keys", e);
             goToMain(); // Proceed anyway
         }
     }

      // Generates a new account-bound identity key pair and uploads the bundle.
      private void initializeKeysAndGoToMain(String token, String userId, String username, String password) {
         try {
             // Generate account-bound Identity Keys
             SignalManager.KeyPairStrings identityKeys = SignalManager.generateAccountBoundKeyPair(
                     username,
                     password
             );
             Prefs.saveIdentityKeys(identityKeys.publicKey, identityKeys.privateKey);

             // Generate Registration ID
             int registrationId = new SecureRandom().nextInt(10000) + 1000;
             Prefs.saveRegistrationId(registrationId);

             // Generate Signed Prekey
             SignalManager.KeyPairStrings signedPrekey = SignalManager.generateKeyPair();
             Prefs.saveSignedPrekey(signedPrekey.publicKey, signedPrekey.privateKey);

             // Generate One-time Prekeys
             List<String> otps = SignalManager.generateOneTimePrekeys(10);

             // Encrypt identity private key for secure backup/restore across devices
             String encryptedPrivateKey = null;
             try {
                 encryptedPrivateKey = SignalManager.encryptIdentityPrivateKey(
                         identityKeys.privateKey,
                         username,
                         password
                 );
                 Log.d("LoginActivity", "Successfully encrypted identity private key for backup");
             } catch (Exception e) {
                 Log.w("LoginActivity", "Failed to encrypt identity private key for backup: " + e.getMessage());
                 // Continue anyway - encrypted backup is optional
             }

             KeyBundleRequest bundleRequest = new KeyBundleRequest(
                     identityKeys.publicKey,
                     signedPrekey.publicKey,
                     otps,
                     registrationId,
                     "android-" + android.os.Build.MODEL,
                     encryptedPrivateKey
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

    // Opens the main screen after login/key setup finishes.
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}