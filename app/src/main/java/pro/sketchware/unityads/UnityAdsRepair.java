package pro.sketchware.unityads;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;

/**
 * Downloads the libraries the SDK needs but the project lacks ({@link UnityAdsRequirements}), one by one and without
 * their own dependencies (those came with the SDK), and adds them to the project's local libraries. Blocking: run it
 * off the main thread.
 */
public final class UnityAdsRepair {
    public interface Progress {
        void onStep(String coordinate, int index, int total);
    }

    /** What happened to each library: empty when all were added. */
    public static final class Result {
        public final List<String> added = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    private UnityAdsRepair() {
    }

    public static Result run(String scId, List<UnityAdsRequirements.Requirement> missing, Progress progress) {
        Result result = new Result();
        BuiltInLibraries.maybeExtractAndroidJar();
        BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

        String sdkVersion = UnityAdsRequirements.sdkVersion(LocalLibrariesUtil.getLocalLibraries(scId));
        // Marks the libraries as part of the SDK, so their manifests are merged like the others'.
        String dependencyLabel = UnityAdsSdk.dependency(sdkVersion == null ? "repair" : sdkVersion);
        BuildSettings buildSettings = new BuildSettings(scId);

        for (int i = 0; i < missing.size(); i++) {
            String coordinate = missing.get(i).coordinate;
            progress.onStep(coordinate, i, missing.size());
            String[] parts = coordinate.split(":");
            List<String> downloaded = new ArrayList<>();
            boolean[] failed = {false};
            try {
                new DependencyResolver(parts[0], parts[1], parts[2], true, buildSettings)
                        .resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                            @Override
                            public void onArtifactNotFound(@NonNull Artifact artifact) {
                                failed[0] = true;
                            }

                            @Override
                            public void onDownloadError(@NonNull Artifact artifact, @NonNull Throwable error) {
                                failed[0] = true;
                            }

                            @Override
                            public void dexingFailed(@NonNull Artifact artifact, @NonNull Exception e) {
                                failed[0] = true;
                            }

                            @Override
                            public void onTaskCompleted(@NonNull List<String> artifacts) {
                                downloaded.addAll(artifacts);
                            }
                        });
            } catch (RuntimeException e) {
                failed[0] = true;
            }
            if (failed[0] || downloaded.isEmpty()) {
                result.failed.add(coordinate);
            } else {
                addToProject(scId, downloaded, dependencyLabel);
                result.added.add(coordinate);
            }
        }
        return result;
    }

    /** Adds or replaces the project's entries for these library folders. */
    private static void addToProject(String scId, List<String> names, String dependencyLabel) {
        ArrayList<HashMap<String, Object>> libraries = LocalLibrariesUtil.getLocalLibraries(scId);
        for (String name : names) {
            libraries.removeIf(library -> name.equals(String.valueOf(library.get("name"))));
            libraries.add(LocalLibrariesUtil.createLibraryMap(name, dependencyLabel));
        }
        LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(libraries));
    }
}
