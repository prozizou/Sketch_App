package pro.sketchware.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class RetryOnConcurrentChangeTest {

    @Test
    public void aSaveThatWorksRunsOnceWithoutWaiting() {
        int[] runs = {0};
        List<Long> waits = new ArrayList<>();
        assertTrue(RetryOnConcurrentChange.run(() -> runs[0]++, 5, 100, waits::add));
        assertEquals(1, runs[0]);
        assertTrue(waits.isEmpty());
    }

    @Test
    public void aSaveHitByAnEditIsRunAgainAfterGrowingPauses() {
        int[] runs = {0};
        List<Long> waits = new ArrayList<>();
        boolean done = RetryOnConcurrentChange.run(() -> {
            if (++runs[0] < 3) throw new ConcurrentModificationException();
        }, 5, 100, waits::add);
        assertTrue(done);
        assertEquals(3, runs[0]);
        assertEquals(List.of(100L, 200L), waits);
    }

    @Test
    public void itGivesUpAfterTheLastTryInsteadOfCrashing() {
        int[] runs = {0};
        boolean done = RetryOnConcurrentChange.run(() -> {
            runs[0]++;
            throw new ConcurrentModificationException();
        }, 3, 10, millis -> { });
        assertFalse(done);
        assertEquals(3, runs[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void otherErrorsAreNotHidden() {
        RetryOnConcurrentChange.run(() -> {
            throw new IllegalStateException("real bug");
        }, 3, 10, millis -> { });
    }
}
