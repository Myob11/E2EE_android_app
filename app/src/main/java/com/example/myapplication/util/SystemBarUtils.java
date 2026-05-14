package com.example.myapplication.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

public final class SystemBarUtils {

    private SystemBarUtils() {
    }

    public static void applyBlueStatusBarWithWhiteIcons(Activity activity) {
        Window window = activity.getWindow();
        int blue = ContextCompat.getColor(activity, R.color.primary);
        window.setStatusBarColor(blue);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } else {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }
}