package pro.sketchware.analysis.fix;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import pro.sketchware.utility.FileUtil;

/**
 * Applies a {@link LibraryFixes.Plan} to a project: local libraries leave the project's list, built-in ones are added
 * to its exclusions. It remembers what the two files held before so {@link #undo} puts back exactly that. No library
 * file is deleted. Run it off the main thread.
 */
public final class LibraryFixApplier {
    /** What was stored before, to take the change back. */
    public record Snapshot(@Nullable String localLibraries, @Nullable String excluded) {
    }

    public record Result(int removed, int excluded, Snapshot snapshot) {
    }

    private LibraryFixApplier() {
    }

    public static Result apply(String scId, LibraryFixes.Plan plan) {
        File localFile = LocalLibrariesUtil.getLocalLibFile(scId);
        Snapshot snapshot = new Snapshot(localFile.isFile() ? FileUtil.readFile(localFile.getAbsolutePath()) : null,
                ExcludeBuiltInLibrariesActivity.readRawConfig(scId));

        Set<String> remove = new HashSet<>();
        List<String> exclude = new ArrayList<>();
        for (LibraryFixes.Action action : plan.actions()) {
            if (action.kind() == LibraryFixes.Kind.REMOVE_LOCAL) remove.add(action.library());
            else exclude.add(action.library());
        }
        int removed = 0;
        if (!remove.isEmpty()) {
            List<HashMap<String, Object>> kept = new ArrayList<>();
            for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
                Object name = library.get("name");
                if (name != null && remove.contains(name.toString())) removed++;
                else kept.add(library);
            }
            if (removed > 0) LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(kept));
        }
        if (!exclude.isEmpty()) ExcludeBuiltInLibrariesActivity.excludeLibraries(scId, exclude);
        return new Result(removed, exclude.size(), snapshot);
    }

    /** Puts both files back as they were before {@link #apply}. */
    public static void undo(String scId, Snapshot snapshot) {
        File localFile = LocalLibrariesUtil.getLocalLibFile(scId);
        if (snapshot.localLibraries() == null) FileUtil.deleteFile(localFile.getAbsolutePath());
        else LocalLibrariesUtil.rewriteLocalLibFile(scId, snapshot.localLibraries());
        ExcludeBuiltInLibrariesActivity.restoreRawConfig(scId, snapshot.excluded());
    }
}
