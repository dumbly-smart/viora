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
        // Android checks signature permission ownership when the same package
        // redeclares AndroidX's non-exported receiver permission after rotation.
        var capabilities = new SigningCertificateLineage.SignerCapabilities.Builder()
            .setInstalledData(true).setSharedUid(false).setPermission(true).setAuth(false).setRollback(false).build();
        new SigningCertificateLineage.Builder(oldSigner, newSigner).setOriginalCapabilities(capabilities)
            .setMinSdkVersion(28).build().writeToFile(new File(args[1]));
        System.out.println("Created public rotation proof; no private key exported.");
    }
}
