package com.linzin.legacy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tls12SocketFactoryTest {
    private static final class TestSocket extends SSLSocket {
        String[] protocols = { "TLSv1.2" };
        boolean closed;
        int timeout;
        public String[] getSupportedProtocols() { return new String[] { "SSLv3", "TLSv1", "TLSv1.2" }; }
        public String[] getEnabledProtocols() { return protocols; }
        public void setEnabledProtocols(String[] protocols) { this.protocols = protocols; }
        public String[] getSupportedCipherSuites() { return new String[] { "fixture" }; }
        public String[] getEnabledCipherSuites() { return new String[] { "fixture" }; }
        public void setEnabledCipherSuites(String[] suites) { }
        public SSLSession getSession() { return null; }
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { }
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { }
        public void startHandshake() { }
        public void setUseClientMode(boolean enabled) { }
        public boolean getUseClientMode() { return true; }
        public void setNeedClientAuth(boolean enabled) { }
        public boolean getNeedClientAuth() { return false; }
        public void setWantClientAuth(boolean enabled) { }
        public boolean getWantClientAuth() { return false; }
        public void setEnableSessionCreation(boolean enabled) { }
        public boolean getEnableSessionCreation() { return true; }
        public void setSoTimeout(int timeout) { this.timeout = timeout; }
        public int getSoTimeout() { return timeout; }
        public void close() { closed = true; }
    }
    private static class Factory extends SSLSocketFactory {
        final TestSocket socket = new TestSocket();
        public String[] getDefaultCipherSuites() { return new String[] { "fixture" }; }
        public String[] getSupportedCipherSuites() { return getDefaultCipherSuites(); }
        public Socket createSocket() { return socket; }
        public Socket createSocket(Socket s, String h, int p, boolean c) { return socket; }
        public Socket createSocket(String h, int p) { return socket; }
        public Socket createSocket(String h, int p, InetAddress l, int q) { return socket; }
        public Socket createSocket(InetAddress h, int p) { return socket; }
        public Socket createSocket(InetAddress h, int p, InetAddress l, int q) { return socket; }
    }

    @Test public void platformSsl3FallbackCannotDisableTls12() throws Exception {
        Factory delegate = new Factory();
        SSLSocket socket = (SSLSocket) new Tls12SocketFactory(delegate).createSocket();
        socket.setEnabledProtocols(new String[] { "SSLv3" });
        assertArrayEquals(new String[] { "TLSv1.2" }, delegate.socket.getEnabledProtocols());
    }

    @Test public void timeoutsAndClosingReachTheUnderlyingSocket() throws Exception {
        Factory delegate = new Factory();
        Socket socket = new Tls12SocketFactory(delegate).createSocket();
        socket.setSoTimeout(1234);
        socket.close();
        assertEquals(1234, delegate.socket.timeout);
        assertTrue(delegate.socket.closed);
    }

    @Test public void cancellationCannotStartAnotherSocket() throws Exception {
        Thread.currentThread().interrupt();
        try {
            new Tls12SocketFactory(new Factory()).createSocket();
            fail("Cancelled request must not create another socket");
        } catch (java.io.InterruptedIOException expected) {
        } finally { Thread.interrupted(); }
    }

    @Test public void bcSocketsVerifyHttpsPeerDuringHandshake() throws Exception {
        java.security.Provider crypto = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS",
            new org.bouncycastle.jsse.provider.BouncyCastleJsseProvider(crypto));
        context.init(null, null, new java.security.SecureRandom());
        SSLSocket actual = (SSLSocket) context.getSocketFactory().createSocket();
        SSLSocketFactory delegate = new Factory() {
            @Override public Socket createSocket() { return actual; }
        };
        Socket wrapped = new Tls12SocketFactory(delegate).createSocket();
        try {
            assertEquals("HTTPS", ((org.bouncycastle.jsse.BCSSLSocket) actual).getParameters()
                .getEndpointIdentificationAlgorithm());
        } finally { wrapped.close(); }
    }

    @Test public void cancellationClosesRawTransportEvenWhenTlsCloseIsBlocked() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        Socket transport = new Socket() {
            @Override public void close() { closed.set(true); }
        };
        Tls12SocketFactory factory = new Tls12SocketFactory(new Factory());
        factory.createSocket(transport, "fixture.invalid", 443, true);
        factory.cancel();
        assertTrue("Raw transport must close without acquiring the TLS monitor", closed.get());
        try { factory.createSocket(); fail("Cancelled factory must reject retries"); }
        catch (java.io.InterruptedIOException expected) { }
    }
}
