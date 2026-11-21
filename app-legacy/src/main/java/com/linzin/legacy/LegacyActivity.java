package com.linzin.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

public class LegacyActivity extends Activity {
    private LegacyClockScreen screen;
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private Future<?> weatherTask;
    private volatile LegacyHttps client;
    private TextView weatherStatus;
    private String lastWeather;
    private boolean foreground;
    private int generation;
    private TextView calendarStatus;
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor();
    private Future<?> calendarTask;
    private int calendarGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new LegacyClockScreen(this, state != null && state.getBoolean("uiLocked"),
            this::refreshWeather, this::updateVisibleData);
        calendarStatus = screen.calendarStatus;
        weatherStatus = screen.weatherStatus;
        setContentView(screen);
    }

    private void refreshWeather() {
        if (!foreground || !screen.isWeatherVisible() || screen.isLocked() || weatherTask != null) return;
        final double latitude, longitude;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("desk_clock_prefs", MODE_PRIVATE);
            latitude = Double.parseDouble(prefs.getString("pref_last_valid_lat", ""));
            longitude = Double.parseDouble(prefs.getString("pref_last_valid_lon", ""));
            if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            weatherStatus.setText(lastWeather == null ? getString(R.string.weather_no_location)
                : lastWeather + "\n" + getString(R.string.weather_unavailable));
            return;
        }
        final int ticket = ++generation;
        screen.setWeatherBusy(true);
        weatherStatus.setText(R.string.weather_updating);
        weatherTask = weatherWorker.submit(() -> {
            String incoming = null;
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
                String time = new SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(new Date());
                incoming = getString(R.string.weather_value, temperature, time);
            } catch (Exception error) {
                if (!initializedTls) Log.w("LegacyClock", "TLS initialization failed", error);
                else Log.w("LegacyClock", "Weather refresh failed: " + error.getClass().getName());
            } finally {
                if (client == request) client = null;
                final String result = incoming;
                ui.post(() -> {
                    if (!foreground || generation != ticket) return;
                    weatherTask = null;
                    if (result != null) lastWeather = result;
                    weatherStatus.setText(result != null ? result : lastWeather == null ? getString(R.string.weather_unavailable)
                        : lastWeather + "\n" + getString(R.string.weather_unavailable));
                    screen.setWeatherBusy(false);
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen.resume();
        updateVisibleData();
    }

    private void updateVisibleData() {
        if (!foreground) return;
        final int ticket = ++calendarGeneration;
        if (calendarTask != null) calendarTask.cancel(true);
        calendarTask = null;
        if (!screen.isWeatherVisible()) cancelWeather();
        if (!screen.isCalendarVisible()) return;
        if (!getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_show_calendar_events", false)) {
            calendarStatus.setText(R.string.calendar_disabled);
            return;
        }
        calendarTask = calendarWorker.submit(() -> {
            String events = LegacyCalendar.read(getApplicationContext(), System.currentTimeMillis());
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
        if (wasRefreshing) weatherStatus.setText(lastWeather == null ? getString(R.string.weather_idle) : lastWeather);
        screen.setWeatherBusy(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("uiLocked", screen.isLocked());
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() {
        foreground = false;
        calendarGeneration++;
        if (calendarTask != null) calendarTask.cancel(true);
        cancelWeather();
        screen.pause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override protected void onDestroy() {
        weatherWorker.shutdownNow();
        calendarWorker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
