package pro.sketchware.control;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The decisions {@link UpdateChecker} takes to continue an interrupted update download instead of starting it
 * over, kept free of Android and OkHttp classes so they can be unit tested.
 * <p>
 * The bytes already received stay in {@code update.apk.part}, next to a small file holding the SHA-256 of the
 * APK they belong to. A later attempt asks the server for the rest with an HTTP {@code Range} header.
 */
public final class DownloadResume {

    /** {@code bytes 100-999/1000}; the total may be {@code *} when the server doesn't know it. */
    private static final Pattern CONTENT_RANGE = Pattern.compile("^\\s*bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** How many times in a row a lost connection is retried on its own before the user is asked. */
    public static final int MAX_AUTO_RETRIES = 6;

    /** What to do with a server's answer to a download request. */
    public enum Mode {
        /** The server sends the rest of the file: append it to the bytes already there. */
        APPEND,
        /** The server sends the whole file (it ignored the range, or none was asked): start the file over. */
        RESTART,
        /** Nothing is left to download: the bytes already there may be the whole file, check them. */
        ALREADY_COMPLETE,
        /** An error answer. */
        FAIL
    }

    private DownloadResume() {
    }

    /**
     * @param partialLength the size of the bytes kept from an earlier attempt, 0 when there are none
     * @param partialSha256 the checksum of the APK those bytes belong to, as saved next to them
     * @param expectedSha256 the checksum of the APK being downloaded now
     * @return where to continue from: the kept size when those bytes belong to this APK, otherwise 0
     */
    public static long resumeFrom(long partialLength, @Nullable String partialSha256, @Nullable String expectedSha256) {
        if (partialLength <= 0 || !UpdatePolicy.isValidSha256(expectedSha256) || partialSha256 == null) {
            return 0;
        }
        return normalize(partialSha256).equals(normalize(expectedSha256)) ? partialLength : 0;
    }

    /**
     * @param code         the HTTP status of the answer
     * @param requestedFrom the first byte asked for, 0 when the whole file was asked
     * @param contentRange the answer's {@code Content-Range} header, may be null
     */
    public static Mode mode(int code, long requestedFrom, @Nullable String contentRange) {
        if (code == 206) {
            // Only append when the server continues exactly where the kept bytes stop.
            return requestedFrom > 0 && rangeStart(contentRange) == requestedFrom ? Mode.APPEND : Mode.FAIL;
        }
        if (code >= 200 && code < 300) {
            return Mode.RESTART;
        }
        if (code == 416 && requestedFrom > 0) {
            return Mode.ALREADY_COMPLETE;
        }
        return Mode.FAIL;
    }

    /**
     * @return whether an error answer may go away by itself (server busy, timeout), so it is worth retrying.
     */
    public static boolean isRetryable(int code) {
        return code == 408 || code == 429 || code >= 500;
    }

    /** @return the first byte of a {@code Content-Range}, or -1 when the header is missing or malformed. */
    public static long rangeStart(@Nullable String contentRange) {
        Matcher matcher = contentRange == null ? null : CONTENT_RANGE.matcher(contentRange);
        return matcher != null && matcher.matches() ? parse(matcher.group(1)) : -1;
    }

    /** @return the whole file's size from a {@code Content-Range}, or -1 when it isn't given. */
    public static long totalFromContentRange(@Nullable String contentRange) {
        Matcher matcher = contentRange == null ? null : CONTENT_RANGE.matcher(contentRange);
        return matcher != null && matcher.matches() && !matcher.group(3).equals("*") ? parse(matcher.group(3)) : -1;
    }

    /** @return the wait before automatic retry number {@code attempt} (1 for the first): 2, 4, 8, 16, 30, 30 s. */
    public static int retryDelaySeconds(int attempt) {
        if (attempt <= 1) return 2;
        return (int) Math.min(30, 2L << Math.min(attempt - 1, 4));
    }

    private static long parse(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normalize(String sha256) {
        return sha256.trim().toLowerCase(Locale.ROOT);
    }
}
