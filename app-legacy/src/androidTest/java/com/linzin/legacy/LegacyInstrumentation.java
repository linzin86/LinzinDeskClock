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
    Activity activity;
    private String mode;
    volatile int clockResumes, clockStops;
    private volatile Activity resumedActivity;
    @Override public void callActivityOnResume(Activity target) {
        super.callActivityOnResume(target);
        resumedActivity = target;
        if (target == activity) clockResumes++;
    }
    @Override public void callActivityOnStop(Activity target) {
        super.callActivityOnStop(target);
        if (target == activity) clockStops++;
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "" : arguments.getString("mode", "");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            if (mode.startsWith("hardwareSoak")) {
                HardwareSoakChecks.run(this, mode); result.putString("stream", "OK: " + mode);
            } else if (mode.startsWith("frameSetup")) {
                FrameSetupChecks.run(this, mode.substring(10)); result.putString("stream", "OK: " + mode);
            } else if ("analogRendering".equals(mode)) {
                AnalogRenderingChecks.run(getTargetContext());
                result.putString("stream", "OK: fresh/reused analog rendering across sizes, styles and seconds");
            } else if ("tlsProfileChecks".equals(mode) || "tlsProfile".equals(mode)) {
                if ("tlsProfileChecks".equals(mode)) TlsProfileChecks.check(getTargetContext());
                else TlsProfileChecks.run(this);
                result.putString("stream", "OK: " + mode);
            } else if ("soakFixtureChecks".equals(mode)) {
                LegacySoakChecks.fixtureChecks(this); result.putString("stream", "OK: analog clock and controlled offline/replacement weather fixture");
            } else if ("soakControlled".equals(mode)) {
                LegacySoakChecks.run(this);
                result.putString("stream", "OK: real thirty-minute runtime with Home/power and controlled network/date boundaries");
            } else if ("probeChecks".equals(mode)) {
                HttpsProbeChecks.run(); result.putString("stream", "OK: per-request TLS outcome and endpoint isolation controls");
            } else if ("primeOldGps".equals(mode)) {
                LocatorUiChecks.primeOld(this); result.putString("stream", "OK: dated GPS cache fixture and listener removal");
            } else if ("updateBrowser".equals(mode)) {
                UpdateChecks.browser(getTargetContext()); result.putString("stream", "OK: unavailable browser retains safe copy fallback");
            } else if ("update".equals(mode) || "updateInvalid".equals(mode) || "updateCancel".equals(mode) || "updateLive".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putString("pref_app_language", "en").commit();
                if (!"updateLive".equals(mode)) UpdateChecks.install(mode);
                launch(); UpdateChecks.run(this, activity, "updateLive".equals(mode));
                result.putString("stream", "OK: " + mode + ": secure query, Legacy compatibility and copyable safe release link");
            } else if ("calendarRange".equals(mode) || "calendarContent".equals(mode)) {
                CalendarPeriodChecks.run(getTargetContext(), "calendarRange".equals(mode));
                result.putString("stream", "OK: " + mode);
            } else if (mode.startsWith("geocoding")) {
                GeocodingChecks.run(mode, getTargetContext());
                result.putString("stream", "OK: " + mode);
            } else if ("locationPermission".equals(mode) || "locationCoarse".equals(mode)) {
                check(android.os.Build.VERSION.SDK_INT >= 23, "Location runtime permission case requires API23+");
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_weather_enabled", true).putString("pref_app_language", "en")
                    .putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", 1)
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                ForecastDataChecks.install(); launch();
                if ("locationCoarse".equals(mode)) LocationPermissionChecks.coarse(this, activity);
                else LocationPermissionChecks.run(this, activity);
                result.putString("stream", "OK: " + mode + ": permission flow and saved-location weather");
            } else if ("weatherLocate".equals(mode) || "weatherLocateActive".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_weather_enabled", true).putString("pref_app_language", "en")
                    .putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", 1).commit();
                ForecastDataChecks.install(); launch(); LocatorUiChecks.run(this, activity, "weatherLocateActive".equals(mode));
                result.putString("stream", "OK: real emulator location updates, weather and saved fix metadata");
            } else if (mode.startsWith("locator")) {
                LocatorChecks.run(mode);
                result.putString("stream", "OK: " + mode);
            } else if ("weatherDisplay".equals(mode)) {
                launch(); WeatherDisplayChecks.run(this, activity);
                result.putString("stream", "OK: four locales at fontScale 2, wrapping, scroll and reachable location action");
            } else if ("weatherParserChecks".equals(mode)) {
                WeatherParserChecks.run(getTargetContext());
                result.putString("stream", "OK: forecast parsing, finite values, overflow, array alignment, units and bundled Kyiv zone");
            } else if ("weatherForecast".equals(mode) || "weatherMetadata".equals(mode) || "weatherCity".equals(mode)
                || "weatherState".equals(mode) || "weatherAutoRefresh".equals(mode)
                || "weatherGeoDeadline".equals(mode) || "weatherTotalDeadline".equals(mode) || "weatherRollback".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_weather_enabled", true).putBoolean("pref_24_hour", true)
                    .putString("pref_app_language", "en").putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", 1)
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                if ("weatherMetadata".equals(mode) || "weatherState".equals(mode)) getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                    .putString("pref_last_location_fix_time", Long.toString(System.currentTimeMillis() - 5400000))
                    .putString("pref_last_location_observed_at", Long.toString(System.currentTimeMillis()))
                    .putString("pref_last_location_provider", "gps").commit();
                boolean deadlineCase = "weatherGeoDeadline".equals(mode) || "weatherTotalDeadline".equals(mode) || "weatherRollback".equals(mode);
                if (deadlineCase) WeatherDeadlineChecks.install("weatherTotalDeadline".equals(mode)); else ForecastDataChecks.install();
                launch();
                if ("weatherRollback".equals(mode)) activity = WeatherDeadlineChecks.rollback(this, activity);
                else if (deadlineCase) WeatherDeadlineChecks.run(this, activity, "weatherTotalDeadline".equals(mode));
                else if ("weatherState".equals(mode)) activity = WeatherStateChecks.run(this, activity);
                else if ("weatherAutoRefresh".equals(mode)) WeatherRefreshChecks.run(this, activity);
                else ForecastDataChecks.run(this, activity);
                if ("weatherMetadata".equals(mode)) ForecastDataChecks.metadata(this, activity);
                if ("weatherCity".equals(mode)) ForecastDataChecks.city(this, activity);
                result.putString("stream", "OK: " + mode);
            } else if ("flipLanguage".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); activity = WindowSettingsChecks.flipAcrossLanguage(this, activity);
                result.putString("stream", "OK: disabling flip after language recreation restores automatic orientation");
            } else if ("reverseRotation".equals(mode) || "reversePortrait".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); WindowSettingsChecks.reverseRotation(this, activity, "reversePortrait".equals(mode));
                result.putString("stream", "OK: flip preserves a reverse display pose");
            } else if ("runtimePermission".equals(mode)) {
                check(android.os.Build.VERSION.SDK_INT >= 23, "Runtime permission case requires API23+");
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); RuntimePermissionChecks.run(this, activity);
                result.putString("stream", "OK: calendar permission denial and grant through system UI");
            } else if ("brightnessSettings".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); BrightnessSettingsChecks.run(this, activity);
                result.putString("stream", "OK: brightness cancel/confirm, real rotation and clock application");
            } else if ("languageSettings".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); activity = LanguageSettingsChecks.run(this, activity);
                result.putString("stream", "OK: language picker recreates settings and clock without changing the global locale");
            } else if ("tutorial".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(false); TutorialChecks.first(this, activity);
                runOnMainSync(() -> activity.finish()); waitForIdleSync();
                launch(false); TutorialChecks.reopen(this, activity);
                result.putString("stream", "OK: first-run guide, persistent dismissal and settings reopen");
            } else if ("weatherSettings".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putString("pref_app_language", "en").putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", 1)
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                WeatherSettingsChecks.install(); launch(); WeatherSettingsChecks.run(this, activity);
                result.putString("stream", "OK: weather opt-in and cached Celsius/Fahrenheit settings");
            } else if ("battery".equals(mode) || "month".equals(mode) || "batteryChecks".equals(mode) || "monthChecks".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_show_battery", true).putString("pref_layout_mode", mode.startsWith("month") ? "DUAL_LEFT_LARGE" : "SINGLE")
                    .putBoolean("pref_show_calendar_events", false).commit();
                launch();
                if ("batteryChecks".equals(mode)) BatteryChecks.run(this, activity);
                else if ("monthChecks".equals(mode)) MonthChecks.run(this, activity);
                else if ("battery".equals(mode)) ContentWidgetsChecks.battery(this, activity);
                else ContentWidgetsChecks.month(this, activity);
                result.putString("stream", "OK: " + mode);
            } else if ("windowSettings".equals(mode) || "timeoutSettings".equals(mode) || "longTimeoutSettings".equals(mode)) {
                long systemTimeout = android.provider.Settings.System.getInt(getTargetContext().getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 15000);
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_manual_brightness", true).putFloat("pref_brightness_val", .25f)
                    .putBoolean("pref_flip_screen_180", "windowSettings".equals(mode))
                    .putLong("pref_timeout_millis", "windowSettings".equals(mode) ? -1 : "longTimeoutSettings".equals(mode) ? systemTimeout + 3000 : 500).commit();
                launch();
                if ("windowSettings".equals(mode)) WindowSettingsChecks.window(this, activity);
                else if ("longTimeoutSettings".equals(mode)) WindowSettingsChecks.longTimeout(this, activity);
                else WindowSettingsChecks.timeout(this, activity);
                result.putString("stream", "OK: " + mode);
            } else if ("localeJa".equals(mode) || "localeTw".equals(mode) || "localeCn".equals(mode) || "localeEn".equals(mode)
                || "localeSystem".equals(mode)) {
                String language = "localeJa".equals(mode) ? "ja" : "localeTw".equals(mode) ? "zh-TW"
                    : "localeCn".equals(mode) ? "zh-CN" : "localeEn".equals(mode) ? "en" : "system";
                Locale processLocale = Locale.getDefault();
                Locale applicationLocale = getTargetContext().getResources().getConfiguration().locale;
                int titleId = getTargetContext().getResources().getIdentifier("settings_title", "string", getTargetContext().getPackageName());
                String applicationTitle = getTargetContext().getString(titleId);
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putString("pref_app_language", language).commit();
                launch(); LocaleChecks.run(this, activity, language, processLocale, applicationLocale, applicationTitle);
                result.putString("stream", "OK: isolated Activity locale " + language + " survives real rotation");
            } else if ("settings".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); SettingsChecks.run(this, activity);
                result.putString("stream", "OK: native settings entry and existing preference schema");
            } else if ("secondsControls".equals(mode) || "dateReadout".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch();
                if ("secondsControls".equals(mode)) AccessibilityChecks.seconds(this, activity);
                else AccessibilityChecks.date(this, activity);
                result.putString("stream", "OK: " + mode);
            } else if ("twoFingerOrder".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); TwoFingerChecks.run(this, activity);
                result.putString("stream", "OK: both pointer orders and moving-pointer rejection");
            } else if ("directTls12".equals(mode) || "directTls13".equals(mode)) {
                result.putString("stream", DirectTlsChecks.run(getTargetContext(), "directTls12".equals(mode) ? "TLSv1.2" : "TLSv1.3"));
            } else if ("stylesExternal".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_24_hour", true).putInt("pref_main_page", 0)
                    .putBoolean("pref_digital_show_sec", false).putBoolean("pref_analog_show_sec", false).putBoolean("pref_flip_show_sec", false).commit();
                launch(); ExternalStylesChecks.run(this, activity);
                result.putString("stream", "OK: all eight public styles draw distinctly with wall time");
            } else if ("rotation".equals(mode)) {
                launch(); RotationChecks.run(this, activity);
                result.putString("stream", "OK: real landscape/portrait on installed candidate");
            } else if ("displayChecks".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear().commit();
                launch(); DisplayChecks.run(this, activity);
                result.putString("stream", "OK: real landscape/portrait and fontScale2 native layout/48dp fixture");
            } else if ("paneCancellation".equals(mode)) {
                selectSidebar(1);
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                try (PaneCancellationChecks fixture = new PaneCancellationChecks()) {
                    HttpsProbe.install(fixture.endpoint); launch(); fixture.run(this, activity);
                }
                result.putString("stream", "OK: hidden weather cancels transport, clears updating state and rejects late results");
            } else if ("gestures".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putInt("pref_main_page", 0).putInt("pref_digital_style_idx", 0).putBoolean("pref_digital_show_sec", false).commit();
                launch();
                GestureChecks.run(this, activity);
                result.putString("stream", "OK: style/seconds/family/sidebar gestures and lock/unlock reachability");
            } else if ("layouts".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putString("pref_layout_mode", "SINGLE").putInt("pref_side_page", 0).commit();
                launch();
                LayoutChecks.run(this, activity);
                result.putString("stream", "OK: single/dual mirrored 70/30 panes, sidebar switching and touch lock");
            } else if ("clockStyles".equals(mode)) {
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                    .putBoolean("pref_24_hour", true).putInt("pref_main_page", 0).commit();
                launch();
                ClockStylesChecks.run(this, activity);
                runOnMainSync(() -> activity.finish());
                waitForIdleSync();
                launch();
                final String[] label = new String[1];
                runOnMainSync(() -> label[0] = ((Button) activity.getWindow().getDecorView().findViewWithTag("clock_style")).getText().toString());
                check(label[0].contains("FFT"), "Selected style must survive reopening");
                result.putString("stream", "OK: all eight styles selectable and last selection restored");
            } else if ("artworkChecks".equals(mode)) {
                ArtworkChecks.run(getTargetContext());
                result.putString("stream", "OK: all 96 original clock assets available with pre21 bitmap fallback");
            } else if ("frozenChainStress".equals(mode)) {
                CertificateChecks.stress(getTargetContext(), getContext());
                result.putString("stream", "OK: frozen public chain verified 100 times at its capture date");
            } else if ("certificateChecks".equals(mode)) {
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
            } else if ("calendarOff".equals(mode)) {
                selectSidebar(0);
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("pref_show_calendar_events", false).commit();
                launch();
                String[] text = new String[1];
                long deadline = SystemClock.elapsedRealtime() + 5000;
                do {
                    SystemClock.sleep(100);
                    runOnMainSync(() -> text[0] = ((TextView) activity.getWindow().getDecorView()
                        .findViewWithTag("calendar_status")).getText().toString());
                } while (!text[0].contains("events are off") && !text[0].contains("Linzin Legacy fixture") && SystemClock.elapsedRealtime() < deadline);
                check(text[0].contains("events are off"), "Disabled events must not expose provider data; got: " + text[0]);
                result.putString("stream", "OK: disabled calendar events show an explicit off state");
            } else if ("calendarDenied".equals(mode)) {
                selectSidebar(0);
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
                selectSidebar(0);
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
                selectSidebar(1);
                HttpsProbe.install(new URL("https://api.open-meteo.com/v1/forecast?latitude=25.0&longitude=121.0&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto&timeformat=unixtime"));
                getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
                    .putString("pref_last_valid_lat", "25.0").putString("pref_last_valid_lon", "121.0").commit();
                launch();
                try (RuntimeSamples samples = new RuntimeSamples()) {
                    String status = refreshAndWait();
                    if (!status.contains("°")) {
                        throw new RuntimeException("Network must return a temperature; got: " + status + " factoryHost=" + HttpsProbe.socketHost
                            + samples.snapshot(getTargetContext()), HttpsProbe.failure);
                    }
                    check(!HttpsProbe.cleartext, "No request may use HTTP");
                    result.putString("stream", "OK: real HTTPS weather response parsed on target API; " + HttpsProbe.protocol
                        + samples.snapshot(getTargetContext()));
                }
            } else if ("saved".equals(mode)) {
                launch();
                check(!getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
                    .getBoolean("pref_24_hour", true), "Process restart must preserve the saved setting");
                String[] restored = new String[1];
                runOnMainSync(() -> restored[0] = ((Button) activity.getWindow().getDecorView().findViewWithTag("time_mode")).getText().toString());
                check(restored[0].contains("12"), "Restored UI must use 12-hour time");
                result.putString("stream", "OK: saved setting restored after process restart");
            } else {
            check(mode.isEmpty() || "clock".equals(mode), "Unknown test mode: " + mode);
            getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("pref_24_hour", true).putInt("pref_main_page", 1).commit();
            launch();
            final View[] clock = new View[1];
            runOnMainSync(() -> clock[0] = activity.getWindow().getDecorView().findViewWithTag("clock"));
            check(clock[0] != null, "Native clock must be visible");
            final String[] before = new String[1];
            runOnMainSync(() -> before[0] = clock[0].getContentDescription().toString());
            SystemClock.sleep(1300);
            String[] afterTick = new String[1];
            runOnMainSync(() -> afterTick[0] = clock[0].getContentDescription().toString());
            check(!before[0].equals(afterTick[0]), "Clock must advance using wall time");
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
    void launch() { launch(true); }
    private void launch(boolean hideGuide) {
        if (hideGuide) getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("pref_dismiss_gesture_tutorial", true).commit();
        Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
        Activity previous = resumedActivity;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        runOnMainSync(() -> getTargetContext().startActivity(launch));
        activity = awaitResumed(launch.getComponent().getClassName(), previous);
        waitForIdleSync();
    }
    Activity openSettings(Activity clock) {
        Activity ready = awaitResumed(clock.getComponentName().getClassName(), null);
        Button open = ready.getWindow().getDecorView().findViewWithTag("settings");
        runOnMainSync(() -> open.performClick());
        return awaitResumed("com.linzin.legacy.LegacySettingsActivity", ready);
    }
    Activity awaitResumed(String className, Activity previous) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        Activity[] seen = new Activity[1];
        boolean[] ready = new boolean[1];
        do {
            runOnMainSync(() -> {
                Activity current = resumedActivity; seen[0] = current;
                ready[0] = current != null && current != previous && current.getComponentName().getClassName().equals(className)
                    && !current.isFinishing() && current.getWindow().getDecorView().isShown()
                    && (current.hasWindowFocus() || visibleDialog(current));
                if (ready[0] && current instanceof android.preference.PreferenceActivity)
                    ready[0] = ((android.preference.PreferenceActivity) current).getListView().getAdapter() != null;
            });
            if (ready[0]) return seen[0];
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("No visible resumed Activity: " + className + "; last=" + seen[0]);
    }
    private static boolean visibleDialog(Activity activity) {
        for (String tag : new String[] { "gesture_guide", "brightness" }) {
            android.app.Fragment fragment = activity.getFragmentManager().findFragmentByTag(tag);
            if (fragment instanceof android.app.DialogFragment) {
                android.app.Dialog dialog = ((android.app.DialogFragment) fragment).getDialog();
                if (dialog != null && dialog.isShowing()) return true;
            }
        }
        return false;
    }
    private void selectSidebar(int page) {
        getTargetContext().getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("pref_weather_enabled", page == 1)
            .putLong("pref_timeout_millis", -1)
            .putString("pref_layout_mode", "DUAL_LEFT_LARGE").putInt("pref_side_page", page)
            .putBoolean("pref_show_calendar_events", true).commit();
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
        selectSidebar(1);
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
                + " failure=" + HttpsProbe.failure + "\n" + HttpsProbe.outcomes);
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
