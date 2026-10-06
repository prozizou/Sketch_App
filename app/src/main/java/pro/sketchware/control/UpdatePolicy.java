package pro.sketchware.control;

import androidx.annotation.Nullable;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The decisions {@link UpdateChecker} takes about a downloaded update, kept free of Android classes
 * so they can be unit tested: what may be downloaded, and what may be installed.
 */
public final class UpdatePolicy {
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private UpdatePolicy() {
    }

    /**
     * Only plain https links with a host are ever followed or opened.
     */
    public static boolean isSecureUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidSha256(@Nullable String value) {
        return value != null && SHA256_HEX.matcher(value.trim()).matches();
    }

    /**
     * @param digest the computed SHA-256 of the downloaded file
     * @param expectedHex the checksum announced in the manifest
     */
    public static boolean checksumMatches(byte[] digest, @Nullable String expectedHex) {
        return isValidSha256(expectedHex) && toHex(digest).equalsIgnoreCase(expectedHex.trim());
    }

    public static boolean isNewer(long candidateVersionCode, long installedVersionCode) {
        return candidateVersionCode > installedVersionCode;
    }

    /**
     * SHA-256 of a signing certificate, as lowercase hex.
     */
    public static String signerDigest(byte[] certificate) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(certificate));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Decides whether a downloaded APK may be handed to the package installer.
     *
     * @return null when it may be installed, otherwise the reason it was rejected.
     */
    @Nullable
    public static String rejectionReason(String installedPackage, @Nullable String downloadedPackage,
                                         long installedVersionCode, long downloadedVersionCode,
                                         Set<String> installedSigners, Set<String> downloadedSigners) {
        if (downloadedPackage == null) {
            return "the downloaded file is not a valid APK";
        }
        if (!installedPackage.equals(downloadedPackage)) {
            return "the APK belongs to another app (" + downloadedPackage + ")";
        }
        if (!isNewer(downloadedVersionCode, installedVersionCode)) {
            return "the APK is not newer than the installed version";
        }
        if (installedSigners.isEmpty()) {
            return "cannot read the installed app signature";
        }
        if (!installedSigners.equals(downloadedSigners)) {
            return "the APK is signed with a different key than the installed app";
        }
        return null;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
