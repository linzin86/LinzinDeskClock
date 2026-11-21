package com.linzin.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.preference.PreferenceActivity;
import android.widget.SeekBar;

final class BrightnessSettingsChecks {
    static void run(Instrumentation test, Activity clock) {
        PreferenceActivity settings = (PreferenceActivity) ((LegacyInstrumentation) test).openSettings(clock);
        SettingsChecks.click(test, settings, settings.findPreference("pref_manual_brightness"));
        SettingsChecks.click(test, settings, settings.findPreference("brightness"));
        AlertDialog dialog = dialog(settings);
        SeekBar bar = dialog.getWindow().getDecorView().findViewWithTag("brightness_slider");
        test.runOnMainSync(() -> { bar.setProgress(19); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); });
        test.waitForIdleSync();
        if (clock.getSharedPreferences("desk_clock_prefs", 0).getFloat("pref_brightness_val", .6f) != .6f)
            throw new AssertionError("Cancel must preserve the existing brightness");
        SettingsChecks.click(test, settings, settings.findPreference("brightness"));
        AlertDialog changing = dialog(settings);
        SeekBar slider = changing.getWindow().getDecorView().findViewWithTag("brightness_slider");
        test.runOnMainSync(() -> slider.setProgress(39));
        Instrumentation.ActivityMonitor monitor = test.addMonitor(settings.getComponentName().getClassName(), null, false);
        try {
            PreferenceActivity original = settings;
            int target = original.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            test.runOnMainSync(() -> original.setRequestedOrientation(target));
            long deadline = android.os.SystemClock.elapsedRealtime() + 6000;
            Activity rotated;
            do {
                android.os.SystemClock.sleep(50); rotated = monitor.getLastActivity();
            } while ((rotated == null || rotated == original) && android.os.SystemClock.elapsedRealtime() < deadline);
            if (!(rotated instanceof PreferenceActivity) || rotated == original) throw new AssertionError("Settings must recreate for real rotation");
            settings = (PreferenceActivity) rotated; test.waitForIdleSync();
            AlertDialog restored = dialog(settings);
            SeekBar restoredSlider = restored.getWindow().getDecorView().findViewWithTag("brightness_slider");
            if (restoredSlider.getProgress() != 39) throw new AssertionError("Rotation must retain the unsaved slider value");
            test.runOnMainSync(() -> restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick()); test.waitForIdleSync();
            if (Math.abs(clock.getSharedPreferences("desk_clock_prefs", 0).getFloat("pref_brightness_val", 0) - .4f) > .001f)
                throw new AssertionError("Confirmed slider value must persist");
            if (!"40%".equals(settings.findPreference("brightness").getSummary())) throw new AssertionError("Brightness summary must reflect confirmation");
            PreferenceActivity last = settings; test.runOnMainSync(last::finish); test.waitForIdleSync();
            if (Math.abs(clock.getWindow().getAttributes().screenBrightness - .4f) > .001f) throw new AssertionError("Clock must apply the confirmed brightness");
        } finally { test.removeMonitor(monitor); }
    }
    private static AlertDialog dialog(Activity activity) {
        android.app.Fragment fragment = activity.getFragmentManager().findFragmentByTag("brightness");
        if (!(fragment instanceof DialogFragment) || !(((DialogFragment) fragment).getDialog() instanceof AlertDialog))
            throw new AssertionError("Brightness dialog must be available; activity=" + activity + " fragment=" + fragment
                + " dialog=" + (fragment instanceof DialogFragment ? ((DialogFragment) fragment).getDialog() : null));
        return (AlertDialog) ((DialogFragment) fragment).getDialog();
    }
}
