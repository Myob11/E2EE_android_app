package com.example.myapplication.api;

import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface ApiService {
    @POST("/api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("/api/users/me")
    Call<User> getMe(@Header("Authorization") String token);

    @GET("/api/users/{user_id}/public-key")
    Call<Map<String, String>> getPublicKey(@Path("user_id") String userId);

    @GET("/api/users")
    Call<List<User>> searchUsers(@Header("Authorization") String token, @Query("query") String query);

    @POST("/api/users/{user_id}/friends")
    Call<Map<String, String>> addFriend(@Header("Authorization") String token, @Path("user_id") String currentUserId, @Body Map<String, String> body);

    @GET("/api/users/{user_id}/friends")
    Call<List<User>> getFriends(@Header("Authorization") String token, @Path("user_id") String currentUserId);

    @POST("/api/users/{user_id}/keys")
    Call<Void> uploadKeys(@Header("Authorization") String token, @Path("user_id") String userId, @Body KeyBundleRequest request);

    @GET("/api/users/{user_id}/bundle")
    Call<KeyBundleResponse> getKeyBundle(@Header("Authorization") String token, @Path("user_id") String userId);

    @GET("/api/chats")
    Call<List<Chat>> getChats(@Header("Authorization") String token, @Query("user_id") String userId);

    @POST("/api/chats")
    Call<Chat> createChat(@Header("Authorization") String token, @Body Map<String, Object> body);

    @POST("/api/chats/{chat_id}/messages")
    Call<MessageResponse> sendMessage(@Header("Authorization") String token, @Path("chat_id") String chatId, @Body MessageRequest request);

    @GET("/api/chats/{chat_id}/messages")
    Call<List<MessageResponse>> getMessages(
            @Header("Authorization") String token,
            @Path("chat_id") String chatId,
            @Query("limit") Integer limit,
            @Query("before") String before
    );

    @POST("/api/messages/{message_id}/read")
    Call<Map<String, Object>> markAsRead(@Header("Authorization") String token, @Path("message_id") String messageId);

    // Profile Picture Endpoints
    @POST("/api/profiles/{username}/picture")
    Call<Map<String, String>> getUploadUrl(@Header("Authorization") String token, @Path("username") String username, @Body Map<String, String> body);

    @PUT
    Call<Void> uploadImage(@Url String url, @Body RequestBody image);

    @POST("/api/profiles/{username}/picture/complete")
    Call<Map<String, Object>> markUploadComplete(@Header("Authorization") String token, @Path("username") String username, @Body Map<String, Object> body);

    @GET("/api/profiles/{username}/picture")
    Call<Map<String, String>> getDownloadUrl(@Header("Authorization") String token, @Path("username") String username);

    @DELETE("/api/chats/{chat_id}")
    Call<Map<String, Object>> deleteChat(@Header("Authorization") String token, @Path("chat_id") String chatId);

    // Fallback for deployments that expose delete as POST action routes
    @POST("/api/chats/{chat_id}/delete")
    Call<Map<String, Object>> deleteChatPostAction(@Header("Authorization") String token, @Path("chat_id") String chatId);

    @POST("/api/chats/{chat_id}/remove")
    Call<Map<String, Object>> deleteChatPostRemove(@Header("Authorization") String token, @Path("chat_id") String chatId);

    // Delete the authenticated user account and cascade cleanup on backend
    @DELETE("/api/users/me")
    Call<Map<String, Object>> deleteMe(@Header("Authorization") String token);
}