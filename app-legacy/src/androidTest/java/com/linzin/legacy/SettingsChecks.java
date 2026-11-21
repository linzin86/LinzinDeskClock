package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.widget.ListView;

final class SettingsChecks {
    static void run(Instrumentation test, Activity clock) {
        {
            Activity activity = ((LegacyInstrumentation) test).openSettings(clock);
            if (!(activity instanceof PreferenceActivity)) throw new AssertionError("Native settings must open");
            PreferenceActivity settings = (PreferenceActivity) activity;
            test.waitForIdleSync();
            SharedPreferences prefs = clock.getSharedPreferences("desk_clock_prefs", 0);
            for (String key : new String[] { "pref_24_hour", "pref_show_battery", "pref_show_corner_date", "pref_weather_enabled",
                "pref_is_fahrenheit", "pref_show_calendar_events", "pref_manual_brightness", "pref_flip_screen_180" }) {
                // The separate runtimePermission case owns denial/grant through the system UI.
                if ("pref_show_calendar_events".equals(key) && android.os.Build.VERSION.SDK_INT >= 23
                    && settings.checkSelfPermission("android.permission.READ_CALENDAR") != android.content.pm.PackageManager.PERMISSION_GRANTED) continue;
                Preference preference = settings.findPreference(key);
                if (!(preference instanceof CheckBoxPreference)) throw new AssertionError("Missing boolean setting: " + key);
                boolean original = ((CheckBoxPreference) preference).isChecked();
                click(test, settings, preference);
                if (prefs.getBoolean(key, original) == original) throw new AssertionError("Setting must persist its user action: " + key);
                click(test, settings, preference);
            }
            ListPreference timeout = (ListPreference) settings.findPreference("pref_timeout_millis");
            if (timeout == null) throw new AssertionError("Timeout needs all original choices");
            choose(test, settings, timeout, 0);
            if (prefs.getLong("pref_timeout_millis", -1) != 60000) throw new AssertionError("Timeout must keep the existing long schema");
            choose(test, settings, timeout, 6);
            if (prefs.getLong("pref_timeout_millis", 0) != -1) throw new AssertionError("Never timeout must persist");
            ListPreference language = (ListPreference) settings.findPreference("pref_app_language");
            if (language == null || language.getEntryValues().length != 5) throw new AssertionError("Five language choices are required");
            if (settings.findPreference("tutorial") == null || settings.findPreference("capabilities") == null)
                throw new AssertionError("Settings must expose help and platform capability limits");
            test.runOnMainSync(settings::finish); test.waitForIdleSync();
        }
    }
    static void click(Instrumentation test, PreferenceActivity activity, Preference preference) {
        ListView list = activity.getListView();
        int position = -1;
        for (int i = 0; i < list.getAdapter().getCount(); i++) if (list.getAdapter().getItem(i) == preference) { position = i; break; }
        if (position < 0) throw new AssertionError("Preference must be reachable: " + preference.getKey());
        final int row = position;
        test.runOnMainSync(() -> list.performItemClick(null, row, list.getAdapter().getItemId(row)));
        test.waitForIdleSync();
    }
    static void choose(Instrumentation test, PreferenceActivity activity, ListPreference preference, int row) {
        click(test, activity, preference);
        AlertDialog dialog = (AlertDialog) preference.getDialog();
        if (dialog == null || !dialog.isShowing()) throw new AssertionError("Choice dialog must open");
        test.runOnMainSync(() -> dialog.getListView().performItemClick(null, row, row));
        test.waitForIdleSync();
    }
}
