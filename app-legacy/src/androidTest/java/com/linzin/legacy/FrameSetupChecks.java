package com.linzin.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import java.util.Arrays;

final class FrameSetupChecks {
    private static final String[] STYLES = {"TK", "TTN", "RAFM", "ABW", "OET", "ZMG", "PP", "FFT"};

    static void run(LegacyInstrumentation test, String target) {
        if (!Arrays.asList(STYLES).contains(target)) throw new IllegalArgumentException("Unknown frame style: " + target);
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().putInt("pref_main_page", 0).putBoolean("pref_24_hour", false)
            .putBoolean("pref_digital_show_sec", true).putBoolean("pref_analog_show_sec", true)
            .putBoolean("pref_flip_show_sec", true).putBoolean("pref_show_calendar_events", false).commit();
        test.launch();
        select(test, target);
        test.runOnMainSync(() -> {
            Button selected = test.activity.getWindow().getDecorView().findViewWithTag("clock_style");
            if (!target.equals(selected.getText().toString())) throw new AssertionError("Expected frame style " + target);
        });
        if (!prefs.edit().putLong("pref_timeout_millis", -1).commit()) throw new AssertionError("Frame settings must persist");
        test.launch();
        test.runOnMainSync(() -> {
            Button restored = test.activity.getWindow().getDecorView().findViewWithTag("clock_style");
            if (!target.equals(restored.getText().toString())) throw new AssertionError("Frame style must survive reopening");
            Button seconds = test.activity.getWindow().getDecorView().findViewWithTag("clock_seconds");
            int label = test.activity.getResources().getIdentifier("hide_seconds", "string", test.activity.getPackageName());
            if (label == 0) throw new AssertionError("Seconds label resource must exist");
            String expected = test.activity.getString(label);
            if (!expected.equals(seconds.getText().toString())) throw new AssertionError("Seconds must be enabled");
        });
    }

    private static void select(LegacyInstrumentation test, String target) {
        test.runOnMainSync(() -> {
            Button selector = test.activity.getWindow().getDecorView().findViewWithTag("clock_style");
            for (int i = 0; i < STYLES.length && !target.equals(selector.getText().toString()); i++) selector.performClick();
        });
        test.waitForIdleSync();
    }
}
