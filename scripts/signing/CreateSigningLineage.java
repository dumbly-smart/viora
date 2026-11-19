import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.KeyConfig;
import java.io.*;
import java.security.*;
import java.security.cert.*;

public class CreateSigningLineage {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance(new File(System.getenv("VIORA_OLD_KEYSTORE_PATH")), System.getenv("VIORA_OLD_KEYSTORE_PASSWORD").toCharArray());
        PrivateKey key = (PrivateKey) store.getKey(System.getenv("VIORA_OLD_KEY_ALIAS"), System.getenv("VIORA_OLD_KEY_PASSWORD").toCharArray());
        X509Certificate oldCert = (X509Certificate) store.getCertificate(System.getenv("VIORA_OLD_KEY_ALIAS"));
        X509Certificate newCert;
        try (InputStream input = new FileInputStream(args[0])) {
            newCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        var oldSigner = new SigningCertificateLineage.SignerConfig.Builder(key, oldCert).build();
        var newSigner = new SigningCertificateLineage.SignerConfig.Builder((KeyConfig) null, newCert).build();
        var capabilities = new SigningCertificateLineage.SignerCapabilities.Builder()
            .setInstalledData(true).setSharedUid(false).setPermission(false).setAuth(false).setRollback(false).build();
        new SigningCertificateLineage.Builder(oldSigner, newSigner).setOriginalCapabilities(capabilities)
            .setMinSdkVersion(28).build().writeToFile(new File(args[1]));
        System.out.println("Created public rotation proof; no private key exported.");
    }
}
