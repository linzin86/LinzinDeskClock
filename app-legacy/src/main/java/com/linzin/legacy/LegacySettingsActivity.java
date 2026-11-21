package com.linzin.legacy;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.linzin.clockcore.ClockLayoutMode;

public class LegacySettingsActivity extends PreferenceActivity {
    private static final int CALENDAR_PERMISSION = 211;
    private SharedPreferences prefs;
    private PreferenceScreen root;
    private LegacyUpdates updates;

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(LegacyLocales.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getPreferenceManager().setSharedPreferencesName("desk_clock_prefs");
        prefs = getPreferenceManager().getSharedPreferences();
        root = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(root);
        PreferenceCategory heading = new PreferenceCategory(this);
        heading.setTitle(R.string.settings_title); root.addPreference(heading);
        ListPreference language = choices("pref_app_language", R.string.pref_language_title,
            new CharSequence[] { getString(R.string.lang_follow_system), "繁體中文", "简体中文", "日本語", "English" },
            new String[] { "system", "zh-TW", "zh-CN", "ja", "en" }, prefs.getString("pref_app_language", "system"));
        language.setSummary(language.getEntry());
        language.setOnPreferenceChangeListener((preference, value) -> {
            getWindow().getDecorView().post(this::recreate); return true;
        });
        choices("pref_layout_mode", R.string.layout_section_title,
            new CharSequence[] { getString(R.string.layout_single), getString(R.string.layout_left), getString(R.string.layout_right) },
            new String[] { "SINGLE", "DUAL_LEFT_LARGE", "DUAL_RIGHT_LARGE" }, prefs.getString("pref_layout_mode", "SINGLE"));
        toggle("pref_24_hour", R.string.pref_24h_title, false);
        toggle("pref_show_battery", R.string.pref_battery_title, true);
        toggle("pref_show_corner_date", R.string.pref_corner_date_title, true);
        toggle("pref_weather_enabled", R.string.pref_weather_title, false);
        toggle("pref_is_fahrenheit", R.string.temp_unit_f, false);
        toggle("pref_show_calendar_events", R.string.pref_calendar_title, false);
        toggle("pref_manual_brightness", R.string.pref_brightness_title, false);
        Preference brightness = action("brightness", R.string.brightness_value, this::brightnessDialog);
        brightness.setSummary(Math.round(prefs.getFloat("pref_brightness_val", .6f) * 100) + "%");
        toggle("pref_flip_screen_180", R.string.pref_flip_180_title, false);
        ListPreference timeout = choices("pref_timeout_millis", R.string.pref_timeout_title,
            new CharSequence[] { getString(R.string.timeout_1m), getString(R.string.timeout_3m), getString(R.string.timeout_10m),
                getString(R.string.timeout_30m), getString(R.string.timeout_1h), getString(R.string.timeout_3h), getString(R.string.timeout_never) },
            new String[] { "60000", "180000", "600000", "1800000", "3600000", "10800000", "-1" },
            Long.toString(prefs.getLong("pref_timeout_millis", -1)), false);
        timeout.setSummary(timeout.getEntry());
        timeout.setOnPreferenceChangeListener((preference, value) -> {
            prefs.edit().putLong("pref_timeout_millis", Long.parseLong(value.toString())).apply();
            timeout.setValue(value.toString()); timeout.setSummary(timeout.getEntry()); return true;
        });
        action("tutorial", R.string.pref_tutorial_title, () -> LegacyTutorial.show(this));
        updates = new LegacyUpdates(this, action("check_update", R.string.update_check, () -> { }));
        Preference capabilities = new Preference(this); capabilities.setKey("capabilities");
        capabilities.setTitle(R.string.capabilities_title); capabilities.setSummary(R.string.capabilities_description);
        capabilities.setSelectable(false); root.addPreference(capabilities);
    }
    @Override protected void onResume() { super.onResume(); updates.resume(); }
    @Override protected void onPause() { updates.pause(); super.onPause(); }
    @Override protected void onDestroy() { updates.close(); super.onDestroy(); }
    private void toggle(String key, int title, boolean defaultValue) {
        CheckBoxPreference value = new CheckBoxPreference(this); value.setKey(key); value.setTitle(title);
        value.setDefaultValue(defaultValue); root.addPreference(value);
        if ("pref_weather_enabled".equals(key) || "pref_show_calendar_events".equals(key)) {
            value.setOnPreferenceChangeListener((preference, checked) -> {
                if (Boolean.TRUE.equals(checked)) {
                    if ("pref_show_calendar_events".equals(key) && android.os.Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission("android.permission.READ_CALENDAR") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[] { "android.permission.READ_CALENDAR" }, CALENDAR_PERMISSION); return false;
                    }
                    ensureDualLayout();
                }
                return true;
            });
        }
    }
    private void ensureDualLayout() {
        if (ClockLayoutMode.fromPersisted(prefs.getString("pref_layout_mode", "SINGLE")) == ClockLayoutMode.SINGLE)
            ((ListPreference) findPreference("pref_layout_mode")).setValue(ClockLayoutMode.DUAL_LEFT_LARGE.name());
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (android.os.Build.VERSION.SDK_INT >= 23) super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != CALENDAR_PERMISSION) return;
        boolean granted = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        ((CheckBoxPreference) findPreference("pref_show_calendar_events")).setChecked(granted);
        if (granted) ensureDualLayout();
        else android.widget.Toast.makeText(this, R.string.calendar_permission, android.widget.Toast.LENGTH_SHORT).show();
    }
    private ListPreference choices(String key, int title, CharSequence[] labels, String[] values, String current) {
        return choices(key, title, labels, values, current, true);
    }
    private ListPreference choices(String key, int title, CharSequence[] labels, String[] values, String current, boolean persistent) {
        ListPreference value = new ListPreference(this); value.setKey(key); value.setTitle(title);
        value.setPersistent(persistent); value.setEntries(labels); value.setEntryValues(values);
        root.addPreference(value); value.setValue(current); value.setSummary("%s"); return value;
    }
    private Preference action(String key, int title, Runnable action) {
        Preference value = new Preference(this); value.setKey(key); value.setTitle(title);
        value.setOnPreferenceClickListener(preference -> { action.run(); return true; }); root.addPreference(value); return value;
    }
    private void brightnessDialog() {
        if (getFragmentManager().findFragmentByTag("brightness") == null)
            new BrightnessDialog().show(getFragmentManager(), "brightness");
    }
    public static final class BrightnessDialog extends android.app.DialogFragment {
        private SeekBar bar;
        @Override public android.app.Dialog onCreateDialog(Bundle state) {
            android.app.Activity activity = getActivity();
            SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
            LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
            int pad = Math.round(16 * getResources().getDisplayMetrics().density); content.setPadding(pad, pad, pad, pad);
            TextView label = new TextView(activity); content.addView(label);
            bar = new SeekBar(activity); bar.setTag("brightness_slider"); bar.setMax(99);
            bar.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            bar.setProgress(state == null ? Math.round(prefs.getFloat("pref_brightness_val", .6f) * 100) - 1 : state.getInt("progress"));
            bar.setContentDescription(getString(R.string.brightness_value));
            label.setText((bar.getProgress() + 1) + "%"); content.addView(bar);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar seek, int progress, boolean user) { label.setText((progress + 1) + "%"); }
                public void onStartTrackingTouch(SeekBar seek) { }
                public void onStopTrackingTouch(SeekBar seek) { }
            });
            return new AlertDialog.Builder(activity).setTitle(R.string.brightness_value).setView(content)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    prefs.edit().putFloat("pref_brightness_val", (bar.getProgress() + 1) / 100f).apply();
                    ((PreferenceActivity) activity).findPreference("brightness").setSummary((bar.getProgress() + 1) + "%");
                }).create();
        }
        @Override public void onSaveInstanceState(Bundle state) {
            super.onSaveInstanceState(state); state.putInt("progress", bar.getProgress());
        }
    }
}
