package com.linzin.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LegacyActivity extends Activity {
    private String appliedLanguage;
    private LegacyClockScreen screen;
    private LegacyDisplay display;
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private Future<?> weatherTask;
    private Runnable weatherTimeout;
    private final Set<LegacyHttps> clients = new CopyOnWriteArraySet<>();
    private TextView weatherStatus;
    private LegacyWeather lastWeather;
    private LegacyPosition lastPosition;
    private String lastCity;
    private long lastWeatherAt;
    private long lastWeatherAttemptAt;
    private final Runnable weatherTick = this::updateWeatherSchedule;
    private int weatherProblem;
    private boolean foreground;
    private boolean pendingLocation;
    private volatile int generation;
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
        restoreWeather(state == null ? null : state.getBundle("weather"));
        screen = new LegacyClockScreen(this, state != null && state.getBoolean("uiLocked"),
            this::refreshWeather, this::refreshLocation, this::updateVisibleData);
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

    private void refreshWeather() { refreshWeather(false); }
    private void refreshLocation() {
        if (!foreground || !screen.isWeatherVisible() || !weatherEnabled() || screen.isLocked() || weatherTask != null) return;
        if (android.os.Build.VERSION.SDK_INT >= 23 && !LegacyLocator.permitted(this)) {
            requestPermissions(new String[] { "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION" }, 212);
            return;
        }
        refreshWeather(true);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (android.os.Build.VERSION.SDK_INT >= 23) super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 212) return;
        pendingLocation = LegacyLocator.permitted(this);
        if (pendingLocation && foreground) { pendingLocation = false; refreshWeather(true); }
        else if (!pendingLocation) android.widget.Toast.makeText(this, R.string.location_permission, android.widget.Toast.LENGTH_SHORT).show();
    }
    private void refreshWeather(boolean relocate) {
        refreshWeather(relocate, false);
    }
    private void refreshWeather(boolean relocate, boolean automatic) {
        if (!foreground || !screen.isWeatherVisible() || !weatherEnabled() || !automatic && screen.isLocked() || weatherTask != null) return;
        final LegacyPosition saved = LegacyPosition.read(getSharedPreferences("desk_clock_prefs", MODE_PRIVATE));
        if (saved == null && !relocate) {
            weatherProblem = lastWeather == null ? R.string.weather_no_location : R.string.weather_unavailable;
            showWeather();
            return;
        }
        final int ticket = ++generation;
        final long deadline = System.nanoTime() + 30_000_000_000L;
        weatherTimeout = () -> {
            if (!foreground || generation != ticket || weatherTask == null) return;
            cancelWeather(); weatherProblem = R.string.weather_unavailable;
            showWeather(); updateWeatherSchedule();
        };
        lastWeatherAttemptAt = System.currentTimeMillis();
        screen.setWeatherBusy(true);
        weatherStatus.setText(R.string.weather_updating);
        weatherTask = weatherWorker.submit(() -> {
            LegacyPosition position = saved;
            LegacyWeather incoming = null;
            String incomingCity = null;
            String phase = "Location";
            try {
                if (relocate) {
                    android.location.Location fix = LegacyLocator.find(this);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Location cancelled");
                    if (fix != null) position = LegacyPosition.from(fix, System.currentTimeMillis());
                }
                if (position == null) throw new IllegalStateException("No location available");
                phase = "TLS initialization";
                LegacyTls.factory(getApplicationContext());
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Weather cancelled");
                phase = "Geocoding";
                incomingCity = LegacyGeocoding.resolve(this, position, url -> readWeatherUrl(url, deadline, 6000, ticket));
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Weather cancelled");
                phase = "Weather refresh";
                String body = readWeatherUrl(new URL("https://api.open-meteo.com/v1/forecast?latitude=" + position.latitude +
                    "&longitude=" + position.longitude + "&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto&timeformat=unixtime"), deadline, 30000, ticket);
                incoming = LegacyWeather.parse(body);
            } catch (Exception error) {
                Log.w("LegacyClock", phase + " failed: " + error.getClass().getName());
            } finally {
                final LegacyWeather result = incoming;
                final String resultCity = incomingCity;
                final LegacyPosition resolvedPosition = position;
                final long fetchedAt = System.currentTimeMillis();
                ui.post(() -> {
                    if (!foreground || generation != ticket) return;
                    if (weatherTimeout != null) ui.removeCallbacks(weatherTimeout);
                    weatherTimeout = null;
                    weatherTask = null;
                    if (relocate && resolvedPosition != null && resolvedPosition != saved)
                        resolvedPosition.save(getSharedPreferences("desk_clock_prefs", MODE_PRIVATE));
                    if (result != null) { lastWeather = result; lastPosition = resolvedPosition; lastCity = resultCity; lastWeatherAt = fetchedAt; }
                    weatherProblem = result != null ? 0 : resolvedPosition == null && lastWeather == null
                        ? R.string.weather_no_location : R.string.weather_unavailable;
                    showWeather();
                    screen.setWeatherBusy(false);
                });
            }
        });
        ui.postDelayed(weatherTimeout, 30000);
    }
    private String readWeatherUrl(URL url, long deadline, long maximumMillis, int ticket) throws Exception {
        if (Thread.currentThread().isInterrupted() || generation != ticket) throw new InterruptedException("Weather cancelled");
        javax.net.ssl.SSLSocketFactory sockets = LegacyTls.factory(getApplicationContext());
        if (Thread.currentThread().isInterrupted() || generation != ticket) throw new InterruptedException("Weather cancelled");
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0) throw new java.net.SocketTimeoutException("Weather refresh deadline exceeded");
        LegacyHttps request = new LegacyHttps(sockets, Math.min(maximumMillis, remaining));
        clients.add(request);
        try {
            if (generation != ticket) { request.cancel(); throw new InterruptedException("Weather cancelled"); }
            return request.get(url);
        }
        finally { clients.remove(request); }
    }

    private boolean weatherEnabled() { return getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_weather_enabled", false); }
    private void updateWeatherSchedule() {
        ui.removeCallbacks(weatherTick);
        if (!foreground || !screen.isWeatherVisible() || !weatherEnabled()) return;
        showWeather();
        long now = System.currentTimeMillis();
        long reference = weatherProblem == 0 ? lastWeatherAt : lastWeatherAttemptAt;
        if (lastWeather != null && (now < reference || now - reference >= 30 * 60000L)) refreshWeather(false, true);
        ui.postDelayed(weatherTick, 60000);
    }
    private void showWeather() {
        screen.weatherCity.setText(lastWeather == null || !weatherEnabled() ? ""
            : lastCity == null ? getString(R.string.location_name_unavailable) : lastCity);
        screen.weatherForecast.setText(lastWeather == null || !weatherEnabled() ? "" : lastWeather.forecastText(this,
            getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_is_fahrenheit", false),
            getSharedPreferences("desk_clock_prefs", MODE_PRIVATE).getBoolean("pref_24_hour", false)));
        if (!weatherEnabled()) { weatherStatus.setText(R.string.weather_disabled_title); return; }
        if (weatherTask != null) { weatherStatus.setText(R.string.weather_updating); return; }
        String value = null;
        if (lastWeather != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("desk_clock_prefs", MODE_PRIVATE);
            boolean fahrenheit = prefs.getBoolean("pref_is_fahrenheit", false);
            String pattern = prefs.getBoolean("pref_24_hour", false) ? "M/d HH:mm" : "M/d hh:mm a";
            String time = new SimpleDateFormat(pattern, getResources().getConfiguration().locale).format(new Date(lastWeatherAt));
            value = getString(R.string.weather_value, fahrenheit ? lastWeather.currentTempC * 9 / 5 + 32 : lastWeather.currentTempC, fahrenheit ? "F" : "C", time)
                + "\n" + lastPosition.label(this, System.currentTimeMillis())
                + "\n" + LegacyWeather.description(this, lastWeather.currentCode);
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
        if (pendingLocation) { pendingLocation = false; refreshWeather(true); }
    }

    private void updateVisibleData() {
        if (!foreground) return;
        final int ticket = ++calendarGeneration;
        if (calendarTask != null) calendarTask.cancel(true);
        calendarTask = null;
        if (!screen.isWeatherVisible() || !weatherEnabled()) cancelWeather();
        else updateWeatherSchedule();
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
        ui.removeCallbacks(weatherTick);
        if (weatherTimeout != null) ui.removeCallbacks(weatherTimeout);
        weatherTimeout = null;
        generation++;
        boolean wasRefreshing = weatherTask != null;
        if (weatherTask != null) weatherTask.cancel(true);
        weatherTask = null;
        for (LegacyHttps request : clients) request.cancel();
        if (wasRefreshing) weatherProblem = 0;
        showWeather();
        screen.setWeatherBusy(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("uiLocked", screen.isLocked());
        if (lastWeather != null && lastPosition != null) {
            Bundle weather = new Bundle();
            weather.putString("body", lastWeather.body); weather.putLong("fetchedAt", lastWeatherAt);
            weather.putLong("attemptedAt", lastWeatherAttemptAt);
            weather.putString("city", lastCity); weather.putInt("problem", weatherProblem);
            weather.putDouble("lat", lastPosition.latitude); weather.putDouble("lon", lastPosition.longitude);
            weather.putLong("fix", lastPosition.fixTime == null ? 0 : lastPosition.fixTime);
            weather.putLong("observed", lastPosition.observedAt == null ? 0 : lastPosition.observedAt);
            weather.putString("provider", lastPosition.provider); state.putBundle("weather", weather);
        }
        display.saveState(state);
        super.onSaveInstanceState(state);
    }

    private void restoreWeather(Bundle state) {
        if (state == null) return;
        try {
            long fetchedAt = state.getLong("fetchedAt");
            LegacyWeather weather = LegacyWeather.parse(state.getString("body"));
            long fix = state.getLong("fix"), observed = state.getLong("observed");
            lastPosition = new LegacyPosition(state.getDouble("lat"), state.getDouble("lon"),
                fix > 0 ? fix : null, observed > 0 ? observed : null, state.getString("provider"));
            lastWeather = weather; lastWeatherAt = fetchedAt; lastCity = state.getString("city");
            lastWeatherAttemptAt = state.getLong("attemptedAt");
            weatherProblem = state.getInt("problem");
        } catch (Exception invalid) { Log.w("LegacyClock", "Saved weather unavailable"); }
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
