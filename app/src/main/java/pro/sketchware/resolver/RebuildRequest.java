package pro.sketchware.resolver;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How the compile log asks the project screen to build again after resources were fixed, and what the project screen
 * hands back when it opens the log again so the result can be compared.
 */
public final class RebuildRequest {
    /** In the log screen's result: build again. */
    public static final String EXTRA_REBUILD = "resolver.rebuild";
    /** In the log screen's result, and in the intent that reopens it: the keys of the missing resources before the fix. */
    public static final String EXTRA_BEFORE_KEYS = "resolver.before_keys";
    /** In the intent that reopens the log after a rebuild: it follows a rebuild started by the resolver. */
    public static final String EXTRA_AFTER_REBUILD = "resolver.after_rebuild";

    private RebuildRequest() {
    }

    /** The result the log screen returns when the person fixed resources and a build should follow. */
    @NonNull
    public static Intent result(List<String> keysBefore) {
        return new Intent().putExtra(EXTRA_REBUILD, true).putStringArrayListExtra(EXTRA_BEFORE_KEYS, new ArrayList<>(keysBefore));
    }

    /** Whether a result asks for a rebuild. */
    public static boolean wantsRebuild(int resultCode, @Nullable Intent data) {
        return resultCode == android.app.Activity.RESULT_OK && data != null && data.getBooleanExtra(EXTRA_REBUILD, false);
    }

    /** The keys of the missing resources before the fix, from a result or from the reopening intent. */
    @NonNull
    public static List<String> keysBefore(@Nullable Intent data) {
        ArrayList<String> keys = data == null ? null : data.getStringArrayListExtra(EXTRA_BEFORE_KEYS);
        return keys == null ? new ArrayList<>() : keys;
    }

    /** What to add to the intent that reopens the log after the rebuild the resolver asked for. */
    public static void markReopening(@NonNull Intent intent, List<String> keysBefore) {
        intent.putExtra(EXTRA_AFTER_REBUILD, true).putStringArrayListExtra(EXTRA_BEFORE_KEYS, new ArrayList<>(keysBefore));
    }

    public static boolean isAfterRebuild(@Nullable Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_AFTER_REBUILD, false);
    }
}
