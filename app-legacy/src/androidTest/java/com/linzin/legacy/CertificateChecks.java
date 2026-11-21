package com.linzin.legacy;

import android.content.Context;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class CertificateChecks {
    static void run(Context context, Context testContext) throws Exception {
        KeyStore roots = KeyStore.getInstance("BKS", new BouncyCastleProvider());
        try (InputStream input = context.getAssets().open("mozilla-roots.bks")) { roots.load(input, new char[0]); }
        int count = 0;
        for (Enumeration<String> aliases = roots.aliases(); aliases.hasMoreElements();) {
            X509Certificate certificate = (X509Certificate) roots.getCertificate(aliases.nextElement());
            try {
                if (certificate.getPublicKey() == null) throw new AssertionError("Missing public key");
            } catch (Throwable error) {
                throw new RuntimeException("Bundled root public key: " + certificate.getSubjectX500Principal(), error);
            }
            count++;
        }
        if (count != 118) throw new AssertionError("Unexpected root count: " + count);
        try (InputStream input = testContext.getAssets().open("open-meteo-chain.pem")) {
            java.security.cert.CertificateFactory parser = java.security.cert.CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
            java.util.Collection<? extends java.security.cert.Certificate> chain = parser.generateCertificates(input);
            for (java.security.cert.Certificate certificate : chain) {
                X509Certificate x509 = (X509Certificate) certificate;
                try { x509.getPublicKey(); }
                catch (Throwable error) { throw new RuntimeException("Frozen server chain key: " + x509.getSubjectX500Principal(), error); }
            }
            javax.net.ssl.TrustManagerFactory trust = javax.net.ssl.TrustManagerFactory.getInstance("PKIX",
                new org.bouncycastle.jsse.provider.BouncyCastleJsseProvider(new BouncyCastleProvider()));
            java.security.cert.PKIXBuilderParameters parameters = new java.security.cert.PKIXBuilderParameters(
                roots, new java.security.cert.X509CertSelector());
            parameters.setDate(new java.util.Date(1794848400000L)); // 2026-11-16 17:00 UTC, within the frozen chain's validity.
            parameters.setRevocationEnabled(false);
            trust.init(new javax.net.ssl.CertPathTrustManagerParameters(parameters));
            javax.net.ssl.X509TrustManager manager = (javax.net.ssl.X509TrustManager) trust.getTrustManagers()[0];
            X509Certificate[] certificates = chain.toArray(new X509Certificate[0]);
            for (int pass = 0; pass < 5; pass++) manager.checkServerTrusted(certificates, "ECDHE_ECDSA");
        }
    }
}
