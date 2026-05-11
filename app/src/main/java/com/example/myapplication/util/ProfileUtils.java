package com.example.myapplication.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.myapplication.R;
import com.example.myapplication.api.RetrofitClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileUtils {
    private static final String TAG = "ProfileUtils";
    private static final Map<String, String> urlCache = new HashMap<>();

    public static void loadProfilePicture(Context context, String username, ImageView imageView) {
        if (username == null || username.isEmpty() || !isValidContext(context)) {
            loadPlaceholder(context, username, imageView);
            return;
        }

        if (urlCache.containsKey(username)) {
            loadImage(context, urlCache.get(username), username, imageView);
            return;
        }

        String token = Prefs.getToken();
        if (token == null) {
            loadPlaceholder(context, username, imageView);
            return;
        }

        String authHeader = "Bearer " + token;
        RetrofitClient.getApiService().getDownloadUrl(authHeader, username).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (!isValidContext(context)) return;
                
                if (response.isSuccessful() && response.body() != null && response.body().containsKey("download_url")) {
                    String url = response.body().get("download_url");
                    urlCache.put(username, url);
                    loadImage(context, url, username, imageView);
                } else {
                    loadPlaceholder(context, username, imageView);
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                if (!isValidContext(context)) return;
                loadPlaceholder(context, username, imageView);
            }
        });
    }

    private static void loadImage(Context context, String url, String username, ImageView imageView) {
        if (!isValidContext(context)) return;
        
        Glide.with(context)
                .load(url)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(imageView);
    }

    private static void loadPlaceholder(Context context, String username, ImageView imageView) {
        if (!isValidContext(context)) return;
        
        String fallback = "https://i.pravatar.cc/150?u=" + (username != null ? username : "default");
        Glide.with(context)
                .load(fallback)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.mipmap.ic_launcher_round)
                .into(imageView);
    }

    private static boolean isValidContext(Context context) {
        if (context == null) return false;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return true;
    }

    public static void clearCache(String username) {
        urlCache.remove(username);
    }

    public static void clearAllCache() {
        urlCache.clear();
    }
}
