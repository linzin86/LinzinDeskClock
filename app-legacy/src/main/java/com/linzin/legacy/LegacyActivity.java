package com.linzin.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private LegacyClockView clock;
    private boolean is24Hour;
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private Future<?> weatherTask;
    private volatile LegacyHttps client;
    private Button weatherRefresh;
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
        is24Hour = getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_24_hour", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.BLACK);
        clock = new LegacyClockView(this, is24Hour);
        root.addView(clock, new LinearLayout.LayoutParams(-1, 0, 1));
        Button timeMode = new Button(this);
        timeMode.setTag("time_mode");
        timeMode.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        timeMode.setText(is24Hour ? R.string.time_mode_24 : R.string.time_mode_12);
        timeMode.setOnClickListener(view -> {
            is24Hour = !is24Hour;
            getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).edit().putBoolean("pref_24_hour", is24Hour).apply();
            clock.set24Hour(is24Hour);
            timeMode.setText(is24Hour ? R.string.time_mode_24 : R.string.time_mode_12);
        });
        root.addView(timeMode, new LinearLayout.LayoutParams(-1, -2));
        calendarStatus = new TextView(this);
        calendarStatus.setTag("calendar_status");
        calendarStatus.setTextColor(android.graphics.Color.WHITE);
        root.addView(calendarStatus, new LinearLayout.LayoutParams(-1, -2));
        weatherStatus = new TextView(this);
        weatherStatus.setTag("weather_status");
        weatherStatus.setTextColor(android.graphics.Color.WHITE);
        weatherStatus.setText(R.string.weather_idle);
        root.addView(weatherStatus, new LinearLayout.LayoutParams(-1, -2));
        weatherRefresh = new Button(this);
        weatherRefresh.setTag("weather_refresh");
        weatherRefresh.setText(R.string.weather_refresh);
        weatherRefresh.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        weatherRefresh.setOnClickListener(view -> refreshWeather());
        root.addView(weatherRefresh, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void refreshWeather() {
        if (!foreground || weatherTask != null) return;
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
        weatherRefresh.setEnabled(false);
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
                    weatherRefresh.setEnabled(true);
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clock.start();
        final int ticket = ++calendarGeneration;
        calendarTask = calendarWorker.submit(() -> {
            String events = LegacyCalendar.read(getApplicationContext(), System.currentTimeMillis());
            if (Thread.currentThread().isInterrupted()) return;
            ui.post(() -> {
                if (foreground && calendarGeneration == ticket) calendarStatus.setText(events);
            });
        });
    }

    @Override protected void onPause() {
        foreground = false;
        generation++;
        calendarGeneration++;
        if (calendarTask != null) calendarTask.cancel(true);
        if (weatherTask != null) weatherTask.cancel(true);
        weatherTask = null;
        LegacyHttps request = client;
        if (request != null) request.cancel();
        weatherRefresh.setEnabled(true);
        clock.stop();
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
