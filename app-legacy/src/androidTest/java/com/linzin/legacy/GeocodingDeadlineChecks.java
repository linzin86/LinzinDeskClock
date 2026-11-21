package com.linzin.legacy;

import android.content.Context;
import android.location.Geocoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class GeocodingDeadlineChecks {
    static void run(Context context, boolean cancel) throws Exception {
        if (Geocoder.isPresent()) throw new AssertionError("This fixture requires an AVD without a system Geocoder");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        LegacyPosition position = new LegacyPosition(25, 121, null, null, null);
        Thread caller = new Thread(() -> {
            try {
                result.set(LegacyGeocoding.resolve(context, position, url -> {
                    calls.incrementAndGet(); entered.countDown();
                    try {
                        while (release.getCount() != 0) {
                            try { release.await(); } catch (InterruptedException ignored) { }
                        }
                        return "{\"locality\":\"Late town\"}";
                    } finally { done.countDown(); }
                }));
            } catch (Throwable failure) { error.set(failure); }
        });
        caller.start();
        try {
            if (!entered.await(4, TimeUnit.SECONDS)) throw new AssertionError("The fixture must reach the HTTPS reverse-geocoding call");
            if (cancel) caller.interrupt();
            caller.join(cancel ? 1500 : 8500);
            if (caller.isAlive() || result.get() != null)
                throw new AssertionError("HTTPS reverse geocoding must yield at its deadline or cancellation even while IO ignores interruption");
            if (cancel ? !(error.get() instanceof InterruptedException) : error.get() != null)
                throw new AssertionError("Wrong HTTP geocoding cancellation boundary: " + error.get());
            String queued = LegacyGeocoding.resolve(context, position, url -> {
                calls.incrementAndGet(); return "{\"locality\":\"Queued town\"}";
            });
            if (queued != null || calls.get() != 1)
                throw new AssertionError("Blocked HTTPS geocoding must not accumulate workers or queued requests");
        } finally {
            release.countDown(); caller.interrupt(); caller.join(3000); done.await(3, TimeUnit.SECONDS);
        }
        if (result.get() != null) throw new AssertionError("Late HTTPS city results must remain discarded");
    }
}
