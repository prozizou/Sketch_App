package pro.sketchware.control;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Downloads a file whose SHA-256 is known, surviving lost connections. The bytes received are kept in
 * {@code partial}, with the checksum they belong to in {@code partialSha}; every new attempt asks the server for
 * the rest with an HTTP {@code Range} header instead of starting over. Free of Android classes so it can be tested
 * against a real HTTP server.
 */
public final class ResumableDownloader {

    public interface Listener {
        /** @param percent 0-100, or -1 when the text is a message rather than progress */
        void onProgress(int percent, String text);

        /** @return true to stop retrying (the screen went away) */
        boolean isCancelled();
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** A lost connection or a server error that may go away: the kept bytes stay, the download can continue. */
    public static final class RetryableException extends IOException {
        RetryableException(String message) {
            super(message);
        }
    }

    /** The finished file doesn't have the announced checksum. */
    public static final class ChecksumException extends SecurityException {
        ChecksumException() {
            super("checksum mismatch, the file is corrupted or was tampered with");
        }
    }

    private final OkHttpClient client;
    private final Sleeper sleeper;
    private final int maxAutoRetries;

    public ResumableDownloader(OkHttpClient client) {
        this(client, Thread::sleep, DownloadResume.MAX_AUTO_RETRIES);
    }

    public ResumableDownloader(OkHttpClient client, Sleeper sleeper, int maxAutoRetries) {
        this.client = client;
        this.sleeper = sleeper;
        this.maxAutoRetries = maxAutoRetries;
    }

    /**
     * Downloads until {@code partial} holds the whole file with the expected checksum. A lost connection is retried
     * on its own up to {@code maxAutoRetries} times in a row (attempts that received bytes reset the count).
     *
     * @throws IOException       when the connection could not be brought back; the kept bytes stay for the next call
     * @throws SecurityException when the file doesn't match its checksum, even after one clean restart
     * @throws Exception         on any other error; the kept bytes are dropped
     */
    public void download(String url, String expectedSha256, File partial, File partialSha, Listener listener)
            throws Exception {
        int failures = 0;
        boolean restartedClean = false;
        while (true) {
            long before = partial.length();
            try {
                downloadOnce(url, expectedSha256, partial, partialSha, listener);
                return;
            } catch (IOException e) {
                // Bytes received during this attempt mean the connection works on and off: keep going.
                if (partial.length() > before) failures = 0;
                failures++;
                if (failures > maxAutoRetries || listener.isCancelled()
                        || !waitBeforeRetry(DownloadResume.retryDelaySeconds(failures), partial.length(), listener)) {
                    throw e;
                }
            } catch (ChecksumException e) {
                deletePartial(partial, partialSha);
                if (before == 0 || restartedClean) {
                    throw e;
                }
                // The kept bytes may have been damaged: try once more from the beginning.
                restartedClean = true;
                listener.onProgress(-1, "Restarting the download…");
            } catch (Exception e) {
                deletePartial(partial, partialSha);
                throw e;
            }
        }
    }

    /**
     * One request: continues the kept bytes when they belong to this file, otherwise downloads from the start.
     * On return {@code partial} holds the whole file and its checksum matched.
     *
     * @throws IOException when the connection drops or the server answers with an error that may go away
     */
    void downloadOnce(String url, String expectedSha256, File partial, File partialSha, Listener listener)
            throws Exception {
        long from = DownloadResume.resumeFrom(partial.isFile() ? partial.length() : 0, readText(partialSha), expectedSha256);
        if (from == 0) {
            deletePartial(partial, partialSha);
            writeText(partialSha, expectedSha256.trim());
        }
        Request.Builder request = new Request.Builder().url(url);
        if (from > 0) {
            request.header("Range", "bytes=" + from + "-");
            listener.onProgress(-1, String.format(Locale.US, "Resuming download from %.1f MB…", from / 1048576f));
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Response response = client.newCall(request.build()).execute()) {
            int code = response.code();
            String contentRange = response.header("Content-Range");
            DownloadResume.Mode mode = DownloadResume.mode(code, from, contentRange);
            if (mode == DownloadResume.Mode.FAIL) {
                if (code == 206) {
                    // A partial answer that doesn't continue the kept bytes: start over next time.
                    deletePartial(partial, partialSha);
                    throw new RetryableException("unexpected partial answer");
                }
                if (DownloadResume.isRetryable(code)) {
                    throw new RetryableException("HTTP " + code);
                }
                throw new IllegalStateException("HTTP " + code);
            }
            if (mode == DownloadResume.Mode.RESTART && from > 0) {
                // The server sends the whole file again: drop the kept bytes, keep their checksum.
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
                from = 0;
            }
            if (from > 0) {
                hashFile(partial, digest);
            }
            if (mode != DownloadResume.Mode.ALREADY_COMPLETE) {
                long length = response.body().contentLength();
                long total = mode == DownloadResume.Mode.APPEND ? DownloadResume.totalFromContentRange(contentRange) : length;
                if (total <= 0 && length > 0) total = from + length;
                try (InputStream in = response.body().byteStream();
                     OutputStream out = new FileOutputStream(partial, from > 0)) {
                    byte[] buffer = new byte[16 * 1024];
                    long done = from;
                    int lastPercent = -2;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;
                        int percent = total > 0 ? (int) (done * 100 / total) : -1;
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            listener.onProgress(percent, total > 0
                                    ? String.format(Locale.US, "Downloading… %d%%  (%.1f / %.1f MB)", percent, done / 1048576f, total / 1048576f)
                                    : String.format(Locale.US, "Downloading… %.1f MB", done / 1048576f));
                        }
                    }
                    out.flush();
                }
            }
        }
        if (!UpdatePolicy.checksumMatches(digest.digest(), expectedSha256)) {
            throw new ChecksumException();
        }
    }

    /** @return the bytes kept from an earlier download of this file, 0 when there are none to continue. */
    public static long resumableBytes(File partial, File partialSha, String expectedSha256) {
        return DownloadResume.resumeFrom(partial.isFile() ? partial.length() : 0, readText(partialSha), expectedSha256);
    }

    public static void deletePartial(File partial, File partialSha) {
        //noinspection ResultOfMethodCallIgnored
        partial.delete();
        //noinspection ResultOfMethodCallIgnored
        partialSha.delete();
    }

    /** Counts down before an automatic retry. @return false when cancelled meanwhile. */
    private boolean waitBeforeRetry(int seconds, long kept, Listener listener) {
        for (int left = seconds; left > 0; left--) {
            if (listener.isCancelled()) {
                return false;
            }
            listener.onProgress(-1, String.format(Locale.US, "Connection lost (%.1f MB kept). Retrying in %d s…",
                    kept / 1048576f, left));
            try {
                sleeper.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static void hashFile(File file, MessageDigest digest) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
    }

    @Nullable
    private static String readText(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[256];
            int length = in.read(bytes);
            return length > 0 ? new String(bytes, 0, length, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
