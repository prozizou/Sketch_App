package dev.aldi.sayuti.editor.manage;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chooses which version of a library to download when the wanted one may not exist. */
public class MavenVersions {
    private static final Pattern UNSTABLE = Pattern.compile("(?i)(alpha|beta|rc|snapshot|dev|preview|milestone|canary|eap)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private MavenVersions() {
    }

    public static boolean isStable(String version) {
        return !UNSTABLE.matcher(version).find();
    }

    /** Compares by the numbers in the versions (1.10 is newer than 1.9); stable beats pre-release at equal numbers. */
    public static int compare(String a, String b) {
        List<Long> x = numbers(a);
        List<Long> y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            long left = i < x.size() ? x.get(i) : 0;
            long right = i < y.size() ? y.get(i) : 0;
            if (left != right) {
                return Long.compare(left, right);
            }
        }
        int byStability = Boolean.compare(isStable(a), isStable(b));
        return byStability != 0 ? byStability : Integer.compare(preReleaseRank(a), preReleaseRank(b));
    }

    /** alpha &lt; beta &lt; milestone/preview/rc: the later in a release cycle, the newer. */
    private static int preReleaseRank(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        if (lower.contains("rc") || lower.contains("milestone") || lower.contains("preview")) return 3;
        if (lower.contains("beta")) return 2;
        if (lower.contains("alpha")) return 1;
        return 0;
    }

    /**
     * The wanted version if it exists, else the newest stable one of the same major version, else the newest
     * stable one, else the newest of all. {@code null} when there is nothing to choose from.
     */
    @Nullable
    public static String pickBest(@Nullable String wanted, List<String> available) {
        if (available.isEmpty()) {
            return null;
        }
        if (wanted != null && available.contains(wanted)) {
            return wanted;
        }
        List<String> stable = new ArrayList<>();
        for (String version : available) {
            if (isStable(version)) {
                stable.add(version);
            }
        }
        List<String> pool = stable.isEmpty() ? available : stable;
        if (wanted != null) {
            List<Long> wantedNumbers = numbers(wanted);
            if (!wantedNumbers.isEmpty()) {
                List<String> sameMajor = new ArrayList<>();
                for (String version : pool) {
                    List<Long> candidate = numbers(version);
                    if (!candidate.isEmpty() && candidate.get(0).equals(wantedNumbers.get(0))) {
                        sameMajor.add(version);
                    }
                }
                if (!sameMajor.isEmpty()) {
                    pool = sameMajor;
                }
            }
        }
        String best = pool.get(0);
        for (String version : pool) {
            if (compare(version, best) > 0) {
                best = version;
            }
        }
        return best;
    }

    private static List<Long> numbers(String version) {
        List<Long> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(version.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            try {
                numbers.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
                numbers.add(Long.MAX_VALUE);
            }
        }
        return numbers;
    }
}
