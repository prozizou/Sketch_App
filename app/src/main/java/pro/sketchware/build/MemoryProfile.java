package pro.sketchware.build;

/**
 * Decides how much parallel work the compilers may do, from what the device has. The dex and shrinker steps keep a lot
 * of the app in memory at once, and each extra thread multiplies the peak, so on phones with little memory they run
 * with fewer threads. That is slower but avoids "out of memory" and the system killing the app.
 */
public final class MemoryProfile {
    public enum Tier {LOW, NORMAL, HIGH}

    /**
     * @param compilerThreads        threads D8 and R8 may use
     * @param collectGarbageBetweenSteps whether to ask the runtime to free memory between the heavy steps
     */
    public record Profile(Tier tier, int compilerThreads, boolean collectGarbageBetweenSteps) {
    }

    /** At or below this much RAM (about a "3 GB" phone) the build is kept small. */
    static final long LOW_RAM_MB = 3072;
    /** At or below this heap size the app cannot hold a big build even with a lot of RAM. */
    static final long LOW_HEAP_MB = 256;
    /** From this much RAM and enough cores the build may use more threads. */
    static final long HIGH_RAM_MB = 6144;
    static final int HIGH_MIN_CORES = 6;

    private MemoryProfile() {
    }

    /**
     * @param totalRamMb   the device's total RAM
     * @param lowRamDevice the system marks the device as low-RAM
     * @param maxHeapMb    the most memory this app may use
     * @param cores        processor cores
     * @param forceLow     the user asked for the low-memory build whatever the device is
     */
    public static Profile choose(long totalRamMb, boolean lowRamDevice, long maxHeapMb, int cores, boolean forceLow) {
        int usableCores = Math.max(1, cores);
        if (forceLow || lowRamDevice || (totalRamMb > 0 && totalRamMb <= LOW_RAM_MB) || (maxHeapMb > 0 && maxHeapMb <= LOW_HEAP_MB)) {
            return new Profile(Tier.LOW, 1, true);
        }
        if (totalRamMb >= HIGH_RAM_MB && usableCores >= HIGH_MIN_CORES) {
            return new Profile(Tier.HIGH, Math.min(4, usableCores), false);
        }
        return new Profile(Tier.NORMAL, Math.min(2, usableCores), false);
    }
}
