package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

final class WeatherDeadlineChecks {
    private static final CountDownLatch geocoding = new CountDownLatch(1);
    private static volatile boolean weatherClosed;
    private static volatile int requests;
    static void install(boolean total) {
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                requests++;
                boolean geo = "api.bigdatacloud.net".equals(url.getHost());
                if (!"https".equals(scheme) || !geo && !"api.open-meteo.com".equals(url.getHost()))
                    throw new IOException("Unexpected deadline fixture URL");
                return new HttpsURLConnection(url) {
                    private final CountDownLatch disconnected = new CountDownLatch(1);
                    public void connect() { }
                    public void disconnect() { if (!geo) weatherClosed = true; disconnected.countDown(); }
                    public boolean usingProxy() { return false; }
                    public String getCipherSuite() { return "fixture"; }
                    public Certificate[] getLocalCertificates() { return null; }
                    public Certificate[] getServerCertificates() { return null; }
                    public int getResponseCode() throws IOException {
                        if (geo) geocoding.countDown();
                        if (geo || total) {
                            try { disconnected.await(geo && total ? 20 : 60, TimeUnit.SECONDS); }
                            catch (InterruptedException cancelled) {
                                Thread.currentThread().interrupt(); throw new InterruptedIOException("Fixture cancelled");
                            }
                            if (disconnected.getCount() == 0) throw new IOException("Fixture disconnected");
                        }
                        return 200;
                    }
                    public InputStream getInputStream() throws IOException {
                        try { return new ByteArrayInputStream((geo ? "{\"locality\":\"Fixture City\"}" : ForecastDataChecks.body()).getBytes("UTF-8")); }
                        catch (org.json.JSONException invalid) { throw new IOException(invalid); }
                    }
                };
            }
        });
    }
    static void run(Instrumentation test, Activity clock, boolean total) throws Exception {
        LegacyTls.factory(clock.getApplicationContext());
        Button refresh = clock.getWindow().getDecorView().findViewWithTag("weather_refresh");
        long cachedAt = System.currentTimeMillis() - 600000;
        if (total) {
            LegacyWeather cached = LegacyWeather.parse(ForecastDataChecks.body());
            test.runOnMainSync(() -> {
                try {
                    put(clock, "lastWeather", cached);
                    put(clock, "lastPosition", new LegacyPosition(25, 121, null, null, null));
                    put(clock, "lastWeatherAt", cachedAt); put(clock, "lastCity", "Saved city");
                } catch (Exception invalid) { throw new AssertionError(invalid); }
            });
        }
        long started = SystemClock.elapsedRealtime();
        test.runOnMainSync(() -> refresh.performClick());
        if (!geocoding.await(5, TimeUnit.SECONDS)) throw new AssertionError("The fixture must reach the optional geocoding request");
        long deadline = total ? started + 35000 : SystemClock.elapsedRealtime() + 10000;
        String[] text = new String[1]; boolean[] ready = new boolean[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> {
                ready[0] = refresh.isEnabled();
                text[0] = ((TextView) clock.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString();
            });
        } while ((!ready[0] || !total && !text[0].contains("26.0")) && SystemClock.elapsedRealtime() < deadline);
        if (total) {
            if (!ready[0] || !text[0].toLowerCase(java.util.Locale.US).contains("unavailable"))
                throw new AssertionError("One refresh must stop within its total deadline, retaining an explicit error: " + text[0]);
            long cleanup = SystemClock.elapsedRealtime() + 2000;
            while (!weatherClosed && SystemClock.elapsedRealtime() < cleanup) SystemClock.sleep(20);
            if (!weatherClosed) throw new AssertionError("Total timeout must close the active weather request");
            SystemClock.sleep(500); test.waitForIdleSync();
            java.lang.reflect.Field fetchedAt = LegacyActivity.class.getDeclaredField("lastWeatherAt");
            fetchedAt.setAccessible(true);
            TextView city = clock.getWindow().getDecorView().findViewWithTag("weather_city");
            if (fetchedAt.getLong(clock) != cachedAt || !"Saved city".contentEquals(city.getText()) || !text[0].contains("26.0"))
                throw new AssertionError("Timeout and late completion must retain the original weather, city and fetched time");
        } else if (!ready[0] || !text[0].contains("26.0")) {
            throw new AssertionError("Slow optional geocoding must yield to weather within ten seconds: " + text[0]);
        }
    }
    static Activity rollback(LegacyInstrumentation test, Activity clock) throws Exception {
        LegacyWeather cached = LegacyWeather.parse(ForecastDataChecks.body());
        test.runOnMainSync(() -> {
            try {
                put(clock, "lastWeather", cached);
                put(clock, "lastPosition", new LegacyPosition(25, 121, null, null, null));
                put(clock, "lastWeatherAt", System.currentTimeMillis() + 3600000);
                put(clock, "lastWeatherAttemptAt", System.currentTimeMillis());
                put(clock, "weatherProblem", R.string.weather_unavailable);
            } catch (Exception invalid) { throw new AssertionError(invalid); }
        });
        Activity settings = test.openSettings(clock);
        test.runOnMainSync(settings::finish);
        test.awaitResumed(clock.getComponentName().getClassName(), null);
        test.runOnMainSync(() -> {
            Button refresh = clock.getWindow().getDecorView().findViewWithTag("weather_refresh");
            if (!refresh.isEnabled() || requests != 0) throw new AssertionError("An earlier failed attempt must prevent immediate retries after the clock moves behind cached weather");
        });
        test.runOnMainSync(clock::recreate);
        Activity restored = test.awaitResumed(clock.getComponentName().getClassName(), clock);
        test.runOnMainSync(() -> {
            Button refresh = restored.getWindow().getDecorView().findViewWithTag("weather_refresh");
            TextView status = restored.getWindow().getDecorView().findViewWithTag("weather_status");
            if (!refresh.isEnabled() || requests != 0 || !status.getText().toString().contains("26.0")
                || !status.getText().toString().toLowerCase(java.util.Locale.US).contains("unavailable"))
                throw new AssertionError("Recreation must retain failed weather and its retry interval without a new request: " + status.getText());
        });
        return restored;
    }
    private static void put(Activity clock, String name, Object value) throws Exception {
        java.lang.reflect.Field field = LegacyActivity.class.getDeclaredField(name);
        field.setAccessible(true); field.set(clock, value);
    }
}
