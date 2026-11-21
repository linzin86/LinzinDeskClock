import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class FreezeRoots {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Use: FreezeRoots input.pem output.bks");
        KeyStore store = KeyStore.getInstance("BKS", new BouncyCastleProvider());
        store.load(null, new char[0]);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        int count = 0;
        try (InputStream input = new FileInputStream(args[0])) {
            for (Certificate certificate : factory.generateCertificates(input)) {
                if (((X509Certificate) certificate).getBasicConstraints() < 0)
                    throw new IllegalArgumentException("Trust bundle contains a non-CA certificate");
                store.setCertificateEntry("mozilla-" + count++, certificate);
            }
        }
        if (count == 0) throw new IllegalArgumentException("Trust bundle is empty");
        try (OutputStream output = new FileOutputStream(args[1])) { store.store(output, new char[0]); }
        System.out.println("Frozen Mozilla roots: " + count);
    }
}
