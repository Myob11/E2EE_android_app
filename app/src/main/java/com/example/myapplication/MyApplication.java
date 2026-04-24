package com.example.myapplication;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.myapplication.util.Prefs;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        
        // Force Light Theme as default
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}