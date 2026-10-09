package pro.sketchware.build;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.SketchApplication;
import pro.sketchware.build.MemoryProfile.Profile;
import pro.sketchware.flags.FeatureFlag;
import pro.sketchware.flags.FeatureFlags;

/** Applies the {@link MemoryProfile} of this device to the compilers. */
public final class BuildMemory {
    private static final long MEGABYTE = 1024 * 1024;

    private BuildMemory() {
    }

    /** What the device allows, from its RAM, heap, cores and the user's setting. */
    public static Profile profile() {
        Context context = SketchApplication.getContext();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        long totalRamMb = 0;
        boolean lowRam = false;
        if (activityManager != null) {
            activityManager.getMemoryInfo(memory);
            totalRamMb = memory.totalMem / MEGABYTE;
            lowRam = activityManager.isLowRamDevice();
        }
        return MemoryProfile.choose(totalRamMb, lowRam, Runtime.getRuntime().maxMemory() / MEGABYTE,
                Runtime.getRuntime().availableProcessors(), FeatureFlags.isEnabled(FeatureFlag.FORCE_LOW_MEMORY_BUILD));
    }

    /**
     * A thread pool for D8 or R8 sized for this device, to be shut down by the caller after use; null when the
     * memory-aware build is switched off, in which case the compilers keep their own default behaviour.
     */
    @Nullable
    public static ExecutorService newCompilerExecutor() {
        if (!FeatureFlags.isEnabled(FeatureFlag.MEMORY_AWARE_BUILD)) return null;
        Profile profile = profile();
        if (profile.collectGarbageBetweenSteps()) {
            // Frees what the earlier steps (Java compilation, resources) no longer need before the heavy step starts
            System.gc();
        }
        return Executors.newFixedThreadPool(profile.compilerThreads());
    }
}
