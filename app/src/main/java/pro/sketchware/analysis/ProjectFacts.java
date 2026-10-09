package pro.sketchware.analysis;

import java.util.List;
import java.util.Set;

/**
 * Everything the project analyses look at, gathered once. It is plain data so the analyses can be tested without a device.
 *
 * @param minSdk              0 when unknown
 * @param targetSdk           0 when unknown
 * @param permissions         full names, like {@code android.permission.INTERNET}
 * @param libraries           names of the enabled built-in and local libraries
 * @param usesAndroidX        the project uses AndroidX (AppCompat or Material enabled)
 * @param nativeAbis          ABIs that have native libraries in the project, like {@code arm64-v8a}
 * @param hasNightResources   the project has resources for dark mode
 */
public record ProjectFacts(String packageName, int minSdk, int targetSdk, Set<String> permissions, List<SourceFile> sources,
                           Set<String> libraries, boolean usesAndroidX, Set<String> nativeAbis, List<ViewFacts> views,
                           boolean hasNightResources) {
    public static final ProjectFacts EMPTY = new ProjectFacts("", 0, 0, Set.of(), List.of(), Set.of(), false, Set.of(), List.of(), false);
}
