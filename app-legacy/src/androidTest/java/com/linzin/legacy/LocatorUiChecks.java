package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class LocatorUiChecks {
    static void primeOld(LegacyInstrumentation test) throws Exception {
        LocationManager manager = (LocationManager) test.getTargetContext().getSystemService(Activity.LOCATION_SERVICE);
        CountDownLatch old = new CountDownLatch(1);
        LocationListener listener = new LocationListener() {
            public void onLocationChanged(Location location) {
                long age = System.currentTimeMillis() - location.getTime();
                if (location.getTime() > 0 && age > 600000 && Math.abs(location.getLatitude() - 25.1) < .001
                    && Math.abs(location.getLongitude() - 121.3) < .001) old.countDown();
            }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
            public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        try {
            manager.requestLocationUpdates("gps", 0, 0, listener, android.os.Looper.getMainLooper());
            Bundle witness = new Bundle(); witness.putString("stream", "GPS stimulus requested\n"); test.sendStatus(0, witness);
            if (!old.await(10, TimeUnit.SECONDS)) throw new AssertionError("A dated emulator NMEA fix must reach the old-cache fixture");
        } finally { manager.removeUpdates(listener); }
    }
    static void run(Instrumentation test, Activity activity, boolean active) throws Exception {
        double expectedLatitude = active ? 25.2 : 25.1, expectedLongitude = active ? 121.4 : 121.3;
        long requestedAt = System.currentTimeMillis();
        Button locate = activity.getWindow().getDecorView().findViewWithTag("weather_locate");
        if (locate == null || !locate.isEnabled()) throw new AssertionError("Weather needs an accessible active-location entry");
        LocationManager manager = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
        if (active) {
            if (!manager.isProviderEnabled("gps")) throw new AssertionError("Uncached active-location fixture requires the system GPS provider to be enabled");
            requireOld(manager);
            CountDownLatch queued = new CountDownLatch(1);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, android.content.Intent intent) { queued.countDown(); }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter("com.linzin.legacy.test.GPS_QUEUED");
            if (android.os.Build.VERSION.SDK_INT >= 33)
                activity.registerReceiver(receiver, filter, "android.permission.DUMP", null, android.content.Context.RECEIVER_EXPORTED);
            else registerLegacyQueueReceiver(activity, receiver, filter);
            try {
                Bundle ready = new Bundle(); ready.putString("stream", "GPS queue requested\n"); test.sendStatus(0, ready);
                if (!queued.await(10, TimeUnit.SECONDS)) throw new AssertionError("Host must queue the new GPS position before acquisition");
            } finally { activity.unregisterReceiver(receiver); }
            requireOld(manager);
            Bundle verified = new Bundle(); verified.putString("stream", "Old system cache verified after GPS queue\n"); test.sendStatus(0, verified);
        }
        CountDownLatch primed = new CountDownLatch(1);
        LocationListener primer = new LocationListener() {
            public void onLocationChanged(Location location) {
                if (Math.abs(location.getLatitude() - 25.1) < .001 && Math.abs(location.getLongitude() - 121.3) < .001) primed.countDown();
            }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
            @Deprecated public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        try {
            if (!active) {
            manager.requestLocationUpdates("gps", 0, 0, primer, android.os.Looper.getMainLooper());
            Bundle ready = new Bundle(); ready.putString("stream", "GPS stimulus requested\n"); test.sendStatus(0, ready);
            if (!primed.await(6, TimeUnit.SECONDS)) throw new AssertionError("The real GPS fixture must reach the requested position before the UI action");
            }
        } finally { manager.removeUpdates(primer); }
        test.runOnMainSync(() -> locate.performClick());
        Bundle witness = new Bundle(); witness.putString("stream", "Location acquisition requested\n"); test.sendStatus(0, witness);
        long deadline = SystemClock.elapsedRealtime() + 45000;
        String[] text = new String[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> text[0] = ((TextView) activity.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        } while (!text[0].contains("26.0") && SystemClock.elapsedRealtime() < deadline);
        if (!text[0].contains("26.0") || !text[0].contains("GPS"))
            throw new AssertionError("An actual emulator GPS fix must reach weather with honest source metadata: " + text[0]);
        Object latitude = activity.getSharedPreferences("desk_clock_prefs", 0).getAll().get("pref_last_valid_lat");
        Object longitude = activity.getSharedPreferences("desk_clock_prefs", 0).getAll().get("pref_last_valid_lon");
        if (!(latitude instanceof String) || !(longitude instanceof String)
            || Math.abs(Double.parseDouble((String) latitude) - expectedLatitude) > .001
            || Math.abs(Double.parseDouble((String) longitude) - expectedLongitude) > .001)
            throw new AssertionError("The emulator stimulus must supply the persisted position");
        if (active) {
            String fix = activity.getSharedPreferences("desk_clock_prefs", 0).getString("pref_last_location_fix_time", "0");
            if (Long.parseLong(fix) < requestedAt - 3000) throw new AssertionError("The active result must retain the newly injected fix time");
        }
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerLegacyQueueReceiver(Activity activity, android.content.BroadcastReceiver receiver,
        android.content.IntentFilter filter) {
        // The caller is below API33; DUMP restricts this test-only signal to adb.
        activity.registerReceiver(receiver, filter, "android.permission.DUMP", null);
    }
    private static void requireOld(LocationManager manager) {
        for (String provider : new String[] { "gps", "network", "passive" }) {
            Location previous = manager.getLastKnownLocation(provider);
            long now = System.currentTimeMillis();
            if (previous != null && previous.getTime() > 0 && previous.getTime() <= now
                && now - previous.getTime() < 600000 && (Math.abs(previous.getLatitude()) > .001 || Math.abs(previous.getLongitude()) > .001))
                throw new AssertionError("Uncached active-location fixture requires old system fixes: " + provider + " age=" + (now - previous.getTime()));
        }
    }
}
