package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

final class ForecastDataChecks {
    static volatile int weatherRequests;
    static volatile boolean networkAvailable = true;
    static volatile int temperatureC = 26;
    private static volatile long refreshedAt, geoOpenedAt, weatherOpenedAt;
    static void city(Instrumentation test, Activity activity) {
        TextView name = activity.getWindow().getDecorView().findViewWithTag("weather_city");
        if (name == null || !name.getText().toString().contains("Fixture City"))
            throw new AssertionError("A secure reverse-geocoded city must be exposed with the weather; city="
                + (name == null ? "missing view" : name.getText()) + " geoOpenedAfterMs="
                + (geoOpenedAt == 0 ? -1 : geoOpenedAt - refreshedAt) + " weatherOpenedAfterMs=" + (weatherOpenedAt - refreshedAt));
    }
    static void metadata(Instrumentation test, Activity activity) {
        String[] text = new String[1];
        test.runOnMainSync(() -> text[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        if (!text[0].contains("GPS") || text[0].contains("Time unknown") || !text[0].contains("Last location"))
            throw new AssertionError("Saved fix metadata must retain its real time and source: " + text[0]);
    }
    static void install() {
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws java.io.IOException {
                if (!"https".equals(scheme) || !("api.open-meteo.com".equals(url.getHost()) || "api.bigdatacloud.net".equals(url.getHost())))
                    throw new java.io.IOException("Unexpected fixture URL");
                if ("api.open-meteo.com".equals(url.getHost())) { weatherRequests++; weatherOpenedAt = android.os.SystemClock.elapsedRealtime(); }
                else geoOpenedAt = android.os.SystemClock.elapsedRealtime();
                return new HttpsURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public String getCipherSuite() { return "fixture"; }
                    public Certificate[] getLocalCertificates() { return null; }
                    public Certificate[] getServerCertificates() { return null; }
                    public int getResponseCode() throws java.io.IOException {
                        if (!networkAvailable) throw new java.net.ConnectException("Controlled fixture is offline");
                        return 200;
                    }
                    public InputStream getInputStream() throws java.io.IOException {
                        getResponseCode();
                        try {
                            if ("api.bigdatacloud.net".equals(url.getHost())) return new ByteArrayInputStream(
                                "{\"countryName\":\"Fixture Country\",\"principalSubdivision\":\"Fixture Province\",\"locality\":\"Fixture City\"}".getBytes("UTF-8"));
                            return new ByteArrayInputStream(body().getBytes("UTF-8"));
                        } catch (org.json.JSONException error) { throw new java.io.IOException(error); }
                    }
                };
            }
        });
    }
    static String body() throws org.json.JSONException {
        long hour = System.currentTimeMillis() / 3600000 * 3600000;
        JSONArray times = new JSONArray(), temperatures = new JSONArray(), codes = new JSONArray();
        for (int i = 5; i >= 0; i--) {
            times.put((hour + (i - 1) * 3600000) / 1000); temperatures.put(20 + i); codes.put(0);
        }
        return new JSONObject().put("timezone", "UTC")
            .put("current", new JSONObject().put("temperature_2m", temperatureC).put("weather_code", 0))
            .put("hourly", new JSONObject().put("time", times).put("temperature_2m", temperatures).put("weather_code", codes)).toString();
    }
    static void run(Instrumentation test, Activity activity) {
        Button refresh = activity.getWindow().getDecorView().findViewWithTag("weather_refresh");
        refreshedAt = android.os.SystemClock.elapsedRealtime();
        test.runOnMainSync(() -> refresh.performClick());
        long deadline = SystemClock.elapsedRealtime() + 45000;
        String[] status = new String[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> status[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        } while (!status[0].contains("26.0") && SystemClock.elapsedRealtime() < deadline);
        if (!status[0].contains("26.0")) throw new AssertionError("Fixture current temperature must reach the clock: " + status[0]);
        TextView forecast = activity.getWindow().getDecorView().findViewWithTag("weather_forecast");
        if (forecast == null) throw new AssertionError("Weather must expose four hourly forecasts");
        String value = forecast.getText().toString();
        if (value.split("\n").length != 4) throw new AssertionError("Exactly four forecast rows are required: " + value);
        for (int temperature = 21; temperature <= 24; temperature++)
            if (!value.contains(temperature + "°")) throw new AssertionError("Forecast must be sorted and include the current hour: " + value);
        if (value.contains("20°") || value.contains("25°")) throw new AssertionError("Previous and fifth future rows must be excluded");
    }
}
