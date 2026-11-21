package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import java.util.concurrent.atomic.AtomicReference;

final class DisplayChecks {
    static void run(Instrumentation test, Activity activity) {
        RotationChecks.run(test, activity);
        float[] baseline = new float[1];
        test.runOnMainSync(() -> baseline[0] = ((Button) activity.getWindow().getDecorView().findViewWithTag("clock_style")).getTextSize());
        Configuration configuration = new Configuration(activity.getResources().getConfiguration()); configuration.fontScale = 2;
        DisplayMetrics metrics = new DisplayMetrics(); metrics.setTo(activity.getResources().getDisplayMetrics());
        Resources large = new Resources(activity.getAssets(), metrics, configuration);
        ContextThemeWrapper context = new ContextThemeWrapper(activity, android.R.style.Theme_Holo_NoActionBar) {
            @Override public Resources getResources() { return large; }
        };
        activity.getSharedPreferences("desk_clock_prefs", 0).edit().putString("pref_layout_mode", "SINGLE").putInt("pref_side_page", 0).commit();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        LegacyClockScreen[] screen = new LegacyClockScreen[1];
        test.runOnMainSync(() -> {
            try {
                screen[0] = new LegacyClockScreen(context, false, () -> { }, () -> { });
                activity.setContentView(screen[0]); screen[0].resume();
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new RuntimeException("Large-font fixture failed to construct", failure.get());
        test.waitForIdleSync();
        try {
            LayoutChecks.run(test, activity);
            for (String tag : new String[] { "clock_style", "clock_seconds", "time_mode", "layout_mode", "side_page", "ui_lock", "settings" }) {
                View[] control = new View[1];
                test.runOnMainSync(() -> control[0] = screen[0].findViewWithTag(tag));
                float target = 48 * large.getDisplayMetrics().density;
                if (control[0].getWidth() + 1 < target || control[0].getHeight() + 1 < target)
                    throw new AssertionError("Large-font control needs a 48dp target: " + tag);
            }
            Button style = screen[0].findViewWithTag("clock_style");
            if (style.getTextSize() < baseline[0] * 1.8f) throw new AssertionError("FontScale2 must reach real TextViews");
        } finally { test.runOnMainSync(() -> screen[0].pause()); }
    }
}
