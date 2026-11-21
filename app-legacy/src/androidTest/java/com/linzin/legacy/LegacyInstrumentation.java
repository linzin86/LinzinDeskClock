package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Locale;

public class LegacyInstrumentation extends Instrumentation {
    private Activity activity;
    private String mode;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "" : arguments.getString("mode", "");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            if ("certificateChecks".equals(mode)) {
                CertificateChecks.run(getTargetContext(), getContext());
                result.putString("stream", "OK: 118 root/chain keys decoded and frozen-date PKIX validated five times");
            } else if ("socketChecks".equals(mode)) {
                SocketChecks.run(getTargetContext());
                result.putString("stream", "OK: real silent TLS peer deadline and cancellation close the transport");
            } else if ("profile".equals(mode)) {
                launch();
                android.app.ActivityManager manager = (android.app.ActivityManager) getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
                Runtime heap = Runtime.getRuntime();
                android.util.DisplayMetrics display = getTargetContext().getResources().getDisplayMetrics();
                int brightness = android.provider.Settings.System.getInt(getTargetContext().getContentResolver(), "screen_brightness", -1);
                Intent battery = getTargetContext().registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                result.putString("stream", "OK: memoryClassMiB=" + manager.getMemoryClass() + " heapUsed="
                    + (heap.totalMemory() - heap.freeMemory()) + " heapCommitted=" + heap.totalMemory() + " heapMax=" + heap.maxMemory()
                    + " display=" + display.widthPixels + "x" + display.heightPixels + " density=" + display.density
                    + " brightness=" + brightness + " plugged=" + (battery == null ? -1 : battery.getIntExtra("plugged", -1))
                    + " batteryTemperatureTenthsC=" + (battery == null ? -1 : battery.getIntExtra("temperature", -1)));
            } else if ("calendarDenied".equals(mode)) {
                launch();
                final String[] text = new String[1];
                long deadline = SystemClock.elapsedRealtime() + 10000;
                do {
                    SystemClock.sleep(100);
                    runOnMainSync(() -> text[0] = ((TextView) activity.getWindow().getDecorView()
                        .findViewWithTag("calendar_status")).getText().toString());
                } while (!text[0].contains("permission") && SystemClock.elapsedRealtime() < deadline);
                check(text[0].contains("permission"), "Denied runtime calendar permission must be explicit: " + text[0]);
                result.putString("stream", "OK: denied runtime calendar permission shown safely");
            } else if ("calendarChecks".equals(mode)) {
                CalendarChecks.run(getTargetContext());
                result.putString("stream", "OK: calendar permission, parsing, cursor cleanup and late cancellation");
            } else if ("calendar".equals(mode)) {
                launch();
                final TextView[] calendar = new TextView[1];
                runOnMainSync(() -> calendar[0] = activity.getWindow().getDecorView().findViewWithTag("calendar_status"));
                check(calendar[0] != null, "Upcoming calendar must be visible");
                long deadline = SystemClock.elapsedRealtime() + 10000;
                final String[] text = new String[1];
                do {
                    SystemClock.sleep(100);
                    runOnMainSync(() -> text[0] = calendar[0].getText().toString());
                } while (!text[0].contains("Linzin Legacy fixture") && SystemClock.elapsedRealtime() < deadline);
                check(text[0].contains("Linzin Legacy fixture"), "Real provider event must appear: " + text[0]);
                result.putString("stream", "OK: real CalendarProvider event displayed");
            } else if ("tls".equals(mode)) {
                tlsMatrix(result);
            } else if ("network".equals(mode)) {
                HttpsProbe.install(new URL("https://api.open-meteo.com/v1/forecast?latitude=25.0&longitude=121.0&current=temperature_2m,weather_code&timezone=auto"));
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                launch();
                try (RuntimeSamples samples = new RuntimeSamples()) {
                    String status = refreshAndWait();
                    if (!status.contains("°")) {
                        throw new RuntimeException("Network must return a temperature; got: " + status, HttpsProbe.failure);
                    }
                    check(!HttpsProbe.cleartext, "No request may use HTTP");
                    result.putString("stream", "OK: real HTTPS weather response parsed on target API; " + HttpsProbe.protocol
                        + samples.snapshot(getTargetContext()));
                }
            } else if ("saved".equals(mode)) {
                launch();
                check(!getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
                    .getBoolean("pref_24_hour", true), "Process restart must preserve the saved setting");
                runOnMainSync(() -> check(((Button) activity.getWindow().getDecorView().findViewWithTag("time_mode"))
                    .getText().toString().contains("12"), "Restored UI must use 12-hour time"));
                result.putString("stream", "OK: saved setting restored after process restart");
            } else {
            getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("pref_24_hour", true).commit();
            launch();
            final View[] clock = new View[1];
            runOnMainSync(() -> clock[0] = activity.getWindow().getDecorView().findViewWithTag("clock"));
            check(clock[0] != null, "Native clock must be visible");
            final String[] before = new String[1];
            runOnMainSync(() -> before[0] = clock[0].getContentDescription().toString());
            SystemClock.sleep(1300);
            runOnMainSync(() -> check(!before[0].equals(clock[0].getContentDescription().toString()), "Clock must advance using wall time"));
            runOnMainSync(() -> ((Button) activity.getWindow().getDecorView().findViewWithTag("time_mode")).performClick());
            check(!getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
                .getBoolean("pref_24_hour", true), "Time setting must be saved");
            runOnMainSync(() -> activity.finish());
            waitForIdleSync();
            launch();
            final Button[] mode = new Button[1];
            runOnMainSync(() -> mode[0] = activity.getWindow().getDecorView().findViewWithTag("time_mode"));
            check(mode[0].getText().toString().contains("12"), "Reopened clock must restore its setting");
            result.putString("stream", "OK: native clock advances and settings survive reopening");
            }
        } catch (Throwable error) {
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            result.putString("stream", "FAIL: " + trace); code = Activity.RESULT_CANCELED;
        } finally {
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(code, result);
    }
    private void launch() {
        Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
        activity = startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }
    private String refreshAndWait() {
        int requestsBefore = HttpsProbe.requests;
        final Button[] refresh = new Button[1];
        runOnMainSync(() -> refresh[0] = activity.getWindow().getDecorView().findViewWithTag("weather_refresh"));
        check(refresh[0] != null, "Weather refresh must be available");
        runOnMainSync(() -> refresh[0].performClick());
        // Observe provider initialization plus the independently tested 30-second transport deadline.
        long deadline = SystemClock.elapsedRealtime() + 45000;
        final String[] status = new String[1];
        final boolean[] complete = new boolean[1];
        do {
            SystemClock.sleep(100);
            runOnMainSync(() -> {
                complete[0] = refresh[0].isEnabled();
                status[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString();
            });
        } while (!complete[0] && SystemClock.elapsedRealtime() < deadline);
        if (!complete[0]) {
            StringBuilder trace = new StringBuilder();
            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                if (!entry.getKey().getName().startsWith("pool-")) continue;
                trace.append('\n').append(entry.getKey().getName()).append(' ').append(entry.getKey().getState());
                for (StackTraceElement frame : entry.getValue()) trace.append("\n  ").append(frame);
            }
            throw new AssertionError("Weather request did not finish within its deadline; requests=" + HttpsProbe.requests + trace);
        }
        check(HttpsProbe.requests > requestsBefore, "Enabled refresh ignored: no new connection; status=" + status[0]);
        return status[0];
    }
    private void tlsMatrix(Bundle result) throws Exception {
        URL[] endpoints = new URL[] {
            new URL("https://badssl.com/"),
            new URL("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=25&longitude=121&localityLanguage=en"),
            new URL("https://api.github.com/repos/linzin86/LinzinDeskClock/releases/latest"),
            new URL("https://self-signed.badssl.com/"),
            new URL("https://wrong.host.badssl.com/")
        };
        HttpsProbe.install(endpoints[0]);
        getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
            .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
        launch();
        StringBuilder receipt = new StringBuilder();
        for (int i = 0; i < endpoints.length; i++) {
            HttpsProbe.begin(endpoints[i]);
            String status = refreshAndWait();
            Bundle progress = new Bundle();
            progress.putString("stream", "TLS case " + endpoints[i].getHost() + " requests=" + HttpsProbe.requests
                + " status=" + HttpsProbe.status + " protocol=" + HttpsProbe.protocol + " peer=" + HttpsProbe.peer
                + " failure=" + HttpsProbe.failure + "\n");
            sendStatus(0, progress);
            check(!HttpsProbe.cleartext, "TLS error must never trigger cleartext");
            if (i < 3) {
                if (HttpsProbe.failure != null) throw new RuntimeException("Positive TLS control failed: " + endpoints[i].getHost(), HttpsProbe.failure);
                check(HttpsProbe.status >= 200 && HttpsProbe.status < 500, "No HTTPS response from positive endpoint");
                long listenerDeadline = SystemClock.elapsedRealtime() + 1000;
                while (HttpsProbe.protocol == null && SystemClock.elapsedRealtime() < listenerDeadline) SystemClock.sleep(10);
                check("TLSv1.2".equals(HttpsProbe.protocol) || "TLSv1.3".equals(HttpsProbe.protocol), "Modern TLS must negotiate");
                if (i == 0) check(HttpsProbe.status == 200, "badssl.com positive certificate control must succeed");
                receipt.append(endpoints[i].getHost()).append(' ').append(HttpsProbe.protocol).append(" HTTP ").append(HttpsProbe.status).append('\n');
            } else {
                check(HttpsProbe.failure != null && HttpsProbe.status == 0, "Invalid peer must be rejected before HTTP");
                StringBuilder causes = new StringBuilder();
                for (Throwable error = HttpsProbe.failure; error != null; error = error.getCause()) causes.append(error.getClass().getName()).append(' ').append(error.getMessage()).append('\n');
                String description = causes.toString().toLowerCase(Locale.US);
                check(i == 3 ? description.contains("cert") || description.contains("trust")
                    : description.contains("hostname") || description.contains("subject alternative"), "Wrong failure boundary: " + causes);
                check(status.contains("unavailable"), "Rejected TLS must leave an unavailable update");
                receipt.append(endpoints[i].getHost()).append(" rejected: ").append(causes).append('\n');
            }
        }
        result.putString("stream", "OK: TLS positive/negative matrix\n" + receipt);
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
