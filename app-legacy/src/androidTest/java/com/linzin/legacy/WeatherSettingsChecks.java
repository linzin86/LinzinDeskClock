package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.widget.Button;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

final class WeatherSettingsChecks {
    private static int requests;
    static void install() {
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                if (!"https".equals(scheme) || !"api.open-meteo.com".equals(url.getHost())) throw new IOException("Unexpected fixture URL");
                requests++; return new HttpsURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public String getCipherSuite() { return "fixture"; }
                    public Certificate[] getLocalCertificates() { return null; }
                    public Certificate[] getServerCertificates() { return null; }
                    public int getResponseCode() { return 200; }
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream("{\"current\":{\"temperature_2m\":26.0,\"weather_code\":0}}".getBytes("UTF-8"));
                    }
                };
            }
        });
    }
    static void run(Instrumentation test, Activity activity) {
        Button refresh = activity.getWindow().getDecorView().findViewWithTag("weather_refresh");
        TextView status = activity.getWindow().getDecorView().findViewWithTag("weather_status");
        if (refresh.isEnabled()) throw new AssertionError("Disabled weather must disable refresh");
        test.runOnMainSync(() -> refresh.performClick()); test.waitForIdleSync();
        if (requests != 0) throw new AssertionError("Disabled weather must not open a connection");
        PreferenceActivity settings = open(test, activity);
        SettingsChecks.click(test, settings, settings.findPreference("pref_weather_enabled"));
        SettingsChecks.click(test, settings, settings.findPreference("pref_is_fahrenheit"));
        test.runOnMainSync(settings::finish); test.waitForIdleSync();
        if (!refresh.isEnabled()) throw new AssertionError("Enabled weather must expose refresh");
        test.runOnMainSync(() -> refresh.performClick());
        long deadline = SystemClock.elapsedRealtime() + 45000;
        String[] value = new String[1];
        do {
            SystemClock.sleep(100); test.runOnMainSync(() -> value[0] = status.getText().toString());
        } while (!value[0].contains("78.8°F") && SystemClock.elapsedRealtime() < deadline);
        if (!value[0].contains("78.8°F")) throw new AssertionError("26C fixture must display as 78.8F: " + value[0]);
        int fetched = requests;
        settings = open(test, activity);
        SettingsChecks.click(test, settings, settings.findPreference("pref_is_fahrenheit"));
        PreferenceActivity lastSettings = settings;
        test.runOnMainSync(lastSettings::finish); test.waitForIdleSync();
        if (!status.getText().toString().contains("26.0°C") || requests != fetched)
            throw new AssertionError("Changing units must reformat the existing value without another request");
    }
    private static PreferenceActivity open(Instrumentation test, Activity activity) {
        return (PreferenceActivity) ((LegacyInstrumentation) test).openSettings(activity);
    }
}
