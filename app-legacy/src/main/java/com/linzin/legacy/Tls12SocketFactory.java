package com.linzin.legacy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final java.util.concurrent.atomic.AtomicReference<Socket> transport = new java.util.concurrent.atomic.AtomicReference<>();
    private volatile boolean cancelled;
    Tls12SocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }
    void cancel() {
        cancelled = true;
        Socket socket = transport.getAndSet(null);
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
    private Socket wrap(Socket socket) {
        ((SSLSocket) socket).setEnabledProtocols(new String[] { "TLSv1.2" });
        if (socket instanceof org.bouncycastle.jsse.BCSSLSocket) {
            org.bouncycastle.jsse.BCSSLSocket bc = (org.bouncycastle.jsse.BCSSLSocket) socket;
            org.bouncycastle.jsse.BCSSLParameters parameters = bc.getParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            bc.setParameters(parameters);
        }
        return new ProtocolSocket((SSLSocket) socket);
    }
    private void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            cancel();
            throw new InterruptedIOException("Request cancelled");
        }
    }
    public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
    public Socket createSocket() throws IOException { checkCancelled(); return wrap(delegate.createSocket()); }
    public Socket createSocket(Socket socket, String host, int port, boolean close) throws IOException {
        transport.set(socket);
        checkCancelled();
        Socket wrapped = wrap(delegate.createSocket(socket, host, port, close));
        checkCancelled();
        return wrapped;
    }
    public Socket createSocket(String host, int port) throws IOException { checkCancelled(); return wrap(delegate.createSocket(host, port)); }
    public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { checkCancelled(); return wrap(delegate.createSocket(host, port, local, localPort)); }
    public Socket createSocket(InetAddress host, int port) throws IOException { checkCancelled(); return wrap(delegate.createSocket(host, port)); }
    public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { checkCancelled(); return wrap(delegate.createSocket(host, port, local, localPort)); }

    private static final class ProtocolSocket extends SSLSocket {
        private final SSLSocket actual;
        ProtocolSocket(SSLSocket actual) { this.actual = actual; }
        public String[] getSupportedProtocols() { return new String[] { "TLSv1.2" }; }
        public String[] getEnabledProtocols() { return getSupportedProtocols(); }
        public void setEnabledProtocols(String[] protocols) {
            if (protocols == null) throw new IllegalArgumentException("Protocols are required");
            // Old HttpConnection/OkHttp can force SSLv3; keep the secure protocol policy.
            actual.setEnabledProtocols(new String[] { "TLSv1.2" });
        }
        public String[] getSupportedCipherSuites() { return actual.getSupportedCipherSuites(); }
        public String[] getEnabledCipherSuites() { return actual.getEnabledCipherSuites(); }
        public void setEnabledCipherSuites(String[] suites) { actual.setEnabledCipherSuites(suites); }
        public SSLSession getSession() { return actual.getSession(); }
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { actual.addHandshakeCompletedListener(listener); }
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { actual.removeHandshakeCompletedListener(listener); }
        public void startHandshake() throws IOException { actual.startHandshake(); }
        public void setUseClientMode(boolean enabled) { actual.setUseClientMode(enabled); }
        public boolean getUseClientMode() { return actual.getUseClientMode(); }
        public void setNeedClientAuth(boolean enabled) { actual.setNeedClientAuth(enabled); }
        public boolean getNeedClientAuth() { return actual.getNeedClientAuth(); }
        public void setWantClientAuth(boolean enabled) { actual.setWantClientAuth(enabled); }
        public boolean getWantClientAuth() { return actual.getWantClientAuth(); }
        public void setEnableSessionCreation(boolean enabled) { actual.setEnableSessionCreation(enabled); }
        public boolean getEnableSessionCreation() { return actual.getEnableSessionCreation(); }
        public void connect(SocketAddress address) throws IOException { actual.connect(address); }
        public void connect(SocketAddress address, int timeout) throws IOException { actual.connect(address, timeout); }
        public void bind(SocketAddress address) throws IOException { actual.bind(address); }
        public InetAddress getInetAddress() { return actual.getInetAddress(); }
        public InetAddress getLocalAddress() { return actual.getLocalAddress(); }
        public int getPort() { return actual.getPort(); }
        public int getLocalPort() { return actual.getLocalPort(); }
        public SocketAddress getRemoteSocketAddress() { return actual.getRemoteSocketAddress(); }
        public SocketAddress getLocalSocketAddress() { return actual.getLocalSocketAddress(); }
        public InputStream getInputStream() throws IOException { return actual.getInputStream(); }
        public OutputStream getOutputStream() throws IOException { return actual.getOutputStream(); }
        public void setSoTimeout(int timeout) throws SocketException { actual.setSoTimeout(timeout); }
        public int getSoTimeout() throws SocketException { return actual.getSoTimeout(); }
        public void setTcpNoDelay(boolean enabled) throws SocketException { actual.setTcpNoDelay(enabled); }
        public boolean getTcpNoDelay() throws SocketException { return actual.getTcpNoDelay(); }
        public void setKeepAlive(boolean enabled) throws SocketException { actual.setKeepAlive(enabled); }
        public boolean getKeepAlive() throws SocketException { return actual.getKeepAlive(); }
        public void setSoLinger(boolean enabled, int seconds) throws SocketException { actual.setSoLinger(enabled, seconds); }
        public int getSoLinger() throws SocketException { return actual.getSoLinger(); }
        public void setSendBufferSize(int size) throws SocketException { actual.setSendBufferSize(size); }
        public int getSendBufferSize() throws SocketException { return actual.getSendBufferSize(); }
        public void setReceiveBufferSize(int size) throws SocketException { actual.setReceiveBufferSize(size); }
        public int getReceiveBufferSize() throws SocketException { return actual.getReceiveBufferSize(); }
        public void setReuseAddress(boolean enabled) throws SocketException { actual.setReuseAddress(enabled); }
        public boolean getReuseAddress() throws SocketException { return actual.getReuseAddress(); }
        public void setOOBInline(boolean enabled) throws SocketException { actual.setOOBInline(enabled); }
        public boolean getOOBInline() throws SocketException { return actual.getOOBInline(); }
        public void setTrafficClass(int value) throws SocketException { actual.setTrafficClass(value); }
        public int getTrafficClass() throws SocketException { return actual.getTrafficClass(); }
        public void sendUrgentData(int value) throws IOException { actual.sendUrgentData(value); }
        public void shutdownInput() throws IOException { actual.shutdownInput(); }
        public void shutdownOutput() throws IOException { actual.shutdownOutput(); }
        public boolean isConnected() { return actual.isConnected(); }
        public boolean isBound() { return actual.isBound(); }
        public boolean isClosed() { return actual.isClosed(); }
        public boolean isInputShutdown() { return actual.isInputShutdown(); }
        public boolean isOutputShutdown() { return actual.isOutputShutdown(); }
        public void close() throws IOException { actual.close(); }
    }
}
