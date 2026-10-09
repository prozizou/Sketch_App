package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a failed build's log and says what went wrong in plain words: which tool failed, why, and what to try.
 * <p>
 * It only recognises the failures listed in {@link #RULES}; a log it doesn't recognise gives no findings, and the
 * raw log stays the reference. Each rule reports at most once per log, with the first matching line as evidence.
 */
public final class BuildDoctor {
    private record Rule(String id, Severity severity, Pattern pattern, String title, String cause, String solution) {
        Rule(String id, Severity severity, String regex, String title, String cause, String solution) {
            this(id, severity, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), title, cause, solution);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("build.out-of-memory", Severity.ERROR,
                    "OutOfMemoryError|GC overhead limit exceeded|Java heap space|std::bad_alloc",
                    "The build ran out of memory",
                    "The compiler needs more memory than the device can give it. Shrinking with R8 and large projects are the heaviest steps.",
                    "Close other apps and build again. Set the code shrinker to Off, remove libraries you don't use, and shrink large images. On devices with 2 GB of RAM, build one project at a time."),
            new Rule("build.no-space", Severity.ERROR,
                    "No space left on device|ENOSPC",
                    "The device is out of storage",
                    "The build writes temporary files and the APK, and there is no room left.",
                    "Free some storage, clear the old temporary files of the project (Clean temporary files) and delete old backups and snapshots you don't need."),
            new Rule("build.duplicate-class", Severity.ERROR,
                    "is defined multiple times|Duplicate class |Program type already present",
                    "The same class comes from two libraries",
                    "Two enabled libraries contain the same class, so the dex step can't decide which one to keep. This often happens when a local library already bundles something that is also built in.",
                    "Remove one of the libraries, or exclude the built-in copy in Library Manager > Exclude built-in libraries. The Dependency Inspector lists which libraries overlap."),
            new Rule("build.dex-limit", Severity.ERROR,
                    "Cannot fit requested classes in a single dex file|method ID not in \\[0, 0xffff\\]|too many (?:method|field) references",
                    "The app has too many methods for one dex file",
                    "A single dex file holds at most 65,536 methods. The libraries in this project add up to more.",
                    "Enable multidex in the project's build settings, remove libraries you don't need, or turn on the code shrinker to drop unused code."),
            new Rule("build.wrong-class-version", Severity.ERROR,
                    "Unsupported class file major version|class file has wrong version|was compiled with a newer",
                    "A library was built for a newer Java than the build supports",
                    "The library's classes use a newer Java class-file format than the compiler or D8 in this app can read.",
                    "Use an older version of that library, built for Java 8 to 17."),
            new Rule("build.default-interface-methods", Severity.ERROR,
                    "Default interface methods are only supported starting with Android N|Static interface methods are only supported starting with Android N|Invoke-customs are only supported starting with Android O",
                    "A library or your code needs a higher minimum SDK",
                    "The code uses a Java feature that Android only supports from a later version than the project's minimum SDK.",
                    "Raise the minimum SDK in the project's build settings (24 for default interface methods, 26 for lambdas via invoke-custom), or use an older version of the library."),
            new Rule("build.aapt2-resource-missing", Severity.ERROR,
                    "error: resource [\\w./:-]+ \\(aka [^)]+\\) not found|AAPT: error: resource [\\w./:-]+ .*not found|failed linking references",
                    "A resource that is used does not exist",
                    "A layout, style or the manifest refers to a resource (a colour, style, attribute or drawable) that is not in the project or in its libraries.",
                    "Check the name in the log, add the resource, or enable the library that provides it (for Material attributes, the Material or AppCompat library, and a Material theme)."),
            new Rule("build.aapt2-attribute-missing", Severity.ERROR,
                    "error: attribute [\\w:.-]+ \\(aka [^)]+\\) not found|error: attribute [\\w:.-]+ not found|error: style attribute",
                    "An XML attribute is not known",
                    "A layout or style uses an attribute that belongs to a library that is not enabled, or that does not exist at this SDK level.",
                    "Enable the library that defines the attribute, fix its spelling, or raise the target SDK if it is a recent platform attribute."),
            new Rule("build.manifest", Severity.ERROR,
                    "Manifest merger failed|AndroidManifest\\.xml:\\d+: error|error: unexpected element <",
                    "The AndroidManifest has an error",
                    "The manifest, or an injected manifest snippet, is not valid or conflicts with a library's manifest.",
                    "Open the Manifest manager and check the injected activities, permissions and attributes mentioned in the log."),
            new Rule("build.ecj-unresolved-import", Severity.ERROR,
                    "The import [\\w.]+ cannot be resolved",
                    "Code imports a class that is not available",
                    "The class belongs to a library that is not enabled in this project, or the name is misspelled.",
                    "Enable the library in Library Manager (or add it as a local library) and check the import."),
            new Rule("build.ecj-unresolved-symbol", Severity.ERROR,
                    "\\S+ cannot be resolved( to a (?:type|variable))?$|The method \\S+ is undefined for the type|cannot find symbol",
                    "Code uses a name the compiler doesn't know",
                    "A class, variable or method used in your code or in a custom block does not exist, or its import is missing.",
                    "Open the file from the log (tap the underlined location), check the name and add the missing import. The editor's completion can add it."),
            new Rule("build.java-syntax", Severity.ERROR,
                    "Syntax error,? (?:on token|insert)|Syntax error on token|reached end of file while parsing",
                    "Code has a syntax error",
                    "A custom block, injected code or a Java file has a missing bracket, semicolon or keyword.",
                    "Open the file from the log (tap the underlined location). The editor underlines syntax errors as you type."),
            new Rule("build.kotlin", Severity.ERROR,
                    "^e: .*\\.kts?: \\(\\d+, \\d+\\)",
                    "A Kotlin file does not compile",
                    "The Kotlin compiler reported an error in the file named in the log.",
                    "Open the file from the log and fix the error at the given line."),
            new Rule("build.r8-missing-class", Severity.WARNING,
                    "R8: Missing class |Missing class [\\w.$]+ \\(referenced from|can't find referenced class",
                    "The code shrinker could not find a class that is referenced",
                    "A library refers to an optional class that is not included. Usually harmless, but R8 stops on it.",
                    "Add a -dontwarn rule for the class in the project's ProGuard rules, or set the code shrinker to a lower level."),
            new Rule("build.r8-failure", Severity.ERROR,
                    "^R8: |Compilation failed to complete|ProGuard.*(?:error|exception)",
                    "The code shrinker (R8) failed",
                    "R8 stopped while shrinking or optimising the app. Strict rules and some libraries can cause this.",
                    "Set the code shrinker to a lower level (or Off) in Code Shrinking, then add keep rules for what it removed."),
            new Rule("build.signing", Severity.ERROR,
                    "keystore was tampered with, or password was incorrect|Cannot recover key|Keystore (?:file )?(?:does not exist|not found)|Failed to load signer|no key with alias",
                    "Signing the app failed",
                    "The keystore file, its password, the key alias or the key password is wrong or missing.",
                    "Check the keystore path, alias and both passwords in App Settings > Build & Signing."),
            new Rule("build.download", Severity.WARNING,
                    "UnknownHostException|SocketTimeoutException|Failed to download|Could not resolve (?:all )?(?:files|dependencies)|Unable to resolve host",
                    "A library could not be downloaded",
                    "The device is offline, or the server holding the library did not answer.",
                    "Check your connection and try again. Libraries already downloaded are kept, so a second attempt resumes where this one stopped."),
            new Rule("build.class-not-found-tool", Severity.WARNING,
                    "NoClassDefFoundError|ClassNotFoundException",
                    "A class was missing while building",
                    "A tool in the build, or a library, needs a class that is not present.",
                    "Check that the libraries are complete (Library Manager > Repair), then build again.")
    );

    private BuildDoctor() {
    }

    /** Findings for a build log, most severe first. An empty or unrecognised log gives none. */
    public static List<Finding> diagnose(String log) {
        List<Finding> findings = new ArrayList<>();
        if (log == null || log.isBlank()) return findings;

        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(log);
            if (matcher.find()) {
                findings.add(new Finding(rule.id(), Category.BUILD, rule.severity(), rule.title(), rule.cause(), rule.solution(),
                        lineAround(log, matcher.start())));
            }
        }
        findings.sort((a, b) -> a.severity().compareTo(b.severity()));
        return findings;
    }

    /** The whole line a match starts on, trimmed and cut to a readable length. */
    private static String lineAround(String log, int position) {
        int start = log.lastIndexOf('\n', position - 1) + 1;
        int end = log.indexOf('\n', position);
        String line = log.substring(start, end < 0 ? log.length() : end).strip();
        return line.length() > 240 ? line.substring(0, 240) + "…" : line;
    }
}
