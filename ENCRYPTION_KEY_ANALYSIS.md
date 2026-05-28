# Encryption Key Handling Analysis

## Overview
This document details how private keys are stored, generated, and published to the backend in your Android application.

---

## 1. PRIVATE KEY STORAGE

### Current Implementation
**File:** `com/example/myapplication/util/Prefs.java`

**Storage Method:** EncryptedSharedPreferences with AES256_GCM

```
Storage Location: Device's private app storage
├── Encryption: AES256_GCM (MasterKey managed by Android Security library)
├── Key Name (Private)
│   ├── KEY_IDENTITY_PRIV ("identity_priv")
│   ├── KEY_SIGNED_PREKEY_PRIV ("signed_prekey_priv")
│   └── Stored Format: Base64-encoded PKCS8
└── Cached Shared Secrets: "shared_secret_" + userId
```

**Security Assessment:** ⚠️ **MODERATE RISK**

### Issues Identified

#### 1.1 **Not Using Android Keystore** (HIGH SEVERITY)
- Private keys are stored in EncryptedSharedPreferences instead of AndroidKeystore
- SharedPreferences is vulnerable to extraction if device is compromised
- Android Keystore provides hardware-backed protection when available

**Current (INSECURE):**
```java
// Prefs.java lines 68-72
public static void saveIdentityKeys(String pub, String priv) {
    sharedPreferences.edit()
            .putString(KEY_IDENTITY_PUB, pub)
            .putString(KEY_IDENTITY_PRIV, priv)  // ⚠️ Plain base64 storage
            .apply();
}
```

#### 1.2 **Fallback to Unencrypted SharedPreferences** (HIGH SEVERITY)
- Lines 37-40: If EncryptedSharedPreferences initialization fails, falls back to regular SharedPreferences
- This means encryption is NOT guaranteed - it's optional!

```java
// Prefs.java lines 37-40
catch (GeneralSecurityException | IOException e) {
    e.printStackTrace();
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);  // ⚠️ UNENCRYPTED!
}
```

#### 1.3 **No Key Erasure from Memory** (MEDIUM SEVERITY)
- Private keys remain as Base64 strings, never explicitly cleared
- No use of char[] arrays for sensitive data
- Vulnerable to memory dumps

---

## 2. KEY GENERATION FLOW

### Timeline

```
┌─────────────────────────────────────────────────────────┐
│ REGISTRATION FLOW (RegisterActivity.java)              │
└─────────────────────────────────────────────────────────┘
    ↓
[User clicks Register Button]
    ↓
[Lines 53-54] Generate Identity Keypair (X25519)
    ├─ Called: SignalManager.generateKeyPair()
    ├─ Generated Keys: (public_key, private_key)
    ↓
[Lines 54] Save to Device Storage (Prefs)
    ├─ saveIdentityKeys(pub, priv)
    ↓
[Line 60] Send Registration Request with PUBLIC key only
    ├─ Endpoint: register()
    ├─ Sends: username, password, public_key
    ├─ Backend: Stores user's identity public key
    ↓
[After successful registration → login]
    ↓
[Lines 147-148] Generate Signed Prekey
    ├─ Called: SignalManager.generateKeyPair()
    ├─ Generated: (signed_prekey_pub, signed_prekey_priv)
    ↓
[Lines 154-157] Generate 10 One-Time Prekeys
    ├─ Only public keys generated/stored (private keys derived as needed)
    ├─ Called: generateOneTimePrekeys(10)
    ↓
[Line 161] Upload Key Bundle to Backend
    ├─ Endpoint: uploadKeys()
    ├─ Sends: identity_key (pub), signed_prekey (pub), otps (list of pubs)
    ├─ Includes registration_id, device_name
        └─ Private keys NOT sent ✓

┌─────────────────────────────────────────────────────────┐
│ LOGIN FLOW (LoginActivity.java)                         │
└─────────────────────────────────────────────────────────┘
    ↓
[User clicks Login Button]
    ↓
[Lines 67-86] Authentication Request
    ├─ Sends: username, password
    ├─ Receives: access_token
    ↓
[Lines 88-112] Fetch User Profile
    ├─ Endpoint: getMe(token)
    ├─ Purpose: Get user_id
    ↓
[Lines 97-99] Check if Keys Exist on Device
    ├─ Calls: Prefs.getIdentityPubKey()
    ├─ If NULL → Generate keys now
    ├─ If EXISTS → Skip generation, use existing keys
    ↓
[Lines 115-127] Initialize Keys (if needed)
    ├─ Generate Identity Keys
    ├─ Generate Signed Prekey
    ├─ Generate 10 One-Time Prekeys
    ├─ Save all (including private keys) to device
    ↓
[Lines 140-150] Upload Key Bundle
    ├─ Same as registration flow
    ├─ Only PUBLIC keys uploaded
```

### Key Generation Process

**File:** `com/example/myapplication/util/SignalManager.java`

```java
// Lines 33-41: Key Generation
public static KeyPairStrings generateKeyPair() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);  // X25519
    KeyPair kp = kpg.generateKeyPair();
    
    KeyPairStrings kps = new KeyPairStrings();
    kps.publicKey = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
    kps.privateKey = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
    return kps;
}
```

---

## 3. KEY PUBLISHING TO BACKEND

### What Gets Published

#### 3.1 During Registration
| Data | Direction | Security |
|------|-----------|----------|
| username | → Backend | Plaintext over HTTPS |
| password | → Backend | Plaintext over HTTPS |
| **identity_public_key** | → Backend | ✓ Public (can be public) |
| private_key | ✗ NOT SENT | ✓ Correct |

**Endpoint:** `register()` - Sends `AuthRequest` with public_key

#### 3.2 After Registration/Login
| Data | Endpoint | Direction | Backend Action |
|------|----------|-----------|-----------------|
| identity_key (pub) | uploadKeys() | → Backend | Stores in key bundle |
| signed_prekey (pub) | uploadKeys() | → Backend | Stores in key bundle |
| one_time_prekeys (pubs) | uploadKeys() | → Backend | Stores in rotating pool |
| registration_id | uploadKeys() | → Backend | Stores |
| device_name | uploadKeys() | → Backend | Stores |

**File:** `LoginActivity.java` lines 132-138

```java
KeyBundleRequest bundleRequest = new KeyBundleRequest(
    identityKeys.publicKey,        // Only PUBLIC
    signedPrekey.publicKey,        // Only PUBLIC
    otps,                          // Only PUBLIC keys
    registrationId,
    "android-" + android.os.Build.MODEL
);

RetrofitClient.getApiService().uploadKeys("Bearer " + token, userId, bundleRequest)
```

#### 3.3 Private Keys - How They're Used Locally

**File:** `ChatActivity.java` lines 100-116

When establishing a secure connection with a peer:

```java
// Step 1: Fetch peer's public key
RetrofitClient.getApiService().getPublicKey(peerUserId).enqueue(...)

// Step 2: Compute shared secret using local private key
byte[] secret = SignalManager.computeSharedSecret(
    myIdentityPriv,      // ← Retrieved from local storage
    peerPublicKey        // ← Fetched from backend
);

// Step 3: Cache the derived shared secret
Prefs.saveSharedSecret(peerUserId, secretB64);
```

---

## 4. MESSAGE ENCRYPTION FLOW

```
┌──────────────────────────────────────────────────┐
│ SENDING MESSAGE                                  │
└──────────────────────────────────────────────────┘
    ↓
[User types message]
    ↓
[ChatActivity.prepareAndSendMessage()]
    ↓
[Retrieve shared secret for peer from cache]
    ├─ If cached: Use it ✓
    ├─ If not cached:
    │   ├─ Fetch peer's identity public key
    │   ├─ Compute shared secret: X25519(myPriv, peerPub)
    │   ├─ Derive AES key from shared secret via HKDF-SHA256
    │   ├─ Cache the shared secret
    │   └─ Continue...
    ↓
[SignalManager.encrypt(plaintext, sharedSecret)]
    ├─ Generate random 12-byte IV
    ├─ Derive 16-byte AES key from shared secret
    ├─ Encrypt with AES-GCM
    ├─ Return: Base64(IV || Ciphertext)
    ↓
[Send ciphertext to backend]
    └─ Endpoint: sendMessage(token, chatId, {ciphertext})
        └─ Backend: Stores encrypted message

┌──────────────────────────────────────────────────┐
│ RECEIVING MESSAGE                                │
└──────────────────────────────────────────────────┘
    ↓
[WebSocket receives new message event]
    ↓
[ChatActivity.handleNewMessage(messageResponse)]
    ↓
[Retrieve shared secret for sender from cache]
    ├─ If not cached: Fetch peer's keys & compute
    ↓
[decryptSafely(peerUserId, ciphertext)]
    ├─ Get cached shared secret
    ├─ SignalManager.decrypt(ciphertext, sharedSecret)
    │   ├─ Extract IV from ciphertext
    │   ├─ Derive AES key from shared secret
    │   ├─ Decrypt with AES-GCM
    │   └─ Return: plaintext
    ↓
[Display decrypted message in UI]
```

---

## 5. SECURITY ISSUES & RECOMMENDATIONS

### Critical Issues

#### ❌ Issue #1: No Android Keystore
**Severity:** `HIGH`
**Location:** `Prefs.java` lines 24-40

**Problem:** Private keys stored in EncryptedSharedPreferences which can be extracted
**Impact:** Attacker with device access can extract all private keys

**Recommendation:**
```java
// Use Android Keystore for private key storage
KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
keyStore.load(null);
PrivateKey privateKey = (PrivateKey) keyStore.getKey("key_alias", null);
```

#### ❌ Issue #2: Unencrypted Fallback
**Severity:** `CRITICAL`
**Location:** `Prefs.java` lines 37-40

**Problem:** If EncryptedSharedPreferences fails, falls back to PLAINTEXT storage
**Impact:** All encryption can be silently bypassed

**Recommendation:** 
```java
catch (GeneralSecurityException | IOException e) {
    throw new RuntimeException("Failed to initialize secure storage", e);
    // Don't silently fall back to unencrypted storage
}
```

#### ❌ Issue #3: Shared Secrets Cached in Device Storage
**Severity:** `MEDIUM`
**Location:** `Prefs.java` lines 96-102, `ChatActivity.java` lines 180-184

**Problem:** Computed shared secrets (result of ECDH) cached in SharedPreferences
**Impact:** If device storage is extracted, all chat keys are compromised

**Recommendation:**
```java
// Either:
// 1. Don't cache shared secrets (compute on-demand)
// 2. Or cache only in memory with strict lifecycle
// 3. Or use transient memory that's cleared after each use
```

#### ❌ Issue #4: No Key Rotation
**Severity:** `MEDIUM`
**Location:** Entire application

**Problem:** Keys are never rotated; generated once and used forever
**Impact:** Compromised key = compromised all past and future messages

**Recommendation:** Implement:
- Signed prekey rotation (every 7-30 days)
- One-time prekey replenishment
- Device key refresh mechanism

#### ❌ Issue #5: No Signature Verification
**Severity:** `MEDIUM`
**Location:** `ChatActivity.java` lines 125-146

**Problem:** Public keys fetched from backend are not signed/verified
**Impact:** Man-in-the-middle attack - attacker can substitute keys

**Recommendation:**
- Have backend sign all public keys
- Verify signature before using keys
- Implement certificate pinning

---

## 6. DATA FLOW DIAGRAMS

### Registration & Key Publishing

```
CLIENT (Device)                    BACKEND (Server)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Generate Keys
   ├─ Identity: (pub, priv) ← Stored locally
   ├─ Signed Prekey: (pub, priv) ← Stored locally
   └─ OTP: [pub1, pub2, ...] ← Only pubs generated

2. Register User
   POST /auth/register
   ├─ username
   ├─ password
   └─ public_key ────────────────→ ✓ Stored in DB

3. Login
   POST /auth/login
   ├─ username
   ├─ password ──────────────────→ ✓ Auth successful
                                   ← access_token

4. Upload Key Bundle
   POST /users/{userId}/keys
   ├─ Authorization: Bearer token
   ├─ identity_key (pub) ────────→ ✓ Stored
   ├─ signed_prekey (pub) ───────→ ✓ Stored
   ├─ one_time_prekeys ──────────→ ✓ Stored
   └─ registration_id ───────────→ ✓ Stored
```

### Message Encryption & Sending

```
SENDER (Device)                    BACKEND              RECEIVER (Device)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Fetch Recipient's Key Bundle
   GET /users/{recipientId}/keys ──→ Return: {
                                          identity_key,
                                          signed_prekey,
                                          one_time_prekey
                                        }

2. Compute Shared Secret (X25519)
   SharedSecret = ECDH(
       senderPriv.identity_key,
       recipientPub.identity_key
   )

3. Derive AES Key
   AESKey = HKDF-SHA256(SharedSecret, ...)

4. Encrypt Message
   IV = Random(12 bytes)
   Ciphertext = AES-GCM-Encrypt(plaintext, AESKey, IV)

5. Send Message
   POST /chats/{chatId}/messages
   {
       "ciphertext": Base64(IV || Ciphertext)
   } ───────────────→ ✓ Stored

                                    6. Receive Message
                                       (via WebSocket)
                                       ← {
                                           "ciphertext": "...",
                                           "senderId": "..."
                                         }

                                    7. Fetch Sender's Key
                                       GET /users/{senderId}/keys

                                    8. Compute Same Shared Secret
                                       SharedSecret = ECDH(
                                           receiverPriv.identity_key,
                                           senderPub.identity_key
                                       )

                                    9. Derive AES Key
                                       AESKey = HKDF-SHA256(...)

                                    10. Decrypt Message
                                        Plaintext = AES-GCM-Decrypt(...)
```

---

## 7. WHEN KEYS ARE USED

| Key | Generation | Storage | Usage | Lifetime |
|-----|-----------|---------|-------|----------|
| **Identity Private** | Registration/Login | Device (Encrypted SharedPrefs) | Sign messages, X25519 ECDH | Forever (no rotation) |
| **Identity Public** | Registration/Login | Device + Backend DB | Publishing to other users | Forever |
| **Signed Prekey Priv** | Login | Device (Encrypted SharedPrefs) | Backup key for X25519 | Until refresh |
| **Signed Prekey Pub** | Login | Device + Backend DB | Published for key exchange | Until refresh |
| **One-Time Prekey Pub** | Login | Backend DB | Single-use in key agreement | Consumed after use |
| **Shared Secret** | Per-peer | Device (Encrypted SharedPrefs) | Derive AES keys for messages | Per-conversation |

---

## 8. CURRENT SECURITY POSTURE

### Strengths ✓
- Uses X25519 (modern, elliptic curve)
- Uses AES-256-GCM (authenticated encryption)
- Uses HKDF-SHA256 (proper key derivation)
- Private keys NOT sent to backend
- SSL/TLS transport (HTTPS/WSS)
- Uses AndroidSecurity library for MasterKey

### Weaknesses ✗
- **No Android Keystore** - Private keys in SharedPreferences
- **Unencrypted fallback** - Silently fails to plaintext
- **Shared secrets in storage** - Unnecessary key material persistence
- **No key rotation** - Keys used forever
- **No signature verification** - Keys not authenticated
- **No key attestation** - No device integrity verification
- **Shared secret caching** - Increases attack surface

---

## 9. RECOMMENDED FIXES (Priority Order)

### Priority 1: CRITICAL (Do First)
1. **Remove unencrypted fallback** in Prefs.java
2. **Migrate to Android Keystore** for private key storage
3. **Stop caching shared secrets** - compute on-demand

### Priority 2: HIGH (Do Soon)
4. **Implement key rotation** for signed prekeys
5. **Add signature verification** for keys from backend
6. **Implement certificate pinning** for API calls

### Priority 3: MEDIUM (Enhance)
7. Implement X3DH key agreement (already have code, use it!)
8. Add key attestation for device verification
9. Implement forward secrecy (session keys)
10. Add audit logging for key operations

---

## 10. FILE LOCATIONS REFERENCE

```
Key Generation:      SignalManager.java:33-49
Key Storage:         Prefs.java:24-94
Registration:        RegisterActivity.java:42-94
Login:              LoginActivity.java:67-155
Messaging:          ChatActivity.java:100-379
Encryption:         SignalManager.java:86-151
Decryption:         SignalManager.java:107-138
Shared Secret:      SignalManager.java:140-150
```


