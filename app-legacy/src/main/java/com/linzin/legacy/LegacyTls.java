package com.linzin.legacy;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

final class LegacyTls {
    private static SSLSocketFactory factory;

    static synchronized SSLSocketFactory factory(Context context) throws GeneralSecurityException, IOException {
        if (factory == null) {
            Provider crypto = new BouncyCastleProvider();
            Provider jsse = new BouncyCastleJsseProvider(crypto);
            KeyStore roots = KeyStore.getInstance("BKS", crypto);
            try (InputStream input = context.getAssets().open("mozilla-roots.bks")) { roots.load(input, new char[0]); }
            TrustManagerFactory trust = TrustManagerFactory.getInstance("PKIX", jsse);
            trust.init(roots);
            SSLContext tls = SSLContext.getInstance("TLS", jsse);
            tls.init(null, trust.getTrustManagers(), new KernelSecureRandom());
            SSLSocketFactory sockets = tls.getSocketFactory();
            factory = sockets;
        }
        return factory;
    }
}
