package com.example.myapplication.api;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("/api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("/api/users/me")
    Call<User> getMe(@Header("Authorization") String token);

    @GET("/api/users")
    Call<List<User>> searchUsers(@Header("Authorization") String token, @Query("query") String query);

    @POST("/api/users/{user_id}/friends")
    Call<Map<String, String>> addFriend(@Header("Authorization") String token, @Path("user_id") String currentUserId, @Body Map<String, String> body);

    @GET("/api/users/{user_id}/friends")
    Call<List<User>> getFriends(@Header("Authorization") String token, @Path("user_id") String currentUserId);

    @GET("/api/chats")
    Call<List<Chat>> getChats(@Header("Authorization") String token, @Query("user_id") String userId);

    @POST("/api/chats")
    Call<Chat> createChat(@Header("Authorization") String token, @Body Map<String, Object> body);

    @POST("/api/chats/{chat_id}/messages")
    Call<MessageResponse> sendMessage(@Header("Authorization") String token, @Path("chat_id") String chatId, @Body MessageRequest request);

    @GET("/api/chats/{chat_id}/messages")
    Call<List<MessageResponse>> getMessages(@Header("Authorization") String token, @Path("chat_id") String chatId);
}