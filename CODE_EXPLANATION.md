# Project Code Explanation

Checklist
- [x] Collected all Java source files in the project
- [x] Created a single Markdown file that explains every Java file, each class, and each function/method
- [x] Added clear, line-aware explanations suitable for presenting to a professor

Notes about this document
- This file summarizes every Java source file under `app/src/main/java` in this project.
- For each class I provide: a short overview, then method-by-method explanations. For larger methods I explain the purpose of important code blocks line-by-line.
- I focused on explaining behavior, data flow (network, storage, crypto), and any non-obvious decisions (fallbacks, error handling, caching).
- The crypto notes below reflect the current code base, including account-bound identity keys, encrypted private-key backup, shared-secret caching, and the AES-GCM message layer.

Encryption and key management overview
- Identity keys: the app uses X25519 identity key pairs. In the current code, the identity pair is generated with `SignalManager.generateAccountBoundKeyPair(username, password)`, which makes the identity key deterministic for the same account credentials.
- How the deterministic seed is made: `SignalManager.deriveAccountSeed()` normalizes the username, uses the password as input, and runs PBKDF2WithHmacSHA256 with a username-based salt (`"com.example.myapplication.identity:" + username`) and 120,000 iterations.
- What gets uploaded to the server: registration and key upload send the identity public key, signed prekey, one-time prekeys, registration id, device id, and optionally an encrypted copy of the identity private key.
- Private-key backup: the identity private key can be encrypted with a password-derived AES key using AES/GCM and stored in the `encrypted_identity_private_key` field. This lets a user restore the same account identity on another device.
- Shared secret for chats: for each peer, the app derives a shared secret with X25519 KeyAgreement between the local identity private key and the peer’s public key. That secret is saved in `Prefs` as Base64 under a peer-specific key.
- Message encryption: actual chat messages are encrypted with AES/GCM. The AES key is derived from the shared secret using an HKDF-like HMAC-SHA256 step with a fixed info string. The random IV is prepended to the ciphertext and the result is Base64-encoded.
- Which keys are used where:
  - `identity private key` + `peer public key` -> shared secret
  - `shared secret` -> message AES key
  - `signed prekey` and `one-time prekeys` -> server-side key bundle / secure setup material, not the per-message encryption key
  - `registration id` and `device id` -> metadata for backend/device tracking
- Compatibility note: decryption first tries the current HKDF-derived AES key and then falls back to a legacy key-derivation path so older stored messages can still be read.

-----------------------------

## com.example.myapplication.MainActivity.java

Overview
- Main UI that lists conversations (chats). Loads friends and chats from the backend, maintains local mappings, derives missing shared secrets when possible, and supports searching, auto-refresh, deleting chats, and navigation into `ChatActivity`.

Fields (short):
- `recyclerView`, `adapter` : UI list of conversations.
- `chatConversations`, `friendsList` : in-memory lists for conversations and friends.
- `friendNames`, `friendToChatId` : maps to resolve friend usernames and mapping from friend ID to chat ID.
- `isSearching` : whether the user is actively searching.
- `refreshHandler`, `refreshRunnable`, `REFRESH_INTERVAL` : auto-refresh mechanism.

Key methods and explanations

- onCreate(Bundle savedInstanceState)
  - Sets layout with `setContentView` and initializes toolbar title to "Chats".
  - Finds `recyclerView` and sets a `LinearLayoutManager` so items are vertical-scrolling.
  - Creates `ConversationsAdapter` and attaches it to the `RecyclerView`.
  - Configures `SearchView` listeners:
    - `setOnQueryTextFocusChangeListener`: when search field gains focus, sets `isSearching = true` and shows empty result (calls `performSearch("")`).
    - `setOnQueryTextListener`: `onQueryTextSubmit` triggers `performSearch(query)`; `onQueryTextChange` performs incremental search only when `isSearching` is true.
  - Finds internal close button of the `SearchView` (platform id `android:id/search_close_btn`) and sets its click handler to clear the query, reset focus, set `isSearching=false`, and restore full conversations list.
  - Calls `setupAutoRefresh()` to prepare periodic data refreshing.

- setupAutoRefresh()
  - Creates a `Runnable` that runs every `REFRESH_INTERVAL` ms.
  - On each run, if not searching, calls `loadData()` to refresh friends/chats, then posts itself again.

- performSearch(String query)
  - Sorts the current `friendsList` alphabetically (case-insensitive) by username.
  - Creates `Conversation` placeholder entries for matching users. If a friend has a `chatId` in `friendToChatId`, it attaches it.
  - Updates adapter with the search results.

- onResume()/onPause()/onDestroy()
  - `onResume` triggers `loadData()` and starts auto-refresh if not searching.
  - `onPause` and `onDestroy` stop the auto-refresh runnable.

- loadData()
  - Reads stored token and user id from `Prefs`. If not present, returns early.
  - Calls API `getFriends(token, userId)` asynchronously with Retrofit.
    - On success: stores `friendsList`, updates `friendNames` map, and—if a friend's public key exists and no shared secret is stored—computes a shared secret using the local identity private key plus the friend's public key, then saves it in `Prefs` (Base64 encoded). This is done inside a try/catch to avoid crashing on crypto errors.
    - On failure: continues to `fetchChats()` to attempt loading chats anyway.

- fetchChats()
  - Calls backend `getChats` with token and user id.
  - On successful response, clears `chatConversations` and `friendToChatId`. For each `Chat`:
    - Determines display `name`: for 1:1 chats, it resolves the other member id (not the current user) as `targetId` and replaces opaque names with a friend's username (if known) for readability.
    - Normalizes fallback name to `Chat` when name is empty or contains underscores.
    - Stores mapping `friendToChatId[targetId] = chat.getId()` when `targetId` is known.
    - Creates a `Conversation` placeholder (no messages yet) and calls `fetchLastMessage(conv)` to asynchronously load the most recent message for that chat.
  - After iterating chats, calls `sortAndDisplayChats()` to order and show them.

- fetchLastMessage(Conversation conv)
  - Requests the most recent message (limit=1) for the chat via `getMessages` API.
  - If a message is returned, sets `conv.lastMessage` to the ciphertext and `lastMessageTime` to the created_at timestamp and calls `sortAndDisplayChats()` so UI updates when last-message data arrives.
  - Important: the list stores ciphertext as received from the backend; decryption happens later in `ConversationsAdapter` if a shared secret is available for that peer.

- sortAndDisplayChats()
  - Sorts `chatConversations` by `lastMessageTime` descending (newest first). Handles null timestamps safely by treating null as older.
  - If not searching, updates adapter data to display sorted list.

- onConversationClick / onConversationLongClick
  - `onConversationClick`: starts `ChatActivity` with extras: `chatId`, `targetUserId`, and `contactName`.
  - `onConversationLongClick`: prompts the user to confirm deletion via `AlertDialog` and calls `deleteChat(conversation)` when confirmed.

- deleteChat(conversation)
  - Calls API `deleteChat` with token and chat id.
  - On HTTP 401/405/404 or other codes, tries fallback endpoints `deleteChatPostAction` and then `deleteChatPostRemove` to support servers that expose delete as POST routes.
  - On success, calls `handleChatDeleted` to remove the chat from local lists, remove the mapping, and update the UI (search-aware).

Notes and caveats
- Crypto: `loadData()` attempts to derive and cache shared secrets when the friends' public_key is available. The app stores shared secrets via `Prefs`.
- UI updates are mostly asynchronous: data is loaded in background Retrofit callbacks and adapter is updated accordingly.
- Search mode is intentionally kept separate from the auto-refresh cycle so a live refresh does not overwrite a filtered result list while the user is typing.

-----------------------------

## com.example.myapplication.ConversationsAdapter.java

Overview
- RecyclerView adapter presenting `Conversation` items in the conversations list. Responsible for decrypting and displaying the last message preview when possible and loading profile pictures.

Key behavior
- Constructor: accepts list of conversations and listeners for click and long-click.
- updateData(newList): replaces internal lists and notifies RecyclerView to refresh.
- filter(query): simple in-memory filter to keep `filteredList` containing items whose contactName contains the query.
- onCreateViewHolder/onBindViewHolder/getItemCount: standard RecyclerView binding.

onBindViewHolder(holder, position)
- Reads the conversation for the current position from `filteredList`.
- Sets name text view to `conversation.getContactName()`.
- Decryption logic (important):
  - Gets `lastMsg` ciphertext and `targetUserId`.
  - Obtains the Base64 shared secret string from `Prefs` for that `targetUserId`.
  - If the secret is present and the last message is a non-empty ciphertext (and not the placeholders "No messages yet" / "Tap to chat") it decodes the shared secret and calls `SignalManager.decrypt(lastMsg, secret)` inside try/catch. If decryption fails, shows "[Encrypted Message]" as a fallback.
- Sets time and style on the views and uses `ProfileUtils.loadProfilePicture` to asynchronously set the avatar.
- Registers click and long-click handlers to call the provided listener interfaces.

Notes
- The adapter performs runtime decryption in the UI thread; decryption is usually quick but if profiling shows slowness it could be offloaded to a background thread.

-----------------------------

## com.example.myapplication.LoginActivity.java

Overview
- Handles user login flow. If token and user info already exist, it navigates to the main screen. Otherwise it authenticates, fetches the user profile, and then either restores the account-bound identity key from an encrypted backup or generates a fresh account-bound key pair.

Key methods

- onCreate(Bundle savedInstanceState)
  - If `Prefs.getToken()` exists and `Prefs.getUserId()` and `Prefs.getUsername()` exist, immediately navigates to `MainActivity`.
  - If token exists but profile info is missing, calls `fetchUserProfile(token)` to populate stored details.
  - Wiring UI fields: username, password inputs and buttons. Login button calls `login(username, password)`. Register text takes you to `RegisterActivity`.

- login(username, password)
  - Builds `AuthRequest` and calls `RetrofitClient.getApiService().login(request)`.
  - On success: saves token in `Prefs` and calls `fetchUserProfile(token)`.
  - On failure: shows a toast "Invalid credentials" or "Network error".

- fetchUserProfile(token)
  - Calls `getMe` endpoint with bearer token to retrieve user record.
  - On success: saves `userId` and `username` in Prefs.
    - If identity keys are absent on device (`Prefs.getIdentityPubKey()` == null), it tries to restore the identity private key from the server-side encrypted backup by calling `fetchEncryptedKeysAndInitialize(token, userId, username, password)`.
    - If a backup cannot be restored, it generates a fresh account-bound identity key pair with `initializeKeysAndGoToMain(token, userId, username, password)`.
    - If identity keys already exist, it simply navigates to Main.
  - On failure: clears Prefs and re-shows login layout (defensive reset).

- fetchEncryptedKeysAndInitialize(token, userId, username, password)
  - Fetches the account's key bundle from the backend.
  - If the bundle contains `encrypted_identity_private_key`, it decrypts that blob using `SignalManager.decryptIdentityPrivateKey(...)` with the username and password.
  - If decryption succeeds, it restores the identity key pair in `Prefs` and then calls `initializeNewDeviceKeysAndUpload(token, userId)` to create a fresh signed prekey and one-time prekeys for this device.
  - If decryption fails or there is no backup blob, it falls back to generating a new identity key pair.

- initializeNewDeviceKeysAndUpload(token, userId)
  - Used after a successful restore from backup. Keeps the account identity key but generates new device-specific keys.
  - Creates a new registration id, a new signed prekey pair, and 10 one-time prekeys.
  - Uploads the new bundle to the backend and then proceeds to the main screen regardless of upload success.

- initializeKeysAndGoToMain(token, userId, username, password)
  - Generates an account-bound identity key pair using `SignalManager.generateAccountBoundKeyPair(username, password)`.
  - Saves the public/private identity key pair in `Prefs`.
  - Generates a registration id, signed prekey, and one-time prekeys.
  - Encrypts the identity private key for backup with `SignalManager.encryptIdentityPrivateKey(...)` using the same username/password-derived key material.
  - Builds `KeyBundleRequest` with the optional backup blob and uploads the full bundle to the backend. On response (success or failure) it proceeds to `goToMain()`; upload failures do not block navigation.

- goToMain()
  - Starts `MainActivity` and finishes `LoginActivity`.

Notes
- Key generation is wrapped in try/catch and any exception leads to logging and continuing to the main screen.
- This activity is the place where the app decides whether the device should create a fresh identity or reuse the same account-bound identity from encrypted backup.

-----------------------------

## com.example.myapplication.FriendsAdapter.java

Overview
- RecyclerView adapter used by `FriendsActivity` to show friend list entries. It uses the same layout as conversation items and shows "Tap to start chatting" as last message.

Behavior
- onBindViewHolder: sets friend username, placeholder text, hides time view, loads profile picture with `ProfileUtils`, and registers click listener to call the `OnFriendClickListener`.

-----------------------------

## com.example.myapplication.FriendsActivity.java

Overview
- Activity listing friends. Fetches friends from backend and shows them with `FriendsAdapter`.

Key methods
- onCreate: sets toolbar, recycler view with adapter, and calls `fetchFriends()`.
- fetchFriends: calls `getFriends` from API. On success updates `friendsList` and notifies adapter. On failure, shows a Toast and logs.
- onFriendClick: opens `ChatActivity` with `targetUserId` and `contactName`.

-----------------------------

## com.example.myapplication.MessagesAdapter.java

Overview
- RecyclerView adapter for chat message list. Renders messages differently depending on whether they are sent or received.

Key behaviors
- Two view types: sent vs received; choice determined by `Message.isSentByMe()`.
- Maintains `lastSentMessagePosition` to show read/sent status only for the latest sent message.
- onBindViewHolder:
  - For sent messages: sets content and time; if this is the last sent message it shows a status text "Read" or "Sent" depending on `message.isRead()`.
  - For received messages: sets content, time, and loads avatar with `ProfileUtils`.

-----------------------------

## com.example.myapplication.Message.java

Overview
- Simple data model for a message in local UI memory with fields: id, content, isSentByMe, timestamp, isRead. Includes constructors and getters/setters.

-----------------------------

## com.example.myapplication.MyApplication.java

Overview
- Custom `Application` subclass. Initializes `Prefs` and forces the light theme for the whole app.

Key lines
- `Prefs.init(this)` sets up encrypted shared preferences.
- `AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)` forces light mode.

-----------------------------

## com.example.myapplication.ChatActivity.java

Overview
- Full chat screen. Loads chat messages, decrypts them, sends encrypted messages, handles WebSocket realtime updates with OkHttp WebSocket, falls back to polling when WebSocket is not connected, and manages shared secret establishment with peers.

Important fields
- `messageList`, `loadedMessageIds` : store messages and track which message ids are already loaded to avoid duplicates.
- `chatId`, `targetUserId`, `contactName` : context for the chat.
- `PAGE_SIZE` : messages per page (20).
- Polling and websocket variables: `pollHandler`, `pollRunnable`, `POLL_INTERVAL`, `webSocket`, `okHttpClient`, `isWebSocketConnected`, `isWebSocketConnecting`.
- `Gson` for parsing websocket JSON.

Key helper functions

- resolvePeerUserId(senderId)
  - Given a senderId from a message, returns the peer user's id (the other participant) — this helps pick the correct shared secret for decryption. If senderId equals the current user id, it returns `targetUserId` (since the peer is the other person).

- truncateKey(value)
  - Utility used for logging truncated key fingerprints.

- deriveAndCacheSharedSecret(peerUserId, peerPublicKey, onReady, onFailure)
  - Uses `SignalManager.computeSharedSecret` with the local identity private key and the peer's public key, then saves the Base64 secret in `Prefs` for later use.

- fetchPeerIdentityAndCacheSecret(peerUserId, onReady, onFailure)
  - Tries to fetch the peer's public key from endpoint `/api/users/{user_id}/public-key`; if successful, derives the shared secret immediately. On failure it falls back to `getKeyBundle`.

- fetchPeerBundleAndCacheSecret(peerUserId, onReady, onFailure)
  - Calls `/api/users/{user_id}/bundle` to fetch the key bundle that may contain the identity public key and attempts to derive the secret.

- ensureSharedSecretForPeer(peerUserId, onReady, onFailure)
  - Checks `Prefs` for existing shared secret; if present immediately calls `onReady`, otherwise triggers the fetch/derive flow to obtain it.

- onCreate(Bundle savedInstanceState)
  - Reads intent extras: `chatId`, `targetUserId`, `contactName`.
  - Initializes toolbar, recycler view, adapter, and scroll listener that loads older messages when the user scrolls up.
  - If `chatId` != null and there is no cached shared secret for `targetUserId`, it shows a status message, attempts to ensure a shared secret, then loads messages and starts WebSocket when ready. If secret exists it directly loads messages and starts WebSocket.
  - Configures `buttonSend` to call `prepareAndSendMessage` which orchestrates checking secrets and encrypting.
  - Sets `pollRunnable` used when WebSocket is unavailable (poll every `POLL_INTERVAL` ms) to fetch messages.

- prepareAndSendMessage(text)
  - Ensures `targetUserId` exists and that shared secret is ready using `ensureSharedSecretForPeer`. On success decodes the secret and calls `encryptAndSendMessage`.

- encryptAndSendMessage(plaintext, secret)
  - Uses `SignalManager.encrypt` to produce ciphertext. If `chatId` is null (no existing chat), calls `createChatAndSendCiphertext` to create a chat then send. Otherwise calls `sendCiphertext`.

- decryptSafely(peerUserId, ciphertext)
  - Fetches the peer secret from `Prefs`, logs helpful debug entries, and attempts `SignalManager.decrypt`. On failure it returns token strings such as "[Encrypted Message]" or "[Decryption Error]".

- handleNewMessage(MessageResponse res)
  - If message id has not been loaded, determines whether message is from me, resolves peer id, marks as read for inbound messages, ensures shared secret then decrypts or shows fallback encrypted text. Adds to `messageList`, updates adapter and scrolls to bottom.
  - If message is already loaded, updates read status if changed.

- fetchMessages(before, isInitialLoad)
  - Calls `getMessages` endpoint with limit and optional `before` timestamp to page earlier messages.
  - Processes responses in reverse chronological order to maintain UI order. Decrypts each message using `decryptSafely`. Adds to `messageList` either appended (initial load) or prepended (when loading older messages).

- sendCiphertext(ciphertext, originalPlaintext)
  - Sends encrypted message body to server via `sendMessage`. On success adds a local `Message` object with original plaintext (so the user sees what they sent in plain text) and updates UI.

- createChatAndSendCiphertext(ciphertext, originalPlaintext)
  - Creates a new chat by sorting member ids into a deterministic name (e.g. id1_id2) and posting to `/api/chats`. On success sets `chatId`, starts websocket, and calls `sendCiphertext`.

- markAsRead(messageId)
  - Posts to `/api/messages/{message_id}/read` endpoint. No UI reacts directly to the result in this local implementation.

- startWebSocket()
  - Starts an authenticated WebSocket to `wss://secra.top/ws/chats/{chatId}` using OkHttp. Adds Authorization header.
  - WebSocket `onOpen`: sets `isWebSocketConnected` and updates the status UI.
  - `onMessage`: parses JSON, and if type is `message.new` extracts the `message` object and calls `handleNewMessage` on UI thread.
  - `onClosed`/`onFailure`: set connection flags false; poll fallback remains active.

- updateStatusUI(message, visible)
  - Runs on UI thread to set a `textViewStatus` and its visibility.

- parseIsoDate(isoDate)
  - Parses ISO timestamp strings into epoch millis using a `SimpleDateFormat` configured to UTC. Returns `System.currentTimeMillis()` when parsing fails.

- Lifecycle (onResume/onPause)
  - `onResume` posts the poll runnable and attempts to start WebSocket if not connected.
  - `onPause` removes poll callbacks and closes websocket with normal close code.

Notes and security cues
- Decryption is attempted with a dual strategy in `SignalManager.decrypt`: first the HKDF-derived AES key (current messages), then the legacy method (first 16 bytes of the shared secret) for older messages; both use AES-GCM.
- Shared secret derivation uses X25519 KeyAgreement in `SignalManager.computeSharedSecret`.
- The helper `computeX3DHSharedSecret(...)` exists for a more advanced multi-DH flow, but the main chat flow currently uses the direct X25519 shared-secret path.
- The chat screen encrypts message text before sending it to the backend; the backend stores ciphertext, not plaintext.

-----------------------------

## com.example.myapplication.Conversation.java

Overview
- Simple model representing a conversation entry in the main list. Fields include chat id, target user id, contact name, last message text, a display time, image url, unread flag, and raw `lastMessageTime` ISO string for sorting.

-----------------------------

## com.example.myapplication.SettingsActivity.java

Overview
- Settings UI: theme selection, profile picture upload, switch account, and account deletion.

Key methods
- applyStatusBar(): adjusts window flags to support custom status bar coloring.
- onCreate(): initializes UI, loads avatar via `ProfileUtils`, sets theme radio group listener to toggle AppCompat night mode and applies status bar adjustments. `buttonSwitchAccount` clears session-only preferences and navigates to login.
- showDeleteConfirmationDialog(): prompts the user to type `DELETE` in an EditText to confirm account deletion — defensive UX.
- performAccountDeletion(): shows a non-cancelable progress dialog, calls `/api/users/me` DELETE; on success clears prefs and navigates to login; on failure shows a Toast.
- openGallery/onActivityResult/performActualUpload/markComplete/getBytes: full flow to select image, request an upload URL from backend (MinIO-style S3 pre-signed PUT), perform the PUT to the returned URL, then tell backend upload is complete. Uses Retrofit for the signed-url request and the final notify endpoint; uses Retrofit with a `@PUT` and `RequestBody` to perform the file upload.

Notes
- `getBytes(InputStream)` converts an `InputStream` to byte[] by reading chunks in a loop — typical helper.

-----------------------------

## com.example.myapplication.SearchUsersActivity.java

Overview
- Screen to find users by query and add them as friends. Filters out current user and existing friends.

Important behaviors
- fetchExistingFriends() loads the current friend list to a `Set` for quick exclusion from search results.
- searchUsers(query) calls backend search endpoint and filters out self and existing friends before updating the adapter.
- onAddFriendClick(User user) posts a friend request to the server; on success removes the user from current results and adds to `existingFriendIds`.

-----------------------------

## com.example.myapplication.UsersAdapter.java

Overview
- Adapter used by `SearchUsersActivity` to render user entries with a button to add friend. Uses `ProfileUtils` to load avatar and calls listener when Add Friend is pressed.

-----------------------------

## com.example.myapplication.RegisterActivity.java

Overview
- Handles new user registration. Generates an account-bound identity key locally, registers the user with the identity public key included in the registration request, then logs in and uploads the full key bundle.

Key steps
- onCreate: reads username/password, validates non-empty, generates account-bound identity keys, saves them in `Prefs`, creates an `AuthRequest` that includes the public key, and calls the register endpoint.
- On successful registration, calls `loginAfterRegister` which logs in and on success calls `fetchUserProfile(token)`.
- fetchUserProfile then saves user id and username and calls `uploadKeyBundle`.
- uploadKeyBundle generates signed prekey and OTP prekeys, optionally encrypts the identity private key for backup using the same username/password, and posts everything to `/api/users/{user_id}/keys`. On success it navigates to `MainActivity`; on failure it still proceeds (best-effort).

Important code path details
- The registration flow saves `pendingIdentityKeys`, `pendingRegistrationId`, `pendingUsername`, and `pendingPassword` so the app can build an encrypted backup blob after the backend account is created.
- If encrypted backup creation fails, registration still succeeds, but future restore on another device may require generating a fresh identity key pair.

-----------------------------

## API DTOs and retrofit client

- api/User.java — simple POJO for user id, username, and public_key.
- api/RetrofitClient.java — Builds Retrofit instance with base `https://secra.top` and an OkHttp client that logs bodies and checks for 401 responses to call `handleUnauthorized()` which clears prefs and starts `LoginActivity`.
- DTOs: `MessageResponse`, `MessageRequest`, `KeyBundleResponse`, `KeyBundleRequest`, `Chat`, `AuthResponse`, `AuthRequest` — simple POJOs matching backend JSON for requests and responses.
- `KeyBundleRequest` / `KeyBundleResponse` deserve special mention because they carry the encryption material:
  - `identity_key`: the X25519 identity public key.
  - `signed_prekey`: the current signed prekey public key.
  - `one_time_prekeys`: a list of public one-time prekeys.
  - `registration_id`: registration metadata for the backend.
  - `device_id`: device label such as `android-<model>`.
  - `encrypted_identity_private_key`: optional encrypted backup blob used when restoring an account on a new device.
- api/ApiService.java — Retrofit service interface describing all HTTP endpoints used by the app (auth, users, chats, messages, profile pictures, delete endpoints, key upload endpoints, etc.).

-----------------------------

## com.example.myapplication.util.ProfileUtils.java

Overview
- Helper to load profile pictures using Glide with a small memory cache map `urlCache` for download URLs.

Important behavior
- loadProfilePicture(context, username, imageView): checks username and context validity; if cached download URL exists it calls `loadImage`, otherwise requests a download URL from backend and caches it on success.
- loadImage: uses Glide to load the image with circleCrop and disk caching.
- loadPlaceholder: falls back to `R.drawable.ic_default_profile` when no server URL is available.
- isValidContext: defensive check to avoid Glide illegal state when activity is finishing/destroyed.

-----------------------------

## com.example.myapplication.util.Prefs.java

Overview
- Centralized storage for sensitive settings using `EncryptedSharedPreferences` when available, falling back to plain `SharedPreferences`.

Keys stored
- auth token, user id, username, identity keys, signed prekey, registration id, dark mode setting, and multiple `shared_secret_{userId}` entries.

Important methods
- init(context): creates `EncryptedSharedPreferences` using `MasterKey` with AES256; on exception falls back to standard `SharedPreferences`.
- save/get methods for token, user id, username.
- saveIdentityKeys, getIdentityPubKey, getIdentityPrivKey: to persist device keys.
- saveSignedPrekey/getSignedPrekey*: to store signed prekey pair.
- saveRegistrationId/getRegistrationId.
- saveSharedSecret/getSharedSecret: per-peer shared secret storage keyed by user id.
- clear(): wipes all stored entries.
- clearSessionOnly(): removes token, user id, username but keeps device keys and shared secrets.
- setCurrentUser(userId): prefixes user-scoped keys with the active user id so multiple accounts can coexist on one device without overwriting each other’s cryptographic material.

Security note
- `EncryptedSharedPreferences` is used when possible, with an AES256-GCM master key and AES256-SIV/AES256-GCM wrapping for keys and values. If initialization fails, the code gracefully degrades to unencrypted storage, which is functional but less secure.

-----------------------------

## com.example.myapplication.util.SignalManager.java

Overview
- Cryptographic helper utilities. Implements X25519 key generation, deterministic account-bound key derivation, encrypted private-key backup/restore, shared secret computation, AES-GCM message encryption/decryption, an HKDF-like KDF, and a simplified X3DH-style combination for multi-DH key derivation.

Key pieces
- generateKeyPair(): uses `KeyPairGenerator` for X25519 and returns Base64-encoded public/private bytes.
- generateAccountBoundKeyPair(username, password): derives a deterministic seed from the account credentials and constructs an X25519 keypair from that seed so the same account can be restored on another device.
- deriveAccountSeed(username, password): uses PBKDF2WithHmacSHA256 with a username-based salt and 120,000 iterations to produce 32 bytes of seed material.
- encryptIdentityPrivateKey(identityPrivateKeyB64, username, password): encrypts the identity private key with a password-derived AES key, using AES/GCM with a random 12-byte IV. The output is Base64(iv || ciphertext).
- decryptIdentityPrivateKey(encryptedKeyB64, username, password): reverses the above process and returns the restored Base64 private key.
- generateOneTimePrekeys(count): helper to create a list of public keys used as one-time prekeys.
- deriveAESKeyHKDF(sharedSecret): custom HKDF-like extract/expand using HMAC-SHA256 to derive a 16-byte AES key. It uses an all-zero salt and the fixed info string `AES_ENCRYPTION_KEY`.
- deriveAESKeyLegacy(sharedSecret): compatibility path that truncates the shared secret's first 16 bytes — used for decrypting older server messages.
- encrypt(plaintext, sharedSecret): derives the AES key from the shared secret, generates a random 12-byte IV, encrypts with AES-GCM, and returns Base64(iv || ciphertext).
- decrypt(base64Ciphertext, sharedSecret): attempts HKDF-derived key decryption first; if it fails, attempts legacy key decryption. If both fail, it rethrows the HKDF exception.
- computeSharedSecret(privateKeyStr, publicKeyStr): uses X25519 KeyAgreement to derive the raw shared secret bytes from the local private key and peer public key.
- computeX3DHSharedSecret(...): combines multiple ECDH outputs (DH1, DH2, optional DH3) and applies an HMAC-SHA256 KDF to produce a combined secret. This helper is implemented, but the main chat flow currently uses the simpler X25519 shared-secret path.

Security notes
- AES-GCM with a 12-byte IV and 128-bit tag is used for both message encryption and encrypted identity-backup storage.
- The HKDF implementation is simplified and uses a zero salt; for production, a standard HKDF implementation and stronger key lifecycle separation would be preferable.
- The account-bound identity key generation is intentional: it makes identity restoration possible, but it also means the username/password pair directly influences the key material.

-----------------------------

## com.example.myapplication.util.SystemBarUtils.java

Overview
- Small helpers for status bar styling. `applyBlueStatusBarWithWhiteIcons` sets the status bar color to the app `R.color.primary` and ensures icon contrast across Android versions.

-----------------------------

How to present this to your professor
- Use this Markdown file as a script. Each section explains what each class does and highlights important implementation details. If the professor wants truly line-by-line annotations I can produce a second document that reproduces the source code with inline comments for each and every source line. That will be significantly larger but I can generate it on request.

If you want a per-line annotated source copy (each line followed by a comment), reply "Please annotate per-line" and I will create annotated versions of the most important classes or the entire codebase.

--- End of file

