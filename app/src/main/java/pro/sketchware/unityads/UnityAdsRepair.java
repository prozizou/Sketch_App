package pro.sketchware.unityads;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;

/**
 * Repairs the Unity Ads SDK in a project: compiles JARs its libraries embed, downloads libraries it lacks
 * ({@link UnityAdsRequirements}) and, when classes are still missing, downloads the whole SDK again. Blocking: run it
 * off the main thread.
 */
public final class UnityAdsRepair {
    public interface Progress {
        void onStep(String what, int index, int total);
    }

    /** What happened: {@code failed} holds one line per problem, with the reason. */
    public static final class Result {
        public final List<String> added = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    /** Collects what the downloader reports, including the problems it reports without stopping. */
    private static final class Collector extends DependencyResolver.DependencyResolverCallback {
        final List<String> downloaded = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        @Override
        public void onArtifactNotFound(@NonNull Artifact artifact) {
            problems.add(artifact + ": not found in the repositories");
        }

        @Override
        public void onVersionNotFound(@NonNull Artifact artifact) {
            problems.add(artifact + ": no version available");
        }

        @Override
        public void onDependenciesNotFound(@NonNull Artifact artifact) {
            problems.add(artifact + ": no classes found in the download");
        }

        @Override
        public void onDownloadError(@NonNull Artifact artifact, @NonNull Throwable error) {
            problems.add(artifact + ": download failed (" + error.getMessage() + ")");
        }

        @Override
        public void dexingFailed(@NonNull Artifact artifact, @NonNull Exception e) {
            problems.add(artifact + ": couldn't be compiled for Android (" + e.getMessage() + ")");
        }

        @Override
        public void onTaskCompleted(@NonNull List<String> artifacts) {
            downloaded.addAll(artifacts);
        }
    }

    private UnityAdsRepair() {
    }

    /**
     * @param missing   libraries the SDK needs that the project lacks, each downloaded alone
     * @param reinstall download the whole SDK again (needs the SDK's version in the project's libraries)
     */
    public static Result run(String scId, List<UnityAdsRequirements.Requirement> missing, boolean reinstall, Progress progress) {
        Result result = new Result();
        BuiltInLibraries.maybeExtractAndroidJar();
        BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

        String sdkVersion = UnityAdsRequirements.sdkVersion(LocalLibrariesUtil.getLocalLibraries(scId));
        // Marks the libraries as part of the SDK, so their manifests are merged like the others'.
        String dependencyLabel = UnityAdsSdk.dependency(sdkVersion == null ? "repair" : sdkVersion);
        BuildSettings buildSettings = new BuildSettings(scId);

        if (reinstall && sdkVersion != null) {
            progress.onStep(UnityAdsSdk.dependency(sdkVersion) + " and its libraries", 0, 1);
            Collector collector = download(UnityAdsConfig.SDK_GROUP, UnityAdsConfig.SDK_ARTIFACT, sdkVersion, false, buildSettings);
            if (!collector.downloaded.isEmpty()) {
                addToProject(scId, collector.downloaded, dependencyLabel);
                result.added.add(UnityAdsSdk.dependency(sdkVersion) + " and " + (collector.downloaded.size() - 1) + " libraries");
            }
            result.failed.addAll(collector.problems);
            if (collector.downloaded.isEmpty() && collector.problems.isEmpty()) {
                result.failed.add(UnityAdsSdk.dependency(sdkVersion) + ": nothing was downloaded");
            }
        } else {
            for (int i = 0; i < missing.size(); i++) {
                String coordinate = missing.get(i).coordinate;
                progress.onStep(coordinate, i, missing.size());
                String[] parts = coordinate.split(":");
                Collector collector = download(parts[0], parts[1], parts[2], true, buildSettings);
                if (collector.downloaded.isEmpty() || !collector.problems.isEmpty()) {
                    result.failed.addAll(collector.problems);
                    if (collector.problems.isEmpty()) {
                        result.failed.add(coordinate + ": nothing was downloaded");
                    }
                } else {
                    addToProject(scId, collector.downloaded, dependencyLabel);
                    result.added.add(coordinate);
                }
            }
        }

        // JARs an AAR carries in its libs/ folder (Unity's OMID SDK) that the downloader never compiled.
        for (String library : UnityAdsStore.librariesWithUncompiledJars(scId)) {
            progress.onStep(library + " (embedded libraries)", 0, 1);
            List<String> notCompiled = DependencyResolver.dexEmbeddedJars(
                    new File(LocalLibrariesUtil.getLocalLibsRoot(), library),
                    DependencyResolver.compileLibraryJars(buildSettings));
            for (String jar : notCompiled) {
                result.failed.add(library + "/libs/" + jar + ": couldn't be compiled");
            }
            if (notCompiled.isEmpty()) {
                result.added.add(library + " (embedded libraries)");
            }
        }
        return result;
    }

    private static Collector download(String group, String artifact, String version, boolean skipDependencies, BuildSettings buildSettings) {
        Collector collector = new Collector();
        try {
            new DependencyResolver(group, artifact, version, skipDependencies, buildSettings).resolveDependency(collector);
        } catch (Throwable e) {
            // The downloader reports most problems through the callback, but an I/O error can still escape.
            collector.problems.add(group + ":" + artifact + ":" + version + ": " + e);
        }
        return collector;
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
