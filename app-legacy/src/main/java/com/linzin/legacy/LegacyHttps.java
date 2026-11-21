package com.linzin.legacy;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Timer;
import java.util.TimerTask;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

final class LegacyHttps {
    private final Tls12SocketFactory factory;
    private final long timeoutMillis;
    private final AtomicReference<HttpsURLConnection> active = new AtomicReference<>();
    private volatile boolean cancelled;
    LegacyHttps(SSLSocketFactory factory) { this(factory, 30000); }
    LegacyHttps(SSLSocketFactory factory, long timeoutMillis) {
        this.factory = new Tls12SocketFactory(factory);
        this.timeoutMillis = timeoutMillis;
    }
    String get(URL url) throws IOException {
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS is required");
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        checkActive(deadline);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        active.set(connection);
        Timer watchdog = new Timer("legacy-https-deadline", true);
        watchdog.schedule(new TimerTask() { @Override public void run() { LegacyHttps.this.cancel(); } }, timeoutMillis);
        try {
            checkActive(deadline);
            int platformTimeout = (int) Math.max(1, Math.min(15000, (deadline - System.nanoTime()) / 1_000_000));
            connection.setConnectTimeout(platformTimeout);
            connection.setReadTimeout(platformTimeout);
            connection.setInstanceFollowRedirects(false);
            connection.setSSLSocketFactory(factory);
            connection.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("User-Agent", "LinzinDeskClock-Legacy-Spike");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Unexpected HTTP status: " + status);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] bytes = new byte[4096];
                while (true) {
                    checkActive(deadline);
                    int count = stream.read(bytes);
                    if (count < 0) break;
                    if (body.size() + count > 65536) throw new IOException("Response exceeds 64 KiB");
                    body.write(bytes, 0, count);
                }
            }
            checkActive(deadline);
            return body.toString("UTF-8");
        } finally {
            watchdog.cancel();
            if (active.compareAndSet(connection, null)) connection.disconnect();
        }
    }
    private void checkActive(long deadline) throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Request cancelled");
        if (System.nanoTime() > deadline) throw new SocketTimeoutException("Request deadline exceeded");
    }
    void cancel() {
        cancelled = true;
        factory.cancel();
        HttpsURLConnection connection = active.getAndSet(null);
        if (connection == null) return;
        try {
            Thread cleanup = new Thread(() -> {
                try { connection.disconnect(); }
                catch (RuntimeException error) { active.compareAndSet(null, connection); }
            }, "legacy-https-cancel");
            cleanup.setDaemon(true);
            cleanup.start();
        } catch (RuntimeException error) {
            active.compareAndSet(null, connection);
        }
    }
}
