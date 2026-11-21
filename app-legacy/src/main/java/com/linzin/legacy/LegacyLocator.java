package com.linzin.legacy;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class LegacyLocator {
    static boolean permitted(Context context) {
        return context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED
            || context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED;
    }
    static Location find(Context context) throws InterruptedException {
        if (!permitted(context)) return null;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return null;
        return find(new Driver() {
            public Location cached(String provider) {
                if (context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED
                    && context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION") != PackageManager.PERMISSION_GRANTED) return null;
                return manager.getLastKnownLocation(provider);
            }
            public boolean enabled(String provider) {
                return (!"gps".equals(provider) || context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED)
                    && manager.isProviderEnabled(provider);
            }
            public void register(String provider, LocationListener listener) {
                if (context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED
                    && context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION") != PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("Location permission unavailable");
                manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
            }
            public void remove(LocationListener listener) { manager.removeUpdates(listener); }
        }, System.currentTimeMillis(), 6000);
    }
    interface Driver {
        Location cached(String provider);
        boolean enabled(String provider);
        void register(String provider, LocationListener listener);
        void remove(LocationListener listener);
    }
    static Location find(Driver driver, long now, long waitMillis) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Location cancelled");
        Location fallback = null;
        for (String provider : new String[] { "network", "gps", "passive" }) {
            try {
                Location cached = driver.cached(provider);
                if (valid(cached) && (fallback == null || cached.getTime() > fallback.getTime())) fallback = cached;
            } catch (RuntimeException ignored) { }
        }
        if (fallback != null && fallback.getTime() > 0 && fallback.getTime() <= now && now - fallback.getTime() < 600000) return fallback;
        AtomicReference<Location> result = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        CountDownLatch delivered = new CountDownLatch(1);
        LocationListener listener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Providers can replay old fixes immediately after registration.
                boolean expired = location != null && location.getTime() > 0 && location.getTime() <= now
                    && now - location.getTime() >= 600000;
                if (!closed.get() && valid(location) && !expired
                    && result.compareAndSet(null, location)) delivered.countDown();
            }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
            @Deprecated public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        boolean attempted = false;
        try {
            for (String provider : new String[] { "network", "gps" }) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Location cancelled");
                if (result.get() != null) break;
                boolean enabled;
                try { enabled = driver.enabled(provider); }
                catch (RuntimeException unavailable) { continue; }
                if (!enabled) continue;
                attempted = true;
                try { driver.register(provider, listener); }
                catch (RuntimeException failure) { return result.get() == null ? fallback : result.get(); }
            }
            if (attempted && result.get() == null) delivered.await(waitMillis, TimeUnit.MILLISECONDS);
            return result.get() == null ? fallback : result.get();
        } finally {
            closed.set(true);
            if (attempted) {
                try { driver.remove(listener); }
                catch (RuntimeException failure) { Log.w("LegacyClock", "Unable to remove location listener", failure); }
            }
        }
    }
    private static boolean valid(Location location) {
        return location != null && location.getLatitude() >= -90 && location.getLatitude() <= 90
            && location.getLongitude() >= -180 && location.getLongitude() <= 180
            && (Math.abs(location.getLatitude()) > .001 || Math.abs(location.getLongitude()) > .001);
    }
}
