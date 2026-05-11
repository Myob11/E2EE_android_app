package com.example.myapplication.api;

import android.content.Intent;
import com.example.myapplication.LoginActivity;
import com.example.myapplication.MyApplication;
import com.example.myapplication.util.Prefs;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String BASE_URL = "https://secra.top";
    private static Retrofit retrofit = null;

    public static ApiService getApiService() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        Response response = chain.proceed(chain.request());
                        if (response.code() == 401) {
                            // Token expired or invalid
                            handleUnauthorized();
                        }
                        return response;
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    private static void handleUnauthorized() {
        Prefs.clear();
        Intent intent = new Intent(MyApplication.getInstance(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MyApplication.getInstance().startActivity(intent);
    }
}
