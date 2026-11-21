package com.linzin.legacy;

import android.content.Context;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.jsse.BCSSLParameters;
import org.bouncycastle.jsse.BCSSLSocket;

final class DirectTlsChecks {
    static String run(Context context, String protocol) throws Exception {
        long began = android.os.SystemClock.elapsedRealtime();
        try (SSLSocket socket = (SSLSocket) LegacyTls.factory(context).createSocket()) {
            BCSSLSocket bc = (BCSSLSocket) socket;
            bc.setHost("api.open-meteo.com");
            BCSSLParameters parameters = bc.getParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS"); bc.setParameters(parameters);
            socket.setEnabledProtocols(new String[] { protocol });
            socket.setSoTimeout(15000);
            socket.connect(new InetSocketAddress("api.open-meteo.com", 443), 15000);
            socket.startHandshake();
            return "OK: direct provider " + socket.getSession().getProtocol() + " peer=" + socket.getSession().getPeerPrincipal()
                + " elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - began);
        }
    }
}
