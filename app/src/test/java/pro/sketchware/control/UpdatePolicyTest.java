package pro.sketchware.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UpdatePolicyTest {
    private static final String PKG = "com.sketch_nws";
    private static final String CERT_A = UpdatePolicy.signerDigest("certificate-A".getBytes(StandardCharsets.UTF_8));
    private static final String CERT_B = UpdatePolicy.signerDigest("certificate-B".getBytes(StandardCharsets.UTF_8));

    private static Set<String> signers(String... digests) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, digests);
        return set;
    }

    /* ---- download links ---- */

    @Test
    public void onlyHttpsLinksAreAccepted() {
        assertTrue(UpdatePolicy.isSecureUrl("https://github.com/prozizou/Sketch_App/releases/download/v7.1.1/app.apk"));
        assertTrue(UpdatePolicy.isSecureUrl("HTTPS://example.com/a.apk"));
        assertTrue(UpdatePolicy.isSecureUrl("  https://example.com/a.apk  "));
    }

    @Test
    public void insecureOrOddLinksAreRejected() {
        assertFalse(UpdatePolicy.isSecureUrl(null));
        assertFalse(UpdatePolicy.isSecureUrl(""));
        assertFalse(UpdatePolicy.isSecureUrl("http://example.com/a.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("ftp://example.com/a.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("file:///sdcard/a.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("content://media/external/a.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("javascript:alert(1)"));
        assertFalse(UpdatePolicy.isSecureUrl("https:///no-host.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("https://"));
        assertFalse(UpdatePolicy.isSecureUrl("//example.com/a.apk"));
        assertFalse(UpdatePolicy.isSecureUrl("https://exa mple.com/a.apk"));
    }

    /* ---- checksum ---- */

    @Test
    public void sha256FormatIsValidated() {
        assertTrue(UpdatePolicy.isValidSha256("a".repeat(64)));
        assertTrue(UpdatePolicy.isValidSha256("  " + "A".repeat(64) + "\n"));
        assertFalse(UpdatePolicy.isValidSha256(null));
        assertFalse(UpdatePolicy.isValidSha256(""));
        assertFalse(UpdatePolicy.isValidSha256("a".repeat(63)));
        assertFalse(UpdatePolicy.isValidSha256("a".repeat(65)));
        assertFalse(UpdatePolicy.isValidSha256("g".repeat(64)));
    }

    @Test
    public void checksumMustMatchTheDownloadedBytes() throws Exception {
        byte[] apk = "pretend this is an apk".getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(apk);
        String hex = UpdatePolicy.toHex(digest);

        assertEquals(64, hex.length());
        assertTrue(UpdatePolicy.checksumMatches(digest, hex));
        assertTrue(UpdatePolicy.checksumMatches(digest, hex.toUpperCase()));
        assertTrue(UpdatePolicy.checksumMatches(digest, " " + hex + " "));

        byte[] tampered = MessageDigest.getInstance("SHA-256").digest("pretend this is a hacked apk".getBytes(StandardCharsets.UTF_8));
        assertFalse(UpdatePolicy.checksumMatches(tampered, hex));
    }

    @Test
    public void missingOrMalformedChecksumNeverMatches() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(new byte[0]);
        assertFalse(UpdatePolicy.checksumMatches(digest, null));
        assertFalse(UpdatePolicy.checksumMatches(digest, ""));
        assertFalse(UpdatePolicy.checksumMatches(digest, "not-a-checksum"));
    }

    @Test
    public void signerDigestMatchesKnownSha256() {
        // SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UpdatePolicy.signerDigest("abc".getBytes(StandardCharsets.UTF_8)));
    }

    /* ---- versions ---- */

    @Test
    public void onlyAHigherVersionCodeIsNewer() {
        assertTrue(UpdatePolicy.isNewer(152, 151));
        assertFalse(UpdatePolicy.isNewer(151, 151));
        assertFalse(UpdatePolicy.isNewer(150, 151));
        assertTrue(UpdatePolicy.isNewer(Integer.MAX_VALUE + 1L, Integer.MAX_VALUE));
    }

    /* ---- the APK itself: package, version, signing key ---- */

    @Test
    public void newerApkSignedWithTheSameKeyIsAccepted() {
        assertNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(CERT_A), signers(CERT_A)));
    }

    @Test
    public void unreadableApkIsRejected() {
        assertNotNull(UpdatePolicy.rejectionReason(PKG, null, 151, 0, signers(CERT_A), signers()));
    }

    @Test
    public void apkOfAnotherAppIsRejected() {
        String reason = UpdatePolicy.rejectionReason(PKG, "com.evil.app", 151, 999, signers(CERT_A), signers(CERT_A));
        assertNotNull(reason);
        assertTrue(reason.contains("another app"));
    }

    @Test
    public void sameOrOlderVersionIsRejected() {
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 151, signers(CERT_A), signers(CERT_A)));
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 100, signers(CERT_A), signers(CERT_A)));
    }

    @Test
    public void apkSignedWithAnotherKeyIsRejected() {
        String reason = UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(CERT_A), signers(CERT_B));
        assertNotNull(reason);
        assertTrue(reason.contains("different key"));
    }

    @Test
    public void apkWithoutSignaturesIsRejected() {
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(CERT_A), signers()));
    }

    @Test
    public void apkSignedWithAnExtraKeyIsRejected() {
        // The whole set of signers has to be identical, not merely overlap.
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(CERT_A), signers(CERT_A, CERT_B)));
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(CERT_A, CERT_B), signers(CERT_A)));
    }

    @Test
    public void unknownInstalledSignatureNeverAllowsAnInstall() {
        // Two empty sets are "equal", which must not count as a match.
        assertNotNull(UpdatePolicy.rejectionReason(PKG, PKG, 151, 152, signers(), signers()));
    }
}
