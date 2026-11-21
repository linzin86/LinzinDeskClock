package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

final class WindowSettingsChecks {
    static Activity flipAcrossLanguage(Instrumentation test, Activity activity) {
        int original = activity.getRequestedOrientation();
        if (original != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            throw new AssertionError("Combination fixture requires an initially automatic clock orientation");
        LegacyInstrumentation runner = (LegacyInstrumentation) test;
        android.preference.PreferenceActivity settings = (android.preference.PreferenceActivity) runner.openSettings(activity);
        SettingsChecks.click(test, settings, settings.findPreference("pref_flip_screen_180"));
        test.runOnMainSync(settings::finish);
        Activity clock = runner.awaitResumed(activity.getComponentName().getClassName(), null);
        waitFor(() -> ((ViewGroup) clock.findViewById(android.R.id.content)).getChildAt(0).getRotation() == 180,
            "Clock must be flipped before the language change");
        Activity recreated = LanguageSettingsChecks.run(test, clock);
        if (recreated == clock) throw new AssertionError("Language must actually recreate the flipped clock");
        android.preference.PreferenceActivity translated = (android.preference.PreferenceActivity) runner.openSettings(recreated);
        SettingsChecks.click(test, translated, translated.findPreference("pref_flip_screen_180"));
        test.runOnMainSync(translated::finish);
        runner.awaitResumed(recreated.getComponentName().getClassName(), null);
        waitFor(() -> recreated.getRequestedOrientation() == original
            && ((ViewGroup) recreated.findViewById(android.R.id.content)).getChildAt(0).getRotation() == 0,
            "Disabling flip after language recreation must restore the original automatic orientation");
        return recreated;
    }
    static void reverseRotation(Instrumentation test, Activity activity, boolean portrait) {
        test.runOnMainSync(() -> activity.setRequestedOrientation(portrait
            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE));
        waitFor(() -> (activity.getWindowManager().getDefaultDisplay().getWidth() < activity.getWindowManager().getDefaultDisplay().getHeight()) == portrait
            && activity.getWindowManager().getDefaultDisplay().getRotation() == (portrait ? 2 : 3)
            && activity.getResources().getConfiguration().orientation == (portrait ? 1 : 2),
            "Reverse orientation precondition must reach the real display");
        int before = activity.getWindowManager().getDefaultDisplay().getRotation();
        {
            Activity opened = ((LegacyInstrumentation) test).openSettings(activity);
            if (!(opened instanceof android.preference.PreferenceActivity)) throw new AssertionError("Settings must open");
            android.preference.PreferenceActivity preferences = (android.preference.PreferenceActivity) opened;
            SettingsChecks.click(test, preferences, preferences.findPreference("pref_flip_screen_180"));
            test.runOnMainSync(preferences::finish);
            View screen = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            waitFor(() -> activity.hasWindowFocus() && screen.getRotation() == 180
                && activity.getWindowManager().getDefaultDisplay().getRotation() == before,
                "Flip must restore the returning clock's original display pose; expected=" + before);
            int after = activity.getWindowManager().getDefaultDisplay().getRotation();
            if (before != after) throw new AssertionError("Content flip must lock the original display pose, including reverse orientations; before=" + before + " after=" + after);
        }
    }
    static void window(Instrumentation test, Activity activity) {
        if (Math.abs(activity.getWindow().getAttributes().screenBrightness - .25f) > .001f)
            throw new AssertionError("Manual brightness must apply to the clock window");
        View screen = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        if (screen.getRotation() != 180 || activity.getRequestedOrientation() == -1)
            throw new AssertionError("180-degree mode must rotate content and lock the current orientation");
        Instrumentation.ActivityMonitor monitor = test.addMonitor("com.linzin.legacy.LegacySettingsActivity", null, false);
        try {
            Button settings = activity.getWindow().getDecorView().findViewWithTag("settings");
            test.runOnMainSync(() -> settings.performClick());
            Activity preferences = test.waitForMonitorWithTimeout(monitor, 5000);
            if (preferences == null) throw new AssertionError("Settings must open");
            waitFor(() -> activity.getWindow().getAttributes().screenBrightness == -1 && !keepsScreenOn(activity), "Paused clock must release brightness and keep-screen-on");
            SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
            prefs.edit().putBoolean("pref_manual_brightness", false).putBoolean("pref_flip_screen_180", false).commit();
            test.runOnMainSync(preferences::finish);
            waitFor(() -> keepsScreenOn(activity) && screen.getRotation() == 0, "Returning must reload settings and restore normal rotation");
            if (activity.getWindow().getAttributes().screenBrightness != -1) throw new AssertionError("System brightness must be restored");
        } finally { test.removeMonitor(monitor); }
        LegacyInstrumentation lifecycle = (LegacyInstrumentation) test;
        int stops = lifecycle.clockStops, resumes = lifecycle.clockResumes;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        test.runOnMainSync(() -> activity.startActivity(home));
        waitFor(() -> lifecycle.clockStops > stops && !keepsScreenOn(activity), "Real Home must stop the clock and release the display flag");
        Intent launcher = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        test.runOnMainSync(() -> test.getTargetContext().startActivity(launcher));
        waitFor(() -> lifecycle.clockResumes > resumes, "Launcher must actually resume the stopped clock");
        if (!keepsScreenOn(activity)) throw new AssertionError("Launcher return must restore never-timeout display without another touch");
    }
    static void timeout(Instrumentation test, Activity activity) {
        View[] cover = new View[1];
        waitFor(() -> {
            test.runOnMainSync(() -> cover[0] = activity.getWindow().getDecorView().findViewWithTag("sleep_cover"));
            return cover[0] != null && cover[0].isShown();
        }, "A timeout shorter than the system timeout must show a wakeable blackout");
        if (keepsScreenOn(activity) || activity.getWindow().getAttributes().screenBrightness != 0)
            throw new AssertionError("Sleeping clock must release the display flag and use minimum window brightness");
        SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        boolean seconds = prefs.getBoolean("pref_digital_show_sec", false);
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP }) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 50, 100, 0);
            try { test.runOnMainSync(() -> activity.dispatchTouchEvent(event)); }
            finally { event.recycle(); }
        }
        if (cover[0].isShown() || prefs.getBoolean("pref_digital_show_sec", false) != seconds)
            throw new AssertionError("Wake touch must reveal the clock without changing settings");
        if (Math.abs(activity.getWindow().getAttributes().screenBrightness - .25f) > .001f)
            throw new AssertionError("Wake must restore configured brightness");
    }
    static void longTimeout(Instrumentation test, Activity activity) {
        if (!keepsScreenOn(activity)) throw new AssertionError("Long finite timeout must initially keep the screen on");
        waitFor(() -> !keepsScreenOn(activity), "Long finite timeout must return control to the system timeout");
        View cover = activity.getWindow().getDecorView().findViewWithTag("sleep_cover");
        if (cover.isShown() || activity.getWindow().getAttributes().screenBrightness == 0)
            throw new AssertionError("Long timeout must use real system sleep rather than fake blackout");
        test.runOnMainSync(activity::onUserInteraction);
        if (!keepsScreenOn(activity)) throw new AssertionError("Interaction must restart a finite timeout");
    }
    private interface Condition { boolean met(); }
    private static void waitFor(Condition condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 6000;
        while (!condition.met() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        if (!condition.met()) throw new AssertionError(message);
    }
    private static boolean keepsScreenOn(Activity activity) {
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }
}
