package com.linzin.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

final class LegacyDisplay {
    private final Activity activity;
    private final View screen, cover;
    private final SharedPreferences prefs;
    private final Runnable onSleep, onWake;
    private final Handler handler = new Handler();
    private boolean active, sleeping, wakingTouch, flipped;
    private int originalOrientation;
    private int lastClockOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Runnable releaseFlag;
    private final Runnable blackout;

    LegacyDisplay(Activity activity, View screen, Runnable onSleep, Runnable onWake, Bundle state) {
        this.activity = activity; this.screen = screen; this.onSleep = onSleep; this.onWake = onWake;
        prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        if (state != null) {
            originalOrientation = state.getInt("displayOriginalOrientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            lastClockOrientation = state.getInt("displayClockOrientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            flipped = state.getBoolean("displayFlipped", false);
        }
        cover = new View(activity); cover.setTag("sleep_cover"); cover.setBackgroundColor(Color.BLACK);
        cover.setContentDescription(activity.getString(R.string.tap_to_wake)); cover.setFocusable(true);
        cover.setOnClickListener(view -> interact()); cover.setVisibility(View.GONE);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(cover, new FrameLayout.LayoutParams(-1, -1));
        releaseFlag = () -> { if (active) activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); };
        blackout = () -> {
            if (!active) return;
            sleeping = true; cover.setVisibility(View.VISIBLE); screen.setVisibility(View.INVISIBLE);
            releaseFlag.run(); brightness(0); onSleep.run();
        };
    }
    void resume() {
        active = true;
        boolean flip = prefs.getBoolean("pref_flip_screen_180", false);
        if (flip != flipped) {
            if (flip) {
                originalOrientation = activity.getRequestedOrientation();
                activity.setRequestedOrientation(lastClockOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ? currentOrientation() : lastClockOrientation);
            } else activity.setRequestedOrientation(originalOrientation);
            flipped = flip;
        }
        screen.setRotation(flip ? 180 : 0);
        interact();
    }
    void interact() {
        if (!active) return;
        handler.removeCallbacksAndMessages(null);
        boolean wake = sleeping; sleeping = false;
        cover.setVisibility(View.GONE); screen.setVisibility(View.VISIBLE);
        float value = prefs.getFloat("pref_brightness_val", .6f);
        if (Float.isNaN(value) || Float.isInfinite(value)) value = .6f;
        brightness(prefs.getBoolean("pref_manual_brightness", false) ? Math.max(.01f, Math.min(1, value)) : -1);
        long timeout = prefs.getLong("pref_timeout_millis", -1);
        int systemTimeout;
        try { systemTimeout = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 15000); }
        catch (RuntimeException error) { systemTimeout = 15000; }
        systemTimeout = Math.max(1, systemTimeout);
        if (timeout < 0) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (timeout >= systemTimeout) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            handler.postDelayed(releaseFlag, timeout - systemTimeout);
        } else {
            releaseFlag.run(); handler.postDelayed(blackout, timeout);
        }
        if (wake) onWake.run();
    }
    boolean handleTouch(MotionEvent event) {
        if (active && sleeping && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            wakingTouch = true; interact();
        }
        if (!wakingTouch) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) wakingTouch = false;
        return true;
    }
    void pause() {
        lastClockOrientation = currentOrientation();
        active = false; sleeping = false; wakingTouch = false;
        handler.removeCallbacksAndMessages(null);
        cover.setVisibility(View.GONE); screen.setVisibility(View.VISIBLE);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); brightness(-1);
    }
    void saveState(Bundle state) {
        state.putInt("displayOriginalOrientation", originalOrientation);
        state.putInt("displayClockOrientation", lastClockOrientation);
        state.putBoolean("displayFlipped", flipped);
    }
    private void brightness(float value) {
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes(); attributes.screenBrightness = value;
        activity.getWindow().setAttributes(attributes);
    }
    private int currentOrientation() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            return rotation == android.view.Surface.ROTATION_0 || rotation == android.view.Surface.ROTATION_90
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        return rotation == android.view.Surface.ROTATION_0 || rotation == android.view.Surface.ROTATION_270
            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
    }
}
