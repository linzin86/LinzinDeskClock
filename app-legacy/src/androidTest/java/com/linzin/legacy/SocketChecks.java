package com.linzin.legacy;

import android.content.Context;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocketFactory;

final class SocketChecks {
    static void run(Context context) throws Exception {
        SSLSocketFactory factory = LegacyTls.factory(context);
        silentPeer(factory, false);
        silentPeer(factory, true);
    }
    private static void silentPeer(SSLSocketFactory factory, boolean explicitCancel) throws Exception {
        CountDownLatch accepted = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicReference<Socket> peer = new AtomicReference<>();
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread server = new Thread(() -> {
            try {
                Socket socket = listener.accept();
                peer.set(socket);
                accepted.countDown();
                while (socket.getInputStream().read() != -1) { }
                closed.countDown();
            } catch (IOException ignored) { }
        });
        server.start();
        LegacyHttps request = new LegacyHttps(factory, explicitCancel ? 10000 : 500);
        FutureTask<Boolean> job = new FutureTask<>(() -> {
            try {
                request.get(new URL("https://127.0.0.1:" + listener.getLocalPort() + "/"));
                return false;
            } catch (IOException expected) { return true; }
        });
        new Thread(job, "legacy-silent-peer-test").start();
        try {
            if (!accepted.await(2, TimeUnit.SECONDS)) throw new AssertionError("TLS must reach the silent peer");
            if (explicitCancel) request.cancel();
            if (!job.get(3, TimeUnit.SECONDS)) throw new AssertionError("Silent TLS must fail");
            if (!closed.await(1, TimeUnit.SECONDS)) throw new AssertionError("Cancellation must close the real transport");
        } finally {
            request.cancel();
            listener.close();
            Socket socket = peer.get();
            if (socket != null) socket.close();
            job.cancel(true);
            server.join(2000);
        }
    }
}
