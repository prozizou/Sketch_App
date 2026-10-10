package pro.sketchware.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadResumeTest {
    private static final String SHA_A = "3fbcc27e09a8fd1c6d45f913b297ee9437c6f381493540926f3004d630059cdc";
    private static final String SHA_B = "0000000000000000000000000000000000000000000000000000000000000000";

    @Test
    public void keptBytesOfTheSameApkAreContinued() {
        assertEquals(5_000, DownloadResume.resumeFrom(5_000, SHA_A, SHA_A));
        assertEquals(5_000, DownloadResume.resumeFrom(5_000, " " + SHA_A.toUpperCase() + "\n", SHA_A));
    }

    @Test
    public void keptBytesOfAnotherApkAreDropped() {
        assertEquals(0, DownloadResume.resumeFrom(5_000, SHA_B, SHA_A));
        assertEquals(0, DownloadResume.resumeFrom(5_000, null, SHA_A));
        assertEquals(0, DownloadResume.resumeFrom(5_000, SHA_A, ""));
        assertEquals(0, DownloadResume.resumeFrom(0, SHA_A, SHA_A));
    }

    @Test
    public void partialAnswerContinuingTheKeptBytesIsAppended() {
        assertEquals(DownloadResume.Mode.APPEND, DownloadResume.mode(206, 100, "bytes 100-999/1000"));
    }

    @Test
    public void partialAnswerStartingElsewhereFails() {
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(206, 100, "bytes 0-999/1000"));
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(206, 100, null));
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(206, 0, "bytes 0-999/1000"));
    }

    @Test
    public void fullAnswerRestartsTheFile() {
        assertEquals(DownloadResume.Mode.RESTART, DownloadResume.mode(200, 0, null));
        // The server ignored the range: the whole file comes again.
        assertEquals(DownloadResume.Mode.RESTART, DownloadResume.mode(200, 100, null));
    }

    @Test
    public void rangePastTheEndMeansTheKeptBytesMayBeComplete() {
        assertEquals(DownloadResume.Mode.ALREADY_COMPLETE, DownloadResume.mode(416, 1000, "bytes */1000"));
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(416, 0, null));
    }

    @Test
    public void errorsFail() {
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(404, 0, null));
        assertEquals(DownloadResume.Mode.FAIL, DownloadResume.mode(503, 100, null));
    }

    @Test
    public void onlyTransientErrorsAreRetried() {
        assertTrue(DownloadResume.isRetryable(500));
        assertTrue(DownloadResume.isRetryable(503));
        assertTrue(DownloadResume.isRetryable(408));
        assertTrue(DownloadResume.isRetryable(429));
        assertFalse(DownloadResume.isRetryable(404));
        assertFalse(DownloadResume.isRetryable(403));
    }

    @Test
    public void contentRangeIsParsed() {
        assertEquals(100, DownloadResume.rangeStart("bytes 100-999/1000"));
        assertEquals(1000, DownloadResume.totalFromContentRange("bytes 100-999/1000"));
        assertEquals(145_342_149, DownloadResume.totalFromContentRange("Bytes 72000000-145342148/145342149"));
        assertEquals(-1, DownloadResume.totalFromContentRange("bytes 100-999/*"));
        assertEquals(-1, DownloadResume.rangeStart("garbage"));
        assertEquals(-1, DownloadResume.rangeStart(null));
        assertEquals(-1, DownloadResume.totalFromContentRange(null));
    }

    @Test
    public void retriesWaitLongerEachTimeUpToHalfAMinute() {
        assertEquals(2, DownloadResume.retryDelaySeconds(1));
        assertEquals(4, DownloadResume.retryDelaySeconds(2));
        assertEquals(8, DownloadResume.retryDelaySeconds(3));
        assertEquals(16, DownloadResume.retryDelaySeconds(4));
        assertEquals(30, DownloadResume.retryDelaySeconds(5));
        assertEquals(30, DownloadResume.retryDelaySeconds(50));
    }
}
