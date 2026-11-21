package com.linzin.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;

final class HttpsProbe {
    static volatile Throwable failure;
    static volatile URL target;
    static volatile int status;
    static volatile String protocol;
    static volatile boolean cleartext;
    static volatile int requests;
    static volatile String peer;
    private static volatile int generation;

    static void begin(URL endpoint) { generation++; target = endpoint; failure = null; status = 0; protocol = null; peer = null; }

    static void install(URL endpoint) {
        begin(endpoint);
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) throws IOException {
                if (!"https".equals(url.getProtocol())) { cleartext = true; throw new IOException("Cleartext is forbidden"); }
                requests++;
                return new ObservedConnection(url, (HttpsURLConnection) target.openConnection());
            }
        });
    }

    private static final class ObservedConnection extends HttpsURLConnection {
        private final HttpsURLConnection actual;
        ObservedConnection(URL url, HttpsURLConnection actual) { super(url); this.actual = actual; }
        public void connect() throws IOException { actual.connect(); }
        public void disconnect() { actual.disconnect(); }
        public boolean usingProxy() { return actual.usingProxy(); }
        public String getCipherSuite() { return actual.getCipherSuite(); }
        public Certificate[] getLocalCertificates() { return actual.getLocalCertificates(); }
        public Certificate[] getServerCertificates() throws javax.net.ssl.SSLPeerUnverifiedException { return actual.getServerCertificates(); }
        public void setSSLSocketFactory(SSLSocketFactory factory) { actual.setSSLSocketFactory(new ObservedFactory(factory, generation)); }
        public SSLSocketFactory getSSLSocketFactory() { return actual.getSSLSocketFactory(); }
        public void setHostnameVerifier(HostnameVerifier verifier) { actual.setHostnameVerifier(verifier); }
        public HostnameVerifier getHostnameVerifier() { return actual.getHostnameVerifier(); }
        public void setConnectTimeout(int timeout) { actual.setConnectTimeout(timeout); }
        public void setReadTimeout(int timeout) { actual.setReadTimeout(timeout); }
        public void setInstanceFollowRedirects(boolean follow) { actual.setInstanceFollowRedirects(follow); }
        public void setRequestProperty(String key, String value) { actual.setRequestProperty(key, value); }
        public int getResponseCode() throws IOException {
            try { int code = actual.getResponseCode(); status = code; return code; }
            catch (IOException | RuntimeException error) { failure = error; throw error; }
        }
        public InputStream getInputStream() throws IOException {
            try { return actual.getInputStream(); }
            catch (IOException | RuntimeException error) { failure = error; throw error; }
        }
    }

    private static final class ObservedFactory extends SSLSocketFactory {
        private final SSLSocketFactory actual;
        private final int ticket;
        ObservedFactory(SSLSocketFactory actual, int ticket) { this.actual = actual; this.ticket = ticket; }
        private Socket observe(Socket socket) {
            ((SSLSocket) socket).addHandshakeCompletedListener(event -> {
                if (generation == ticket) {
                    protocol = event.getSession().getProtocol();
                    try { peer = event.getSession().getPeerPrincipal().getName(); }
                    catch (javax.net.ssl.SSLPeerUnverifiedException error) { peer = error.toString(); }
                }
            });
            return socket;
        }
        public String[] getDefaultCipherSuites() { return actual.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return actual.getSupportedCipherSuites(); }
        public Socket createSocket() throws IOException { return observe(actual.createSocket()); }
        public Socket createSocket(Socket socket, String host, int port, boolean close) throws IOException { return observe(actual.createSocket(socket, host, port, close)); }
        public Socket createSocket(String host, int port) throws IOException { return observe(actual.createSocket(host, port)); }
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return observe(actual.createSocket(host, port, local, localPort)); }
        public Socket createSocket(InetAddress host, int port) throws IOException { return observe(actual.createSocket(host, port)); }
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { return observe(actual.createSocket(host, port, local, localPort)); }
    }
}
