package com.linzin.legacy;

import android.content.Context;
import android.location.Geocoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class GeocodingChecks {
    static void run(String mode, Context context) throws Exception {
        if ("geocodingHttpTimeout".equals(mode) || "geocodingHttpCancel".equals(mode)) {
            GeocodingDeadlineChecks.run(context, "geocodingHttpCancel".equals(mode));
        } else if ("geocodingFast".equals(mode)) {
            if (!"Town".equals(LegacyGeocoding.bounded(() -> "Town", 1000))) throw new AssertionError("A completed system lookup must be returned");
        } else if ("geocodingTimeout".equals(mode) || "geocodingCancel".equals(mode)) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    result.set(LegacyGeocoding.bounded(() -> {
                        entered.countDown();
                        try {
                            boolean released = false;
                            while (!released) {
                                try { release.await(); released = true; }
                                catch (InterruptedException ignored) { }
                            }
                            return "Late town";
                        } finally { done.countDown(); }
                    }, "geocodingCancel".equals(mode) ? 6000 : 150));
                } catch (Throwable failure) { error.set(failure); }
            });
            caller.start();
            try {
                if (!entered.await(3, TimeUnit.SECONDS)) throw new AssertionError("The bounded lookup fixture must enter its blocked call");
                if ("geocodingCancel".equals(mode)) caller.interrupt();
                caller.join(3000);
                if (caller.isAlive() || result.get() != null) throw new AssertionError("The owner must finish before an uninterruptible lookup");
                if ("geocodingCancel".equals(mode) ? !(error.get() instanceof InterruptedException) : error.get() != null)
                    throw new AssertionError("Wrong timeout/cancellation boundary: " + error.get());
                AtomicInteger queued = new AtomicInteger();
                if (LegacyGeocoding.bounded(() -> { queued.incrementAndGet(); return "Queued town"; }, 1000) != null || queued.get() != 0)
                    throw new AssertionError("A blocked native lookup must not accumulate more workers or queued calls");
                String fallback = LegacyGeocoding.resolve(context, new LegacyPosition(25, 121, null, null, null),
                    url -> "{\"locality\":\"Fallback town\"}");
                if (!"Fallback town".equals(fallback)) throw new AssertionError("A blocked native Geocoder must leave HTTPS fallback available");
            } finally { release.countDown(); caller.interrupt(); caller.join(3000); done.await(3, TimeUnit.SECONDS); }
            if (result.get() != null) throw new AssertionError("Late results must remain discarded");
        } else if ("geocodingFallback".equals(mode)) {
            if (Geocoder.isPresent()) throw new AssertionError("This fixture requires an AVD without a system Geocoder");
            LegacyPosition position = new LegacyPosition(25, 121, null, null, null);
            AtomicInteger calls = new AtomicInteger();
            String city = LegacyGeocoding.resolve(context, position, url -> {
                calls.incrementAndGet();
                if (!"https".equals(url.getProtocol()) || !"api.bigdatacloud.net".equals(url.getHost())
                    || !url.getQuery().contains("latitude=25.0") || !url.getQuery().contains("longitude=121.0"))
                    throw new AssertionError("Reverse geocoding must use explicit coordinates over HTTPS");
                return "{\"countryName\":\"Country\",\"principalSubdivision\":\"State\",\"locality\":\"Country\",\"city\":\"State\","
                    + "\"localityInfo\":{\"administrative\":[{\"adminLevel\":5,\"name\":\"County\"},{\"adminLevel\":8,\"name\":\"Town\"}]}}";
            });
            if (!"Town".equals(city) || calls.get() != 1) throw new AssertionError("Unavailable native geocoding must use the shared safe town selector");
            String rejected = LegacyGeocoding.resolve(context, position, url -> { throw new javax.net.ssl.SSLHandshakeException("fixture untrusted peer"); });
            if (rejected != null) throw new AssertionError("A TLS failure must leave the city unavailable");
        } else throw new AssertionError("Unknown geocoding case: " + mode);
    }
}
