package pro.sketchware.utility;

import java.util.ConcurrentModificationException;

/**
 * Runs a save that reads data another thread may be changing (the editor edits the project while a background save
 * walks it) and, when the data moved under it, waits a moment and runs it again instead of crashing.
 */
public final class RetryOnConcurrentChange {

    /** Waits between two tries; replaced in tests so they don't sleep. */
    public interface Pause {
        void sleep(long millis);
    }

    private RetryOnConcurrentChange() {
    }

    /**
     * @param attempts how many times to try in all (at least 1)
     * @param pauseMs  the wait before the second try, doubled before each further one
     * @return whether {@code work} finished; false after the last try failed too (other exceptions are not caught)
     */
    public static boolean run(Runnable work, int attempts, long pauseMs, Pause pause) {
        long wait = pauseMs;
        for (int attempt = 1; attempt <= Math.max(1, attempts); attempt++) {
            try {
                work.run();
                return true;
            } catch (ConcurrentModificationException e) {
                if (attempt == Math.max(1, attempts)) {
                    return false;
                }
                pause.sleep(wait);
                wait *= 2;
            }
        }
        return false;
    }
}
