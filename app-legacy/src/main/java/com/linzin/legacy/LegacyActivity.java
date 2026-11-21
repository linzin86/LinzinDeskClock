package com.linzin.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

public class LegacyActivity extends Activity {
    private String appliedLanguage;
    private LegacyClockScreen screen;
    private LegacyDisplay display;
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private Future<?> weatherTask;
    private volatile LegacyHttps client;
    private TextView weatherStatus;
    private double lastTemperatureC = Double.NaN;
    private long lastWeatherAt;
    private int weatherProblem;
    private boolean foreground;
    private int generation;
    private TextView calendarStatus;
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor();
    private Future<?> calendarTask;
    private int calendarGeneration;

    @Override protected void attachBaseContext(android.content.Context base) {
        appliedLanguage = LegacyLocales.tag(base);
        super.attachBaseContext(LegacyLocales.wrap(base));
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        LegacyLocales.configurationChanged(this, configuration);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new LegacyClockScreen(this, state != null && state.getBoolean("uiLocked"),
            this::refreshWeather, this::updateVisibleData);
        calendarStatus = screen.calendarStatus;
        weatherStatus = screen.weatherStatus;
        setContentView(screen);
        display = new LegacyDisplay(this, screen, () -> {
            screen.pause(); cancelWeather(); calendarGeneration++;
            if (calendarTask != null) calendarTask.cancel(true);
        }, () -> { screen.resume(); updateVisibleData(); }, state);
        if (state == null && !getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_dismiss_gesture_tutorial", false))
            LegacyTutorial.show(this);
    }

    @Override public void onUserInteraction() { super.onUserInteraction(); if (display != null) display.interact(); }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        return display != null && display.handleTouch(event) || super.dispatchTouchEvent(event);
    }

    private void refreshWeather() {
        if (!foreground || !screen.isWeatherVisible() || !weatherEnabled() || screen.isLocked() || weatherTask != null) return;
        final double latitude, longitude;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("desk_clock_prefs", MODE_PRIVATE);
            latitude = Double.parseDouble(prefs.getString("pref_last_valid_lat", ""));
            longitude = Double.parseDouble(prefs.getString("pref_last_valid_lon", ""));
            if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            weatherProblem = Double.isNaN(lastTemperatureC) ? R.string.weather_no_location : R.string.weather_unavailable;
            showWeather();
            return;
        }
        final int ticket = ++generation;
        screen.setWeatherBusy(true);
        weatherStatus.setText(R.string.weather_updating);
        weatherTask = weatherWorker.submit(() -> {
            Double incoming = null;
            boolean initializedTls = false;
            LegacyHttps request = null;
            try {
                request = new LegacyHttps(LegacyTls.factory(getApplicationContext()));
                initializedTls = true;
                client = request;
                String body = request.get(new URL("https://api.open-meteo.com/v1/forecast?latitude=" + latitude +
                    "&longitude=" + longitude + "&current=temperature_2m,weather_code&timezone=auto"));
                double temperature = new JSONObject(body).getJSONObject("current").getDouble("temperature_2m");
                if (Double.isNaN(temperature) || Double.isInfinite(temperature)) throw new IllegalArgumentException("Invalid temperature");
                incoming = temperature;
            } catch (Exception error) {
                if (!initializedTls) Log.w("LegacyClock", "TLS initialization failed", error);
                else Log.w("LegacyClock", "Weather refresh failed: " + error.getClass().getName());
            } finally {
                if (client == request) client = null;
                final Double result = incoming;
                final long fetchedAt = System.currentTimeMillis();
                ui.post(() -> {
                    if (!foreground || generation != ticket) return;
                    weatherTask = null;
                    if (result != null) { lastTemperatureC = result; lastWeatherAt = fetchedAt; }
                    weatherProblem = result == null ? R.string.weather_unavailable : 0;
                    showWeather();
                    screen.setWeatherBusy(false);
                });
            }
        });
    }

    private boolean weatherEnabled() { return getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_weather_enabled", false); }
    private void showWeather() {
        if (!weatherEnabled()) { weatherStatus.setText(R.string.weather_disabled_title); return; }
        if (weatherTask != null) { weatherStatus.setText(R.string.weather_updating); return; }
        String value = null;
        if (!Double.isNaN(lastTemperatureC)) {
            android.content.SharedPreferences prefs = getSharedPreferences("desk_clock_prefs", MODE_PRIVATE);
            boolean fahrenheit = prefs.getBoolean("pref_is_fahrenheit", false);
            String pattern = prefs.getBoolean("pref_24_hour", false) ? "M/d HH:mm" : "M/d hh:mm a";
            String time = new SimpleDateFormat(pattern, getResources().getConfiguration().locale).format(new Date(lastWeatherAt));
            value = getString(R.string.weather_value, fahrenheit ? lastTemperatureC * 9 / 5 + 32 : lastTemperatureC, fahrenheit ? "F" : "C", time);
        }
        weatherStatus.setText(value == null ? getString(weatherProblem == 0 ? R.string.weather_idle : weatherProblem)
            : value + (weatherProblem == 0 ? "" : "\n" + getString(weatherProblem)));
    }

    @Override protected void onResume() {
        super.onResume();
        if (!appliedLanguage.equals(LegacyLocales.tag(this))) { recreate(); return; }
        foreground = true;
        screen.reloadPreferences(); screen.resume();
        display.resume();
        updateVisibleData();
    }

    private void updateVisibleData() {
        if (!foreground) return;
        final int ticket = ++calendarGeneration;
        if (calendarTask != null) calendarTask.cancel(true);
        calendarTask = null;
        if (!screen.isWeatherVisible() || !weatherEnabled()) cancelWeather();
        showWeather();
        if (!screen.isCalendarVisible()) return;
        if (!getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_show_calendar_events", false)) {
            calendarStatus.setText(R.string.calendar_disabled);
            return;
        }
        calendarTask = calendarWorker.submit(() -> {
            String events = LegacyCalendar.read(this, System.currentTimeMillis());
            if (Thread.currentThread().isInterrupted()) return;
            ui.post(() -> {
                if (foreground && screen.isCalendarVisible() && calendarGeneration == ticket) calendarStatus.setText(events);
            });
        });
    }

    private void cancelWeather() {
        generation++;
        boolean wasRefreshing = weatherTask != null;
        if (weatherTask != null) weatherTask.cancel(true);
        weatherTask = null;
        LegacyHttps request = client;
        if (request != null) request.cancel();
        if (wasRefreshing) weatherProblem = 0;
        showWeather();
        screen.setWeatherBusy(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("uiLocked", screen.isLocked());
        display.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() {
        foreground = false;
        calendarGeneration++;
        if (calendarTask != null) calendarTask.cancel(true);
        cancelWeather();
        screen.pause();
        display.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        weatherWorker.shutdownNow();
        calendarWorker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
