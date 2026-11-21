package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

final class RuntimePermissionChecks {
    static void run(Instrumentation test, Activity clock) {
        String permission = "android.permission.READ_CALENDAR";
        if (clock.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) throw new AssertionError("Permission fixture requires revoked calendar access");
        android.accessibilityservice.AccessibilityServiceInfo info = test.getUiAutomation().getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(info);
        PreferenceActivity settings = (PreferenceActivity) ((LegacyInstrumentation) test).openSettings(clock);
        SettingsChecks.click(test, settings, settings.findPreference("pref_show_calendar_events"));
        if (clock.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_show_calendar_events", false))
            throw new AssertionError("Enabling calendar without runtime permission must wait for the permission result");
        press(test, "permission_deny_button");
        waitFor(test, () -> settings.hasWindowFocus());
        if (clock.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            || clock.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_show_calendar_events", false))
            throw new AssertionError("Denial must keep calendar events off");
        SettingsChecks.click(test, settings, settings.findPreference("pref_show_calendar_events"));
        press(test, "permission_allow_button");
        waitFor(test, () -> clock.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_show_calendar_events", false));
        if (clock.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            || !"DUAL_LEFT_LARGE".equals(clock.getSharedPreferences("desk_clock_prefs", 0).getString("pref_layout_mode", "")))
            throw new AssertionError("Granted calendar must become available in a dual pane");
        test.runOnMainSync(settings::finish); test.waitForIdleSync();
    }
    private static void press(Instrumentation test, String buttonId) {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
            if (root != null) nodes.add(root);
            boolean clicked = false;
            try {
                for (int i = 0; i < nodes.size() && i < 250; i++) {
                    AccessibilityNodeInfo node = nodes.get(i);
                    String id = node.getViewIdResourceName();
                    if (id != null && id.endsWith(":id/" + buttonId) && node.getPackageName() != null
                        && node.getPackageName().toString().endsWith(".permissioncontroller")) {
                        clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK); break;
                    }
                    for (int child = 0; child < node.getChildCount(); child++) {
                        AccessibilityNodeInfo item = node.getChild(child); if (item != null) nodes.add(item);
                    }
                }
            } finally { for (AccessibilityNodeInfo node : nodes) node.recycle(); }
            if (clicked) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing system permission action: " + buttonId);
    }
    private interface Condition { boolean met(); }
    private static void waitFor(Instrumentation test, Condition condition) {
        long deadline = SystemClock.elapsedRealtime() + 6000;
        while (!condition.met() && SystemClock.elapsedRealtime() < deadline) { SystemClock.sleep(50); test.waitForIdleSync(); }
        if (!condition.met()) throw new AssertionError("Permission result did not reach settings");
    }
}
