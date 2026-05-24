package com.example.friddgy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    public static class ThemePreset {
        public final String name;
        public final String accentColor;
        public final String secondaryColor;

        public ThemePreset(String name, String accentColor, String secondaryColor) {
            this.name = name;
            this.accentColor = accentColor;
            this.secondaryColor = secondaryColor;
        }
    }

    private static final String PREF_NAME = "friddgy_custom_prefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_THEME_NAME = "theme_name";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";

    public static final List<ThemePreset> PRESETS = new ArrayList<>();
    static {
        PRESETS.add(new ThemePreset("Warm Amber", "#FBB72C", "#00855F"));
        PRESETS.add(new ThemePreset("Electric Teal", "#00F5D4", "#7B2CBF"));
        PRESETS.add(new ThemePreset("Neon Rose", "#FF007F", "#FF5E36"));
        PRESETS.add(new ThemePreset("Verdant Lime", "#ADFF2F", "#00A896"));
        PRESETS.add(new ThemePreset("Cosmic Indigo", "#E295FC", "#3A86C8"));
    }

    public static final List<String> AVATARS = new ArrayList<>();
    static {
        AVATARS.add("https://i.pravatar.cc/150?img=47"); // kristin
        AVATARS.add("https://i.pravatar.cc/150?img=32"); // chef boy
        AVATARS.add("https://i.pravatar.cc/150?img=60"); // chef lady
        AVATARS.add("https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=150&q=80"); // chef cat
    }

    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, "Kristin");
    }

    public static void setUserName(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    public static String getAvatarUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_AVATAR_URL, "");
    }

    public static void setAvatarUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_AVATAR_URL, url).apply();
    }

    public static ThemePreset getCurrentTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_THEME_NAME, "Warm Amber");
        for (ThemePreset preset : PRESETS) {
            if (preset.name.equals(name)) {
                return preset;
            }
        }
        return PRESETS.get(0);
    }

    public static void setCurrentTheme(Context context, String themeName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_NAME, themeName).apply();
    }

    public static String getGeminiApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_GEMINI_API_KEY, "");
    }

    public static void setGeminiApiKey(Context context, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply();
    }

    public static void applyTouchScaleAnimation(android.view.View view, final Runnable onClick) {
        view.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f)).start();
                        if (onClick != null) {
                            v.postDelayed(onClick, 100);
                        }
                        break;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                        break;
                }
                return true;
            }
        });
    }
}
