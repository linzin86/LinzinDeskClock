package com.linzin.legacy;

import android.location.Location;
import android.location.LocationListener;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class LocatorChecks {
    static void run(String mode) throws Exception {
        Fake driver = new Fake();
        driver.cache = point("gps", 25, 121, 1000);
        if ("locatorCache".equals(mode)) {
            Location found = LegacyLocator.find(driver, 1100, 50);
            if (found != driver.cache || driver.registrations != 0) throw new AssertionError("A fresh system fix must preserve its time without registering");
        } else if ("locatorFix".equals(mode)) {
            driver.immediate = point("network", 35, 139, 999000);
            Location found = LegacyLocator.find(driver, 1000000, 50);
            if (found != driver.immediate || found.getTime() != 999000 || driver.registrations != 1 || driver.removals != 1)
                throw new AssertionError("A delivered fix must keep its original metadata and remove the listener once");
        } else if ("locatorCallbackFailure".equals(mode)) {
            driver.immediate = point("network", 35, 139, 999000); driver.failAfterCallback = true;
            if (LegacyLocator.find(driver, 1000000, 50) != driver.immediate || driver.removals != 1)
                throw new AssertionError("A registration error after a valid callback must not replace the accepted fix");
        } else if ("locatorTimeout".equals(mode)) {
            Location found = LegacyLocator.find(driver, 1000000, 50);
            if (found != driver.cache || found.getTime() != 1000 || driver.registrations != 2 || driver.removals != 1)
                throw new AssertionError("Timeout must preserve the old fix and remove both provider registrations once");
        } else if ("locatorFailure".equals(mode)) {
            driver.fail = true;
            Location found = LegacyLocator.find(driver, 1000000, 50);
            if (found != driver.cache || driver.removals != 1) throw new AssertionError("A partially failed registration still needs cleanup and the old fallback");
        } else if ("locatorCancel".equals(mode)) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try { LegacyLocator.find(driver, 1000000, 6000); }
                catch (Throwable failure) { error.set(failure); }
            });
            worker.start();
            try {
                if (!driver.registered.await(2, TimeUnit.SECONDS)) throw new AssertionError("Cancellation fixture must reach registration");
                worker.interrupt(); worker.join(2000);
                if (worker.isAlive() || !(error.get() instanceof InterruptedException) || driver.removals != 1)
                    throw new AssertionError("Cancellation must finish and remove the active listener once: " + error.get());
            } finally { worker.interrupt(); worker.join(2000); }
        } else if ("locatorStaleCallback".equals(mode)) {
            driver.immediate = driver.cache; driver.after = point("gps", 25.2, 121.4, 999999);
            try {
                if (LegacyLocator.find(driver, 1000000, 300) != driver.after || driver.removals != 1)
                    throw new AssertionError("A replayed old fix must not finish acquisition before a recent callback");
            } finally { if (driver.delivery != null) driver.delivery.join(2000); }
        } else if ("locatorStaleOnly".equals(mode)) {
            driver.immediate = driver.cache;
            long started = SystemClock.elapsedRealtime();
            if (LegacyLocator.find(driver, 1000000, 50) != driver.cache || SystemClock.elapsedRealtime() - started < 40 || driver.removals != 1)
                throw new AssertionError("Stale callbacks must keep waiting, then preserve the old fallback");
        } else if ("locatorFutureCallback".equals(mode)) {
            for (long time : new long[] { 0, Long.MAX_VALUE }) {
                driver.immediate = point("gps", 25.2, 121.4, time);
                if (LegacyLocator.find(driver, 1000000, 50) != driver.immediate)
                    throw new AssertionError("Unknown-age coordinates must remain usable without inventing a fix time");
            }
        } else if ("locatorInvalid".equals(mode)) {
            driver.enabled = false; driver.cache = point("gps", Double.NaN, 121, 1000);
            if (LegacyLocator.find(driver, 1100, 50) != null || driver.registrations != 0)
                throw new AssertionError("Invalid cached coordinates must not become a real location");
        } else throw new AssertionError("Unknown locator case: " + mode);
    }
    private static Location point(String provider, double latitude, double longitude, long time) {
        Location value = new Location(provider); value.setLatitude(latitude); value.setLongitude(longitude); value.setTime(time); return value;
    }
    private static final class Fake implements LegacyLocator.Driver {
        Location cache, immediate, after;
        Thread delivery;
        boolean enabled = true, fail, failAfterCallback;
        volatile int registrations, removals;
        final CountDownLatch registered = new CountDownLatch(1);
        public Location cached(String provider) { return cache; }
        public boolean enabled(String provider) { return enabled; }
        public void register(String provider, LocationListener listener) {
            registrations++; registered.countDown();
            if (fail) throw new SecurityException("fixture registration failure");
            if (immediate != null) listener.onLocationChanged(immediate);
            if (after != null && delivery == null) {
                delivery = new Thread(() -> { SystemClock.sleep(40); listener.onLocationChanged(after); });
                delivery.start();
            }
            if (failAfterCallback) throw new SecurityException("fixture failure after delivery");
        }
        public void remove(LocationListener listener) { removals++; }
    }
}
