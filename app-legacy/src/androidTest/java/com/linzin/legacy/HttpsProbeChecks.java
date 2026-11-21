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
    }
    private static HttpsURLConnection request(String host) throws IOException {
        return (HttpsURLConnection) new URL("https://" + host + "/").openConnection();
    }
    private static void failResponse(HttpsURLConnection connection) throws IOException {
        try { connection.getResponseCode(); throw new AssertionError("The fixture must fail"); }
        catch (IOException expected) { }
    }
}
