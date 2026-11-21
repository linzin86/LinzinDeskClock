package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.widget.Button;
import java.util.TimeZone;

final class AccessibilityChecks {
    static void seconds(Instrumentation test, Activity activity) {
        Button[] seconds = new Button[1];
        test.runOnMainSync(() -> seconds[0] = activity.getWindow().getDecorView().findViewWithTag("clock_seconds"));
        if (seconds[0] == null) throw new AssertionError("Seconds need a standard accessible button");
        SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        Button style = activity.getWindow().getDecorView().findViewWithTag("clock_style");
        for (String family : new String[] { "digital", "analog", "flip" }) {
            String key = "pref_" + family + "_show_sec";
            boolean before = prefs.getBoolean(key, "analog".equals(family));
            String label = seconds[0].getText().toString();
            test.runOnMainSync(() -> seconds[0].performClick());
            if (prefs.getBoolean(key, before) == before || label.equals(seconds[0].getText().toString()))
                throw new AssertionError("Seconds button must toggle and expose its new action: " + family);
            float target = 48 * activity.getResources().getDisplayMetrics().density;
            if (seconds[0].getWidth() < target || seconds[0].getHeight() < target)
                throw new AssertionError("Seconds button needs a 48dp target");
            int count = "digital".equals(family) || "analog".equals(family) ? 3 : 2;
            for (int i = 0; i < count; i++) test.runOnMainSync(() -> style.performClick());
        }
        Button lock = activity.getWindow().getDecorView().findViewWithTag("ui_lock");
        boolean before = prefs.getBoolean("pref_digital_show_sec", false);
        test.runOnMainSync(() -> { lock.performClick(); seconds[0].performClick(); });
        if (seconds[0].isEnabled() || prefs.getBoolean("pref_digital_show_sec", false) != before)
            throw new AssertionError("Locked seconds control must not change preferences");
        test.runOnMainSync(() -> lock.performClick());
    }
    static void date(Instrumentation test, Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        LegacyClockView clock = activity.getWindow().getDecorView().findViewWithTag("clock");
        TimeZone original = TimeZone.getDefault();
        boolean originalDate = prefs.getBoolean("pref_show_corner_date", true);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            for (boolean visible : new boolean[] { true, false }) {
                prefs.edit().putBoolean("pref_show_corner_date", visible).commit();
                String[] readout = new String[1];
                test.runOnMainSync(() -> { clock.stop(); clock.reloadSettings(); clock.updateTime(1772283909000L); readout[0] = clock.getContentDescription().toString(); });
                if (readout[0].contains("2/28") != visible)
                    throw new AssertionError("Readout must include exactly the visible date: " + readout[0]);
            }
        } finally {
            TimeZone.setDefault(original);
            prefs.edit().putBoolean("pref_show_corner_date", originalDate).commit();
            test.runOnMainSync(() -> { clock.reloadSettings(); clock.start(); });
        }
    }
}
