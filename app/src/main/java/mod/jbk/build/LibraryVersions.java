package mod.jbk.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NWS ships some libraries itself (kotlinx-coroutines 1.8.1...) and the DEX merge keeps the first copy of a class. A
 * downloaded library that needs a newer version of one of them (Unity Ads needs kotlinx-coroutines 1.10.2) would then
 * run on a mix of both versions: the old classes win, the new ones only fill the gaps. That can fail with no error
 * at all (a coroutine that never resumes). This finds the built-in libraries a project has a newer copy of, so the
 * newer one can be used on its own.
 */
public final class LibraryVersions {
    /** A library folder name: {@code kotlinx-coroutines-core-jvm-1.8.1} or {@code kotlin-stdlib-v2.1.21}. */
    private static final Pattern NAME = Pattern.compile("^(.+?)-[vV]?(\\d[0-9A-Za-z.+_-]*)$");

    private LibraryVersions() {
    }

    /** @return {artifact, version}, or null when the name doesn't end in a version */
    static String[] split(String libraryName) {
        if (libraryName == null) {
            return null;
        }
        Matcher matcher = NAME.matcher(libraryName);
        return matcher.matches() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    /** Compares dotted versions number by number; a missing part counts as 0 and text parts compare as text. */
    static int compare(String a, String b) {
        String[] left = a.split("[.\\-+_]");
        String[] right = b.split("[.\\-+_]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String x = i < left.length ? left[i] : "0";
            String y = i < right.length ? right[i] : "0";
            boolean numeric = x.matches("\\d+") && y.matches("\\d+");
            int result = numeric ? Long.compare(Long.parseLong(x), Long.parseLong(y)) : x.compareTo(y);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * @param builtIn the built-in libraries the project uses
     * @param local   the project's downloaded libraries
     * @return the built-in ones that a downloaded library of the same artifact is newer than
     */
    public static List<String> outdatedBuiltIns(List<String> builtIn, List<String> local) {
        Map<String, String> newestLocal = new HashMap<>();
        for (String name : local) {
            String[] parts = split(name);
            if (parts != null && (!newestLocal.containsKey(parts[0]) || compare(parts[1], newestLocal.get(parts[0])) > 0)) {
                newestLocal.put(parts[0], parts[1]);
            }
        }
        List<String> outdated = new ArrayList<>();
        for (String name : builtIn) {
            String[] parts = split(name);
            if (parts != null && newestLocal.containsKey(parts[0]) && compare(newestLocal.get(parts[0]), parts[1]) > 0) {
                outdated.add(name);
            }
        }
        return outdated;
    }
}
