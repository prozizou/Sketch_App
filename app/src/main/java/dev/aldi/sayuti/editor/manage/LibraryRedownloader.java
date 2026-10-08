package dev.aldi.sayuti.editor.manage;

import androidx.annotation.NonNull;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;

/** Downloads a library planned by {@link OnlineLibraryFinder}, the way the library downloader does. */
public class LibraryRedownloader {
    private LibraryRedownloader() {
    }

    /**
     * Blocks until the download is over. Returns the folder names of the libraries installed (the library
     * and its sub-dependencies), or {@code null} if it did not complete. Run it off the main thread.
     */
    public static List<String> download(OnlineLibraryFinder.Plan plan, BuildSettings buildSettings) {
        BuiltInLibraries.maybeExtractAndroidJar((message, progress) -> {
        });
        BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

        AtomicReference<List<String>> installed = new AtomicReference<>();
        new DependencyResolver(plan.group, plan.artifact, plan.version, false, buildSettings)
                .resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onTaskCompleted(@NonNull List<String> dependencies) {
                        installed.set(dependencies);
                    }
                });
        return installed.get();
    }
}
