package com.linzin.legacy;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

final class HttpsProbeChecks {
    private static IOException responseFailure;
    private static IOException bodyFailure;
    private static String nativeHost;
    static void run() throws Exception {
        URL endpoint = new URL(null, "https://fixture.invalid/", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                nativeHost = url.getHost();
                return new HttpsURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public String getCipherSuite() { return "fixture"; }
                    public Certificate[] getLocalCertificates() { return null; }
                    public Certificate[] getServerCertificates() { return null; }
                    public int getResponseCode() throws IOException {
                        if (responseFailure != null) throw responseFailure;
                        return 200;
                    }
                    public java.io.InputStream getInputStream() {
                        return new java.io.InputStream() {
                            public int read() throws IOException {
                                if (bodyFailure != null) throw bodyFailure;
                                return -1;
                            }
                        };
                    }
                };
            }
        });
        HttpsProbe.install(endpoint);
        responseFailure = new SocketTimeoutException("optional geocoding timeout");
        failResponse(request("api.bigdatacloud.net"));
        if (!"api.bigdatacloud.net".equals(nativeHost) || HttpsProbe.requests != 0 || HttpsProbe.failure != null)
            throw new AssertionError("Optional geocoding must preserve its URL and remain outside the selected weather TLS probe");
        responseFailure = null;
        request("api.open-meteo.com").getResponseCode();
        if (HttpsProbe.status != 200 || HttpsProbe.failure != null)
            throw new AssertionError("Successful weather must supersede an optional geocoding failure");

        HttpsURLConnection earlier = request("api.bigdatacloud.net");
        request("api.open-meteo.com").getResponseCode();
        responseFailure = new SocketTimeoutException("late optional geocoding timeout");
        failResponse(earlier);
        if (HttpsProbe.failure != null)
            throw new AssertionError("A late optional-geocoding error must not replace the newer weather response");

        HttpsProbe.begin(endpoint);
        responseFailure = null;
        earlier = request("api.open-meteo.com");
        responseFailure = new SSLHandshakeException("fixture untrusted certificate");
        failResponse(request("api.open-meteo.com"));
        responseFailure = null;
        earlier.getResponseCode();
        if (HttpsProbe.status != 200 || !(HttpsProbe.failure instanceof SSLHandshakeException))
            throw new AssertionError("A later rejection must never hide an earlier HTTP response from a negative control");

        responseFailure = null;
        HttpsURLConnection late = request("api.open-meteo.com");
        HttpsProbe.begin(endpoint);
        late.getResponseCode();
        if (HttpsProbe.status != 0) throw new AssertionError("A previous endpoint's late HTTP response must not enter the next control");
        late = request("api.open-meteo.com");
        HttpsProbe.begin(endpoint);
        responseFailure = new SocketTimeoutException("previous endpoint timeout");
        failResponse(late);
        if (HttpsProbe.failure != null) throw new AssertionError("A previous endpoint's late failure must not enter the next control");

        HttpsProbe.begin(endpoint);
        responseFailure = null;
        HttpsURLConnection body = request("api.open-meteo.com");
        body.getResponseCode();
        bodyFailure = new SocketTimeoutException("body failed after HTTP 200");
        try { body.getInputStream().read(); throw new AssertionError("Body fixture must fail"); }
        catch (IOException expected) { }
        if (HttpsProbe.failure != bodyFailure || HttpsProbe.status != 200 || HttpsProbe.bodyComplete)
            throw new AssertionError("A body failure after HTTP 200 must remain visible");
        body = request("api.open-meteo.com");
        body.getResponseCode();
        try { body.getInputStream().read(new byte[1], 0, 1); throw new AssertionError("Buffered body fixture must fail"); }
        catch (IOException expected) { }
        if (HttpsProbe.failure != bodyFailure || HttpsProbe.bodyComplete)
            throw new AssertionError("A buffered body failure must remain visible");

        HttpsProbe.begin(endpoint);
        bodyFailure = null;
        body = request("api.open-meteo.com");
        body.getResponseCode();
        body.getInputStream().read(new byte[1], 0, 1);
        if (!HttpsProbe.bodyComplete || HttpsProbe.failure != null)
            throw new AssertionError("A successfully consumed body must be complete");
        java.io.InputStream lateBody = body.getInputStream();
        java.io.InputStream pendingBody = request("api.open-meteo.com").getInputStream();
        lateBody.read();
        if (HttpsProbe.bodyComplete) throw new AssertionError("An older request's EOF cannot complete the latest body");
        pendingBody.close();
        if (HttpsProbe.bodyComplete) throw new AssertionError("Closing before EOF cannot complete the body");
        HttpsProbe.begin(endpoint);
        pendingBody.read();
        if (HttpsProbe.bodyComplete) throw new AssertionError("An earlier endpoint's EOF cannot complete a new case");
    }
    private static HttpsURLConnection request(String host) throws IOException {
        return (HttpsURLConnection) new URL("https://" + host + "/").openConnection();
    }
    private static void failResponse(HttpsURLConnection connection) throws IOException {
        try { connection.getResponseCode(); throw new AssertionError("The fixture must fail"); }
        catch (IOException expected) { }
    }
}
