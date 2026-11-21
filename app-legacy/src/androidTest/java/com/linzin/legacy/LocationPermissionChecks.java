package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;

final class LocationPermissionChecks {
    static void coarse(Instrumentation test, Activity activity) {
        if (activity.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") != PackageManager.PERMISSION_GRANTED
            || activity.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED)
            throw new AssertionError("Coarse-only fixture requires approximate access without precise permission");
        Button locate = activity.getWindow().getDecorView().findViewWithTag("weather_locate");
        test.runOnMainSync(() -> locate.performClick());
        long deadline = SystemClock.elapsedRealtime() + 45000;
        String[] value = new String[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> value[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        } while (!value[0].contains("26.0") && SystemClock.elapsedRealtime() < deadline);
        if (!value[0].contains("26.0") || !activity.hasWindowFocus())
            throw new AssertionError("Approximate access must allow weather without requesting precise permission: " + value[0]);
    }
    static void run(Instrumentation test, Activity activity) {
        if (permitted(activity)) throw new AssertionError("Location permission fixture requires denied access");
        RuntimePermissionChecks.configure(test);
        Button locate = activity.getWindow().getDecorView().findViewWithTag("weather_locate");
        Button refresh = activity.getWindow().getDecorView().findViewWithTag("weather_refresh");
        if (locate == null) throw new AssertionError("Location needs an accessible permission-aware entry");
        test.runOnMainSync(() -> locate.performClick());
        RuntimePermissionChecks.press(test, "permission_deny_button");
        waitFor(() -> activity.hasWindowFocus() && locate.isEnabled());
        if (permitted(activity) || !refresh.isEnabled()) throw new AssertionError("Denial must preserve manual refresh using saved coordinates");
        test.runOnMainSync(() -> locate.performClick());
        RuntimePermissionChecks.press(test, "permission_allow_foreground_only_button");
        waitFor(() -> permitted(activity));
        long deadline = SystemClock.elapsedRealtime() + 45000;
        String[] value = new String[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> value[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        } while (!value[0].contains("26.0") && SystemClock.elapsedRealtime() < deadline);
        if (!value[0].contains("26.0")) throw new AssertionError("Grant must resume the explicitly requested weather/location action: " + value[0]);
    }
    private static boolean permitted(Activity activity) {
        return activity.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED
            || activity.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED;
    }
    private interface Condition { boolean met(); }
    private static void waitFor(Condition condition) {
        long deadline = SystemClock.elapsedRealtime() + 6000;
        while (!condition.met() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        if (!condition.met()) throw new AssertionError("Location permission result did not reach the clock");
    }
}
