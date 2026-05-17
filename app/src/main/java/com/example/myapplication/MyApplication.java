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

// Apply saved theme preference
        boolean isDark = Prefs.isDarkMode();
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}