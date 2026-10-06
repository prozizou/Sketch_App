import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

/**
 * CI diagnostic for the release signing secrets. Does the same calls as the Android Gradle plugin
 * (load the store with the store password, then read the key with the key password) and reports which
 * secret is wrong. It never prints a secret value. Run with: java .github/workflows/CheckSigning.java
 */
public class CheckSigning {
    public static void main(String[] args) throws Exception {
        String file = System.getenv("RELEASE_KEYSTORE_FILE");
        String storePassword = System.getenv("RELEASE_STORE_PASSWORD");
        String alias = System.getenv("RELEASE_KEY_ALIAS");
        String keyPassword = System.getenv("RELEASE_KEY_PASSWORD");

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = new FileInputStream(file)) {
            keyStore.load(in, storePassword.toCharArray());
        } catch (Exception e) {
            System.out.println("::error::RELEASE_STORE_PASSWORD does not open the keystore, or RELEASE_KEYSTORE_BASE64 is not a valid keystore.");
            return;
        }
        System.out.println("OK: RELEASE_STORE_PASSWORD opens the keystore (" + keyStore.size() + " entries).");

        if (!keyStore.containsAlias(alias)) {
            System.out.println("::error::RELEASE_KEY_ALIAS was not found in the keystore. Check spelling and spaces.");
            return;
        }
        System.out.println("OK: RELEASE_KEY_ALIAS exists in the keystore.");

        try {
            Key key = keyStore.getKey(alias, keyPassword.toCharArray());
            if (key == null) {
                System.out.println("::error::The alias exists but has no private key.");
            } else {
                System.out.println("OK: RELEASE_KEY_PASSWORD unlocks the key. Signing should work.");
            }
        } catch (UnrecoverableKeyException e) {
            System.out.println("::error::RELEASE_KEY_PASSWORD does not unlock the key (store password and alias are fine).");
        }
    }
}
