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
    static volatile boolean bodyComplete;
    static volatile boolean cleartext;
    static volatile int requests;
    static volatile String peer;
    static volatile String socketHost;
    static final StringBuffer outcomes = new StringBuffer();
    private static volatile int generation;

    static synchronized void begin(URL endpoint) { generation++; target = endpoint; failure = null; status = 0; protocol = null; bodyComplete = false; peer = null; socketHost = null; outcomes.setLength(0); }

    static void install(URL endpoint) {
        install(endpoint, true);
    }

    static void installLoopback(URL endpoint) {
        if (!"https".equals(endpoint.getProtocol()) || !"127.0.0.1".equals(endpoint.getHost()))
            throw new IllegalArgumentException("An HTTPS loopback endpoint is required");
        install(endpoint, false);
    }

    private static void install(URL endpoint, boolean forwardOtherHosts) {
        begin(endpoint);
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) throws IOException {
                if (!"https".equals(url.getProtocol())) { cleartext = true; throw new IOException("Cleartext is forbidden"); }
                if (!"api.open-meteo.com".equals(url.getHost())) {
                    if (!forwardOtherHosts) throw new IOException("External HTTPS is disabled for the loopback fixture");
                    // A scheme-relative URL retains the original handler instead of re-entering our factory.
                    return new URL(target, url.toExternalForm().substring("https:".length())).openConnection();
                }
                final int index, ticket;
                final URL endpoint;
                synchronized (HttpsProbe.class) { index = ++requests; ticket = generation; endpoint = target; bodyComplete = false; }
                return new ObservedConnection(url, (HttpsURLConnection) endpoint.openConnection(), index, ticket);
            }
        });
    }

    private static final class ObservedConnection extends HttpsURLConnection {
        private final HttpsURLConnection actual;
        private final int index, ticket;
        private final long started = System.nanoTime();
        ObservedConnection(URL url, HttpsURLConnection actual, int index, int ticket) {
            super(url); this.actual = actual; this.index = index; this.ticket = ticket;
        }
        public void connect() throws IOException { actual.connect(); }
        public void disconnect() { actual.disconnect(); }
        public boolean usingProxy() { return actual.usingProxy(); }
        public String getCipherSuite() { return actual.getCipherSuite(); }
        public Certificate[] getLocalCertificates() { return actual.getLocalCertificates(); }
        public Certificate[] getServerCertificates() throws javax.net.ssl.SSLPeerUnverifiedException { return actual.getServerCertificates(); }
        public void setSSLSocketFactory(SSLSocketFactory factory) { actual.setSSLSocketFactory(new ObservedFactory(factory, ticket)); }
        public SSLSocketFactory getSSLSocketFactory() { return actual.getSSLSocketFactory(); }
        public void setHostnameVerifier(HostnameVerifier verifier) { actual.setHostnameVerifier(verifier); }
        public HostnameVerifier getHostnameVerifier() { return actual.getHostnameVerifier(); }
        public void setConnectTimeout(int timeout) { actual.setConnectTimeout(timeout); }
        public void setReadTimeout(int timeout) { actual.setReadTimeout(timeout); }
        public void setInstanceFollowRedirects(boolean follow) { actual.setInstanceFollowRedirects(follow); }
        public void setRequestProperty(String key, String value) { actual.setRequestProperty(key, value); }
        public int getResponseCode() throws IOException {
            try {
                int code = actual.getResponseCode(); record("HTTP " + code);
                synchronized (HttpsProbe.class) {
                    if (generation == ticket) { status = code; if (index == requests) failure = null; }
                }
                return code;
            } catch (IOException | RuntimeException error) {
                record(error.toString()); failed(error); throw error;
            }
        }
        public InputStream getInputStream() throws IOException {
            try {
                return new java.io.FilterInputStream(actual.getInputStream()) {
                    public int read() throws IOException {
                        try { return observed(in.read()); }
                        catch (IOException | RuntimeException error) { failed(error); throw error; }
                    }
                    public int read(byte[] buffer, int offset, int count) throws IOException {
                        try { return observed(in.read(buffer, offset, count)); }
                        catch (IOException | RuntimeException error) { failed(error); throw error; }
                    }
                    public void close() throws IOException {
                        try { super.close(); }
                        catch (IOException | RuntimeException error) { failed(error); throw error; }
                    }
                    private int observed(int count) {
                        synchronized (HttpsProbe.class) {
                            if (count == -1 && generation == ticket && index == requests && failure == null) bodyComplete = true;
                        }
                        return count;
                    }
                };
            }
            catch (IOException | RuntimeException error) { record(error.toString()); failed(error); throw error; }
        }
        private void failed(Throwable error) {
            synchronized (HttpsProbe.class) {
                if (generation == ticket && index == requests) { failure = error; bodyComplete = false; }
            }
        }
        private void record(String event) {
            outcomes.append("request=" + index + " generation=" + ticket + " source=" + url.getHost()
                + " target=" + actual.getURL().getHost() + " elapsedMs=" + (System.nanoTime() - started) / 1_000_000
                + " " + event + "\n");
        }
    }

    private static final class ObservedFactory extends SSLSocketFactory {
        private final SSLSocketFactory actual;
        private final int ticket;
        ObservedFactory(SSLSocketFactory actual, int ticket) { this.actual = actual; this.ticket = ticket; }
        private Socket observe(Socket socket) {
            ((SSLSocket) socket).addHandshakeCompletedListener(event -> {
                synchronized (HttpsProbe.class) {
                    if (generation == ticket) {
                        protocol = event.getSession().getProtocol();
                        try { peer = event.getSession().getPeerPrincipal().getName(); }
                        catch (javax.net.ssl.SSLPeerUnverifiedException error) { peer = error.toString(); }
                    }
                }
            });
            return socket;
        }
        public String[] getDefaultCipherSuites() { return actual.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return actual.getSupportedCipherSuites(); }
        public Socket createSocket() throws IOException { return observe(actual.createSocket()); }
        public Socket createSocket(Socket socket, String host, int port, boolean close) throws IOException {
            synchronized (HttpsProbe.class) { if (generation == ticket) socketHost = host; }
            return observe(actual.createSocket(socket, host, port, close));
        }
        public Socket createSocket(String host, int port) throws IOException { return observe(actual.createSocket(host, port)); }
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return observe(actual.createSocket(host, port, local, localPort)); }
        public Socket createSocket(InetAddress host, int port) throws IOException { return observe(actual.createSocket(host, port)); }
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { return observe(actual.createSocket(host, port, local, localPort)); }
    }
}
