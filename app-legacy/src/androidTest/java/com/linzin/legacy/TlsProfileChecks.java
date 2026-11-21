package com.linzin.legacy;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import java.io.IOException;
import java.net.URL;

final class TlsProfileChecks {
    private interface Work { void run() throws Exception; }

    private static void measure(Context context, Work work, StringBuilder metrics) throws Exception {
        try (RuntimeSamples samples = new RuntimeSamples()) {
            try { work.run(); }
            finally { metrics.append(samples.snapshot(context)); }
        }
    }

    static void check(Context context) throws Exception {
        StringBuilder success = new StringBuilder();
        measure(context, () -> android.os.SystemClock.sleep(100), success);
        checkMetrics(success);
        StringBuilder failure = new StringBuilder();
        IOException expected = new IOException("profile negative control");
        try {
            measure(context, () -> { android.os.SystemClock.sleep(100); throw expected; }, failure);
            throw new AssertionError("A failed request must remain failed");
        } catch (IOException observed) {
            if (observed != expected) throw new AssertionError("Original failure must be preserved");
        }
        checkMetrics(failure);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && "legacy-test-heap-sampler".equals(thread.getName()))
                throw new AssertionError("Both exits must stop the sampler");
        }
    }

    private static void checkMetrics(StringBuilder metrics) {
        String value = metrics.toString();
        if (!value.matches(".*sampledPeakHeapBytes=[1-9][0-9]*.*")
            || !value.matches(".*totalPssKiB=[1-9][0-9]*.*")
            || !value.matches(".*memoryClassMiB=[1-9][0-9]*.*"))
            throw new AssertionError("Success and failure must both retain native runtime metrics: " + value);
    }

    static void run(LegacyInstrumentation test) throws Exception {
        Context context = test.getTargetContext();
        HttpsProbe.install(new URL("https://api.github.com/repos/linzin86/LinzinDeskClock/releases/latest"));
        StringBuilder metrics = new StringBuilder();
        try {
            measure(context, () -> {
                context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_weather_enabled", true).putBoolean("pref_show_calendar_events", false)
                    .putString("pref_app_language", "en").putString("pref_layout_mode", "DUAL_LEFT_LARGE")
                    .putInt("pref_side_page", 1).putString("pref_last_valid_lat", "25.0")
                    .putString("pref_last_valid_lon", "121.0").commit();
                test.launch();
                Button[] refresh = new Button[1];
                test.runOnMainSync(() -> {
                    refresh[0] = test.activity.getWindow().getDecorView().findViewWithTag("weather_refresh");
                    if (refresh[0] == null) throw new AssertionError("Weather action must be visible");
                    if (refresh[0].isEnabled() && HttpsProbe.requests == 0) refresh[0].performClick();
                });
                boolean[] complete = new boolean[1];
                long deadline = SystemClock.elapsedRealtime() + 45000;
                do {
                    SystemClock.sleep(100);
                    test.runOnMainSync(() -> complete[0] = refresh[0].isEnabled() && HttpsProbe.requests > 0);
                } while (!complete[0] && SystemClock.elapsedRealtime() < deadline);
                if (!complete[0]) throw new AssertionError("Profile request must finish within its deadline");
                long observedBy = SystemClock.elapsedRealtime() + 1000;
                while (HttpsProbe.protocol == null && SystemClock.elapsedRealtime() < observedBy) SystemClock.sleep(10);
                if (HttpsProbe.requests != 1 || HttpsProbe.status != 200 || HttpsProbe.failure != null || !HttpsProbe.bodyComplete
                    || HttpsProbe.cleartext || !("TLSv1.2".equals(HttpsProbe.protocol) || "TLSv1.3".equals(HttpsProbe.protocol)))
                    throw new IOException("Expected one verified HTTPS control: requests=" + HttpsProbe.requests
                        + " status=" + HttpsProbe.status + " bodyComplete=" + HttpsProbe.bodyComplete
                        + " protocol=" + HttpsProbe.protocol, HttpsProbe.failure);
            }, metrics);
        } finally {
            Bundle receipt = new Bundle();
            receipt.putString("stream", "PROFILE: pid=" + android.os.Process.myPid() + " control=api.github.com HTTP="
                + HttpsProbe.status + " bodyComplete=" + HttpsProbe.bodyComplete + " protocol=" + HttpsProbe.protocol + metrics + "\n");
            test.sendStatus(0, receipt);
        }
    }
}
