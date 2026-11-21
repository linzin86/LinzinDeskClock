package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;

final class RotationChecks {
    static void run(Instrumentation test, Activity activity) {
        for (int orientation : new int[] { ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }) {
            test.runOnMainSync(() -> activity.setRequestedOrientation(orientation));
            long deadline = SystemClock.elapsedRealtime() + 5000;
            int expected = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
            while (activity.getResources().getConfiguration().orientation != expected && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50); test.waitForIdleSync();
            }
            if (activity.getResources().getConfiguration().orientation != expected) throw new AssertionError("Real orientation must change");
        }
    }
}
