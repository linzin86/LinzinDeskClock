package com.linzin.legacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyHttpsTest {
    private static class Fixture extends HttpsURLConnection {
        boolean disconnected;
        boolean closed;
        String body = "{}";
        IOException error;
        Fixture(URL url) { super(url); }
        public void connect() { }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public String getCipherSuite() { return "fixture"; }
        public Certificate[] getLocalCertificates() { return null; }
        public Certificate[] getServerCertificates() { return null; }
        public int getResponseCode() { return 200; }
        public InputStream getInputStream() throws IOException {
            if (error != null) throw error;
            return new ByteArrayInputStream(body.getBytes("UTF-8")) {
                public void close() throws IOException { closed = true; super.close(); }
            };
        }
    }
    private Fixture fixture;
    private URL url(String scheme) throws Exception {
        return new URL(null, scheme + "://fixture.invalid/data", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { fixture = new Fixture(url); return fixture; }
        });
    }
    private LegacyHttps client() { return new LegacyHttps((SSLSocketFactory) SSLSocketFactory.getDefault()); }

    @Test public void cleartextIsRejectedBeforeAnyConnection() throws Exception {
        try { client().get(url("http")); fail("Must reject HTTP"); } catch (IOException expected) { }
        assertNull(fixture);
    }

    @Test public void httpsSuccessClosesTheStreamAndDisconnects() throws Exception {
        assertEquals("{}", client().get(url("https")));
        assertTrue(fixture.closed);
        assertTrue(fixture.disconnected);
    }

    @Test public void oversizedResponsesAreRejectedAndDisconnected() throws Exception {
        URL url = new URL(null, "https://fixture.invalid/data", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                fixture = new Fixture(url); fixture.body = new String(new char[65537]).replace('\0', 'x'); return fixture;
            }
        });
        try { client().get(url); fail("Must reject oversized input"); } catch (IOException expected) { }
        assertNotNull(fixture);
        assertTrue(fixture.closed);
        assertTrue(fixture.disconnected);
    }

    @Test public void failedReadsStillDisconnect() throws Exception {
        URL url = new URL(null, "https://fixture.invalid/data", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                fixture = new Fixture(url); fixture.error = new IOException("fixture read failure"); return fixture;
            }
        });
        try { client().get(url); fail("Read must fail"); } catch (IOException expected) { }
        assertNotNull(fixture);
        assertTrue(fixture.disconnected);
    }

    @Test public void cancellationStartsCleanupWithoutBlockingTheUiCaller() throws Exception {
        CountDownLatch reading = new CountDownLatch(1), cleanup = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        URL url = new URL(null, "https://fixture.invalid/data", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                return new Fixture(url) {
                    public InputStream getInputStream() {
                        return new InputStream() {
                            public int read() throws IOException {
                                reading.countDown();
                                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                                throw new IOException("closed fixture");
                            }
                        };
                    }
                    public void disconnect() {
                        cleanup.countDown();
                        try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                };
            }
        });
        LegacyHttps client = client();
        java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<?> job = executor.submit(() -> {
            try { client.get(url); } catch (IOException expected) { }
        });
        try {
            assertTrue(reading.await(1, TimeUnit.SECONDS));
            long before = System.nanoTime();
            client.cancel();
            assertTrue("Cancellation must not block on disconnect", System.nanoTime() - before < 500_000_000L);
            assertTrue("Cleanup must begin before the provider returns", cleanup.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown(); job.get(3, TimeUnit.SECONDS); executor.shutdownNow();
        }
    }

    @Test public void requestDeadlineCancelsABlockedResponse() throws Exception {
        CountDownLatch releasedResponse = new CountDownLatch(1);
        URL url = new URL(null, "https://fixture.invalid/data", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                return new Fixture(url) {
                    public int getResponseCode() {
                        try { releasedResponse.await(2, TimeUnit.SECONDS); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                        return 200;
                    }
                    public void disconnect() { releasedResponse.countDown(); }
                };
            }
        });
        LegacyHttps client = new LegacyHttps((SSLSocketFactory) SSLSocketFactory.getDefault(), 100);
        long before = System.nanoTime();
        try { client.get(url); fail("Expired request must fail"); }
        catch (IOException expected) { }
        assertTrue("Deadline must actively cancel blocking IO", System.nanoTime() - before < 1_000_000_000L);
    }
}
