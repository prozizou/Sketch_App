package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a rebuild changed about the missing resources: compares the ones the resolver was working on with the ones in
 * the new build's log. Nothing is assumed: a resource counts as fixed only because the new log no longer asks for it.
 *
 * @param fixed        resources that were missing before and are not asked for any more
 * @param stillMissing resources the new log still reports
 * @param newlyMissing resources the new log reports that the old one did not (AAPT2 reports in stages, so the next
 *                     stage shows up now)
 */
public record RebuildReport(@NonNull List<String> fixed, @NonNull List<String> stillMissing, @NonNull List<String> newlyMissing) {

    public static RebuildReport compare(List<String> before, List<MissingResource> after) {
        Set<String> now = new LinkedHashSet<>();
        for (MissingResource problem : after) now.add(problem.key());
        Set<String> then = new LinkedHashSet<>(before);
        List<String> fixed = new ArrayList<>();
        List<String> still = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (String key : then) (now.contains(key) ? still : fixed).add(key);
        for (String key : now) if (!then.contains(key)) added.add(key);
        return new RebuildReport(fixed, still, added);
    }

    /** Whether the build no longer reports any missing resource. */
    public boolean allGone() {
        return stillMissing.isEmpty() && newlyMissing.isEmpty();
    }
}
