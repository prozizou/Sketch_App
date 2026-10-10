package pro.sketchware.control;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;

/** Runs the downloader against a real local HTTP server that drops the connection in the middle of the file. */
public class ResumableDownloaderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final byte[] file = new byte[300_000];
    private String sha;
    private HttpServer server;
    private String url;

    /** Requests that are cut after {@link #cutAfter} bytes, counted down. */
    private final AtomicInteger cutsLeft = new AtomicInteger();
    private volatile int cutAfter;
    private volatile boolean ignoreRange;
    private volatile int failStatus;
    private final List<String> ranges = Collections.synchronizedList(new ArrayList<>());

    private File partial;
    private File partialSha;
    private final List<String> messages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        new Random(42).nextBytes(file);
        sha = hex(MessageDigest.getInstance("SHA-256").digest(file));
        partial = new File(folder.getRoot(), "update.apk.part");
        partialSha = new File(folder.getRoot(), "update.apk.part.sha256");

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app.apk", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            ranges.add(range == null ? "" : range);
            if (failStatus != 0) {
                exchange.sendResponseHeaders(failStatus, -1);
                exchange.close();
                return;
            }
            int from = 0;
            if (range != null && !ignoreRange) {
                from = Integer.parseInt(range.replace("bytes=", "").replace("-", ""));
                if (from >= file.length) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + file.length);
                    exchange.sendResponseHeaders(416, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + from + "-" + (file.length - 1) + "/" + file.length);
                exchange.sendResponseHeaders(206, file.length - from);
            } else {
                exchange.sendResponseHeaders(200, file.length);
            }
            OutputStream out = exchange.getResponseBody();
            int end = file.length;
            if (cutsLeft.getAndDecrement() > 0) {
                end = Math.min(file.length, from + cutAfter);
            }
            try {
                out.write(file, from, end - from);
                out.flush();
            } catch (IOException ignored) {
            }
            // Closing before Content-Length bytes were sent drops the connection mid-file.
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/app.apk";
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private ResumableDownloader downloader(int maxRetries) {
        return new ResumableDownloader(new OkHttpClient(), millis -> {
        }, maxRetries);
    }

    private final ResumableDownloader.Listener listener = new ResumableDownloader.Listener() {
        @Override
        public void onProgress(int percent, String text) {
            messages.add(text);
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    @Test
    public void aDroppedConnectionContinuesWhereItStopped() throws Exception {
        cutsLeft.set(1);
        cutAfter = 100_000;

        downloader(3).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals(List.of("", "bytes=100000-"), ranges);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Connection lost")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Resuming download")));
    }

    @Test
    public void manyDropsInARowStillFinishWhileBytesKeepComing() throws Exception {
        cutsLeft.set(5);
        cutAfter = 50_000;

        // Only 2 retries in a row allowed, but each attempt brings bytes, so the count starts over.
        downloader(2).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals(List.of("", "bytes=50000-", "bytes=100000-", "bytes=150000-", "bytes=200000-", "bytes=250000-"), ranges);
    }

    @Test
    public void givingUpKeepsTheBytesForTheNextTry() throws Exception {
        cutsLeft.set(1);
        cutAfter = 120_000;
        try {
            downloader(0).download(url, sha, partial, partialSha, listener);
            fail("the connection was dropped and no retry was allowed");
        } catch (IOException expected) {
        }
        assertEquals(120_000, partial.length());
        assertEquals(120_000, ResumableDownloader.resumableBytes(partial, partialSha, sha));

        // The user taps "Retry": only the rest is asked for.
        downloader(0).download(url, sha, partial, partialSha, listener);
        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals("bytes=120000-", ranges.get(ranges.size() - 1));
    }

    @Test
    public void bytesOfAnotherVersionAreNotContinued() throws Exception {
        Files.write(partial.toPath(), new byte[50_000]);
        Files.write(partialSha.toPath(), "0000000000000000000000000000000000000000000000000000000000000000".getBytes());

        downloader(0).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals(List.of(""), ranges);
    }

    @Test
    public void aServerIgnoringTheRangeRestartsCleanly() throws Exception {
        cutsLeft.set(1);
        cutAfter = 80_000;
        ignoreRange = true;

        downloader(3).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
    }

    @Test
    public void keptBytesThatAreAlreadyTheWholeFileAreAccepted() throws Exception {
        Files.write(partial.toPath(), file);
        Files.write(partialSha.toPath(), sha.getBytes());

        downloader(0).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals(List.of("bytes=300000-"), ranges);
    }

    @Test
    public void damagedKeptBytesRestartOnceFromTheBeginning() throws Exception {
        byte[] damaged = new byte[100_000];
        System.arraycopy(file, 0, damaged, 0, damaged.length);
        damaged[10] ^= 1;
        Files.write(partial.toPath(), damaged);
        Files.write(partialSha.toPath(), sha.getBytes());

        downloader(0).download(url, sha, partial, partialSha, listener);

        assertArrayEquals(file, Files.readAllBytes(partial.toPath()));
        assertEquals(List.of("bytes=100000-", ""), ranges);
    }

    @Test
    public void aWrongChecksumIsRejectedAndNothingIsKept() throws Exception {
        String wrong = "1111111111111111111111111111111111111111111111111111111111111111";
        try {
            downloader(0).download(url, wrong, partial, partialSha, listener);
            fail("the checksum doesn't match");
        } catch (SecurityException expected) {
        }
        assertFalse(partial.exists());
        assertFalse(partialSha.exists());
    }

    @Test
    public void aMissingFileIsNotRetried() throws Exception {
        failStatus = 404;
        try {
            downloader(3).download(url, sha, partial, partialSha, listener);
            fail("404");
        } catch (IllegalStateException expected) {
            assertEquals("HTTP 404", expected.getMessage());
        }
        assertEquals(1, ranges.size());
    }

    @Test
    public void aBusyServerIsRetried() throws Exception {
        failStatus = 503;
        try {
            downloader(2).download(url, sha, partial, partialSha, listener);
            fail("503");
        } catch (IOException expected) {
            assertEquals("HTTP 503", expected.getMessage());
        }
        assertEquals(3, ranges.size());
    }

    @Test
    public void nothingIsResumableBeforeAnyDownload() {
        assertEquals(0, ResumableDownloader.resumableBytes(partial, partialSha, sha));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}
