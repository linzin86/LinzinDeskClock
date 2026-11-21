package com.linzin.legacy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.URL;
import java.util.Calendar;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

final class LegacySoakChecks {
    private static final long DURATION = 30 * 60000L;
    private static String nonce;

    private static void seed(Context context) {
        context.getSharedPreferences("desk_clock_prefs", 0).edit().clear()
            .putString("pref_app_language", "en").putBoolean("pref_24_hour", true)
            .putInt("pref_main_page", 1).putInt("pref_analog_style_idx", 1).putBoolean("pref_analog_show_sec", true)
            .putBoolean("pref_show_corner_date", true).putLong("pref_timeout_millis", -1)
            .putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", 1)
            .putBoolean("pref_weather_enabled", true).putBoolean("pref_show_calendar_events", false)
            .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
    }

    static void run(LegacyInstrumentation test) throws Exception {
        nonce = Process.myPid() + "-" + Long.toHexString(System.nanoTime());
        Context context = test.getTargetContext();
        File cache = context.getCacheDir();
        Map<String, ?> previous = context.getSharedPreferences("desk_clock_prefs", 0).getAll();
        long began = SystemClock.elapsedRealtime(), wallBegan = System.currentTimeMillis();
        JSONObject result = new JSONObject().put("nonce", nonce).put("pid", Process.myPid())
            .put("initial_stat", processStat()).put("clock_style", "OET")
            .put("memory_class_mib", ((android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass())
            .put("weather_transport", "controlled URLConnection fixture; not live HTTPS");
        try {
            seed(context); ForecastDataChecks.install(); test.launch();
            checkClock(test, test.activity);
            Activity activity = test.activity;
            ForecastDataChecks.run(test, activity);
            String initial = text(test, activity, "weather_status");
            int stops = test.clockStops;
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            test.runOnMainSync(() -> activity.startActivity(home));
            waitFor(() -> test.clockStops > stops && !keepsOn(test, activity), "Home must stop the clock and release its display flag");
            int resumes = test.clockResumes;
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            test.runOnMainSync(() -> test.getTargetContext().startActivity(launch));
            waitFor(() -> test.clockResumes > resumes && keepsOn(test, activity), "Launcher must resume the clock");
            result.put("home", true);

            phase(test, cache, "power-ready", began);
            test.runOnMainSync(() -> test.getTargetContext().startActivity(launch));
            waitFor(() -> keepsOn(test, activity) && activity.hasWindowFocus(), "Real screen wake must restore the clock");
            checkClock(test, activity);
            result.put("power", true);

            ForecastDataChecks.networkAvailable = false;
            refresh(test, activity);
            waitFor(() -> text(test, activity, "weather_status").contains("Weather update unavailable"), "Offline failure must be visible");
            String offline = text(test, activity, "weather_status");
            check(offline.contains(initial) && rows(test, activity) == 4, "Offline failure must retain the original weather and four forecasts");
            result.put("offline", offline);
            ForecastDataChecks.networkAvailable = true;
            ForecastDataChecks.temperatureC = 27;
            refresh(test, activity);
            waitFor(() -> text(test, activity, "weather_status").contains("27.0"), "Recovery must expose distinguishable new weather");
            String recovered = text(test, activity, "weather_status");
            check(!recovered.contains("unavailable") && rows(test, activity) == 4, "Recovery must clear the error and retain four forecasts");
            result.put("recovered", recovered);

            phase(test, cache, "date-before", began);
            waitFor(() -> date(test, activity, 2026, Calendar.DECEMBER, 31, "12/31"), "Clock must show December 31 before the boundary");
            result.put("date_before", readout(test, activity));
            phase(test, cache, "date-after", began);
            waitFor(() -> date(test, activity, 2027, Calendar.JANUARY, 1, "1/1"), "Clock must advance across the actual emulator date boundary");
            result.put("date_after", readout(test, activity));
            phase(test, cache, "date-restore", began);
            waitFor(() -> Math.abs(System.currentTimeMillis() - wallBegan - (SystemClock.elapsedRealtime() - began)) < 60000,
                "Controller must restore the original wall-clock timeline");

            int samples = 0;
            result.put("observing_started_ms", SystemClock.elapsedRealtime() - began);
            while (SystemClock.elapsedRealtime() - began < DURATION) {
                checkActiveClock(test, activity);
                Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
                long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                checkMemory(heap, memory.getTotalPss());
                write(cache, "linzin-soak-phase.json", new JSONObject().put("nonce", nonce).put("phase", "observing")
                    .put("elapsed_ms", SystemClock.elapsedRealtime() - began).put("pid", Process.myPid())
                    .put("pss_kib", memory.getTotalPss())
                    .put("heap_used_bytes", heap)
                    .put("readout", readout(test, activity)).toString());
                samples++;
                SystemClock.sleep(28000);
            }
            result.put("samples", samples).put("duration_ms", SystemClock.elapsedRealtime() - began)
                .put("final_stat", processStat()).put("status", "PASS");
        } catch (Throwable error) {
            result.put("status", "FAIL").put("error", error.toString());
            throw error;
        } finally {
            android.content.SharedPreferences.Editor restore = context.getSharedPreferences("desk_clock_prefs", 0).edit().clear();
            for (Map.Entry<String, ?> entry : previous.entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof java.util.Set) {
                    @SuppressWarnings("unchecked") java.util.Set<String> strings = (java.util.Set<String>) value;
                    restore.putStringSet(key, strings);
                } else throw new AssertionError("Unsupported preference value: " + key);
            }
            boolean restored = restore.commit();
            if (!restored) result.put("status", "FAIL").put("error", "Could not restore test preferences");
            write(cache, "linzin-soak-result.json", result.toString());
            check(restored, "Could not restore test preferences");
        }
    }

    private static void phase(LegacyInstrumentation test, File cache, String name, long began) throws Exception {
        File ack = new File(cache, "linzin-soak-ack.txt");
        if (ack.exists() && !ack.delete()) throw new AssertionError("Cannot discard previous controller ACK");
        String token = nonce + "-" + name;
        write(cache, "linzin-soak-phase.json", new JSONObject().put("nonce", nonce).put("phase", name)
            .put("pid", Process.myPid()).put("elapsed_ms", SystemClock.elapsedRealtime() - began).toString());
        android.os.Bundle state = new android.os.Bundle(); state.putString("phase", name); test.sendStatus(1, state);
        long end = SystemClock.elapsedRealtime() + 120000;
        while (SystemClock.elapsedRealtime() < end) {
            if (ack.exists()) {
                byte[] bytes = new byte[(int) ack.length()];
                try (java.io.FileInputStream input = new java.io.FileInputStream(ack)) {
                    check(input.read(bytes) == bytes.length, "Controller ACK must be complete");
                }
                if (token.equals(new String(bytes, "UTF-8").trim())) return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Controller did not verify " + name);
    }

    private static void write(File cache, String name, String text) throws Exception {
        try (OutputStreamWriter output = new OutputStreamWriter(new FileOutputStream(new File(cache, name)), "UTF-8")) { output.write(text); }
    }
    private static String processStat() throws Exception {
        try (java.io.BufferedReader input = new java.io.BufferedReader(new java.io.FileReader("/proc/self/stat"))) { return input.readLine(); }
    }
    private interface Condition { boolean met(); }
    private static void waitFor(Condition condition, String message) {
        long end = SystemClock.elapsedRealtime() + 45000;
        while (!condition.met() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        check(condition.met(), message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static String text(LegacyInstrumentation test, Activity activity, String tag) {
        String[] value = new String[1];
        test.runOnMainSync(() -> value[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag(tag)).getText().toString());
        return value[0];
    }
    private static String readout(LegacyInstrumentation test, Activity activity) {
        String[] value = new String[1];
        test.runOnMainSync(() -> value[0] = activity.getWindow().getDecorView().findViewWithTag("clock").getContentDescription().toString());
        return value[0];
    }
    private static boolean keepsOn(LegacyInstrumentation test, Activity activity) {
        boolean[] on = new boolean[1];
        test.runOnMainSync(() -> on[0] = (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        return on[0];
    }
    private static void refresh(LegacyInstrumentation test, Activity activity) {
        test.runOnMainSync(() -> ((Button) activity.getWindow().getDecorView().findViewWithTag("weather_refresh")).performClick());
    }
    private static int rows(LegacyInstrumentation test, Activity activity) { return text(test, activity, "weather_forecast").split("\n").length; }
    private static boolean date(LegacyInstrumentation test, Activity activity, int year, int month, int day, String label) {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month && now.get(Calendar.DAY_OF_MONTH) == day
            && readout(test, activity).contains(label);
    }
    private static void checkClock(LegacyInstrumentation test, Activity activity) {
        check("OET".equals(text(test, activity, "clock_style")), "Soak must exercise the OET analog dial cache");
        check(readout(test, activity).matches(".*[0-9]{2}:[0-9]{2}:[0-9]{2}.*"), "Soak must animate seconds");
    }
    private static void checkActiveClock(LegacyInstrumentation test, Activity activity) {
        checkClock(test, activity);
        check(!activity.isFinishing() && keepsOn(test, activity), "Foreground clock must remain active");
        String before = readout(test, activity);
        long tickDeadline = SystemClock.elapsedRealtime() + 5000;
        while (before.equals(readout(test, activity)) && SystemClock.elapsedRealtime() < tickDeadline) SystemClock.sleep(100);
        check(!before.equals(readout(test, activity)), "Real clock must keep advancing");
    }
    private static void checkMemory(long heap, int pss) {
        check(heap > 0 && heap <= 64L * 1024 * 1024, "Native heap sample exceeds 64 MiB budget");
        check(pss > 0 && pss <= 80 * 1024, "Native PSS sample exceeds 80 MiB budget");
    }
    static void fixtureChecks(LegacyInstrumentation test) throws Exception {
        ForecastDataChecks.install();
        ForecastDataChecks.networkAvailable = false;
        HttpsURLConnection offline = (HttpsURLConnection) new URL("https://api.open-meteo.com/v1/forecast").openConnection();
        try {
            offline.getResponseCode();
            throw new AssertionError("Offline weather fixture must fail instead of returning a success response");
        } catch (ConnectException expected) {
            // The fixture must exercise the same failure boundary as an unavailable connection.
        }
        try {
            offline.getInputStream();
            throw new AssertionError("Offline fixture input must fail too");
        } catch (ConnectException expected) {
            // Reading the body must not bypass the offline state.
        } finally { offline.disconnect(); }
        ForecastDataChecks.networkAvailable = true;
        ForecastDataChecks.temperatureC = 27;
        double actual = new JSONObject(ForecastDataChecks.body()).getJSONObject("current").getDouble("temperature_2m");
        if (actual != 27) throw new AssertionError("Reconnected fixture must supply distinguishable new weather: " + actual);
        seed(test.getTargetContext()); test.launch(); checkClock(test, test.activity);
        checkActiveClock(test, test.activity);
        test.runOnMainSync(() -> ((Button) test.activity.getWindow().getDecorView().findViewWithTag("clock_style")).performClick());
        boolean rejected = false;
        try { checkActiveClock(test, test.activity); }
        catch (AssertionError expected) { rejected = expected.getMessage().contains("OET"); }
        check(rejected, "Observation must reject a clock style changed after resume");
        checkMemory(64L * 1024 * 1024, 80 * 1024);
        long[][] invalid = {{0, 1}, {1, 0}, {64L * 1024 * 1024 + 1, 1}, {1, 80 * 1024 + 1}};
        for (long[] sample : invalid) {
            boolean denied = false;
            try { checkMemory(sample[0], (int) sample[1]); }
            catch (AssertionError expected) { denied = true; }
            check(denied, "Native sample must reject missing or over-budget memory");
        }
    }
}
