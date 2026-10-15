package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/** How the compile log and the project screen agree to build again and to compare the outcome. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class RebuildRequestTest {
    @Test
    public void aFixedLogAsksForARebuildAndCarriesTheKeys() {
        Intent result = RebuildRequest.result(List.of("drawable/a", "color/b"));
        assertTrue(RebuildRequest.wantsRebuild(Activity.RESULT_OK, result));
        assertEquals(List.of("drawable/a", "color/b"), RebuildRequest.keysBefore(result));
    }

    @Test
    public void backingOutOfTheLogNeverStartsABuild() {
        assertFalse(RebuildRequest.wantsRebuild(Activity.RESULT_CANCELED, RebuildRequest.result(List.of("a/b"))));
        assertFalse(RebuildRequest.wantsRebuild(Activity.RESULT_OK, null));
        assertFalse(RebuildRequest.wantsRebuild(Activity.RESULT_OK, new Intent()));
        assertTrue(RebuildRequest.keysBefore(null).isEmpty());
        assertTrue(RebuildRequest.keysBefore(new Intent()).isEmpty());
    }

    @Test
    public void reopeningTheLogAfterTheRebuildCarriesWhatToCompare() {
        Intent reopen = new Intent();
        assertFalse(RebuildRequest.isAfterRebuild(reopen));
        RebuildRequest.markReopening(reopen, List.of("drawable/a"));
        assertTrue(RebuildRequest.isAfterRebuild(reopen));
        assertEquals(List.of("drawable/a"), RebuildRequest.keysBefore(reopen));
        assertFalse(RebuildRequest.isAfterRebuild(null));
    }
}
