package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class PaneCancellationChecks implements java.io.Closeable {
    private final URL endpoint;
    private final ServerSocket listener;
    private final AtomicReference<Socket> peer = new AtomicReference<>();
    private final CountDownLatch accepted = new CountDownLatch(1), closed = new CountDownLatch(1);
    private final Thread server;
    PaneCancellationChecks() throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        endpoint = new URL("https://127.0.0.1:" + listener.getLocalPort() + "/");
        server = new Thread(() -> {
            try {
                Socket socket = listener.accept(); peer.set(socket); accepted.countDown();
                try { while (socket.getInputStream().read() != -1) { } }
                finally { closed.countDown(); socket.close(); }
            } catch (IOException ignored) { }
        });
        server.start();
    }
    void install() throws IOException {
        HttpsProbe.installLoopback(endpoint);
        try { new URL("https://fixture.invalid/").openConnection(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Cancellation fixture must refuse external HTTPS before opening a connection");
    }
    void run(Instrumentation test, Activity activity) throws Exception {
        Button refresh = activity.getWindow().getDecorView().findViewWithTag("weather_refresh");
        Button page = activity.getWindow().getDecorView().findViewWithTag("side_page");
        TextView status = activity.getWindow().getDecorView().findViewWithTag("weather_status");
        test.runOnMainSync(() -> refresh.performClick());
        if (!accepted.await(25, TimeUnit.SECONDS)) throw new AssertionError("Weather must reach the silent TLS peer");
        boolean was24 = activity.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_24_hour", false);
        test.runOnMainSync(() -> ((Button) activity.getWindow().getDecorView().findViewWithTag("time_mode")).performClick());
        if (activity.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_24_hour", false) == was24)
            throw new AssertionError("Clock controls must remain usable during the blocked handshake");
        test.runOnMainSync(() -> page.performClick());
        if (!closed.await(2, TimeUnit.SECONDS)) throw new AssertionError("Hiding weather must close the real transport");
        Bundle witness = new Bundle(); witness.putString("stream", "Silent TLS transport closed after hiding weather\n"); test.sendStatus(0, witness);
        String[] after = new String[1];
        test.runOnMainSync(() -> after[0] = status.getText().toString());
        int updating = activity.getResources().getIdentifier("weather_updating", "string", activity.getPackageName());
        if (updating == 0) throw new AssertionError("Updating label must remain available");
        if (after[0].equals(activity.getString(updating)))
            throw new AssertionError("Cancelled weather must not remain labeled as updating");
        SystemClock.sleep(300); test.waitForIdleSync();
        String[] later = new String[1];
        test.runOnMainSync(() -> later[0] = status.getText().toString());
        if (!after[0].equals(later[0])) throw new AssertionError("Late cancelled completion must not overwrite the pane");
        test.runOnMainSync(() -> page.performClick());
        if (!refresh.isEnabled()) throw new AssertionError("Weather must allow a new refresh after returning");
    }
    @Override public void close() throws IOException {
        listener.close();
        Socket socket = peer.get(); if (socket != null) socket.close();
        try { server.join(2000); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        if (server.isAlive()) throw new AssertionError("Silent peer fixture must stop");
    }
}
