package com.example.myapplication;

import android.app.Application;
import com.example.myapplication.util.Prefs;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
    }
}