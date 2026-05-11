package com.example.myapplication;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.myapplication.util.Prefs;

public class MyApplication extends Application {
    private static MyApplication instance;

    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.init(this);
        
        // Force Light Theme as default
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}