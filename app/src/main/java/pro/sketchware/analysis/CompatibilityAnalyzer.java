package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.analysis.ViewFacts.Kind;

/**
 * The Android Compatibility Center's engine: looks at a project's SDK levels, permissions, custom code, libraries,
 * native libraries and screens, and reports what is likely to break or misbehave on current Android versions.
 * <p>
 * It reads text and numbers only; it does not run anything. API levels quoted in the messages are the ones in which
 * Android made the change. The checks on screens (accessibility, right-to-left, dark mode) only see widgets whose
 * properties are stored in the project; things set by injected code are not seen.
 */
public final class CompatibilityAnalyzer {
    /** Google Play asks new apps and updates to target a recent version; this is the level it asked for in 2025. */
    static final int PLAY_TARGET_SDK = 35;
    static final int MIN_TOUCH_TARGET_DP = 48;
    static final double MIN_CONTRAST = 4.5;
    static final double MIN_CONTRAST_LARGE_TEXT = 3.0;
    static final int LARGE_TEXT_SP = 18;
    static final int SMALL_TEXT_SP = 12;
    private static final int MAX_EVIDENCE_LINES = 3;

    private record CodeRule(String id, Severity severity, Severity severityOnNewTarget, int newTargetFrom, Pattern pattern,
                            String title, String cause, String solution) {
    }

    private static final List<CodeRule> CODE_RULES = List.of(
            new CodeRule("compat.async-task", Severity.INFO, Severity.INFO, 0, Pattern.compile("\\bAsyncTask\\b"),
                    "AsyncTask is deprecated",
                    "AsyncTask was deprecated in Android 11 (API 30); it still works but is no longer maintained.",
                    "Use a background thread with a Handler or an Executor, or the app's built-in background blocks."),
            new CodeRule("compat.progress-dialog", Severity.INFO, Severity.INFO, 0, Pattern.compile("\\bProgressDialog\\b"),
                    "ProgressDialog is deprecated",
                    "ProgressDialog was deprecated in Android 8 (API 26) because it blocks the screen.",
                    "Show a ProgressBar in the layout, or a Material dialog with a progress indicator."),
            new CodeRule("compat.handler-no-looper", Severity.INFO, Severity.INFO, 0, Pattern.compile("new\\s+Handler\\s*\\(\\s*\\)"),
                    "Handler without a Looper is deprecated",
                    "The no-argument Handler constructor was deprecated in Android 11 (API 30) because it can attach to the wrong thread.",
                    "Pass the looper explicitly: new Handler(Looper.getMainLooper())."),
            new CodeRule("compat.external-storage-dir", Severity.INFO, Severity.WARNING, 29, Pattern.compile("Environment\\s*\\.\\s*getExternalStorageDirectory\\s*\\("),
                    "Direct access to shared storage is restricted",
                    "getExternalStorageDirectory() was deprecated in Android 10 (API 29). Apps that target Android 11 (API 30) or higher can no longer read and write most of shared storage by path.",
                    "Use the app-specific folders (getExternalFilesDir) or the system file picker and MediaStore."),
            new CodeRule("compat.back-pressed", Severity.INFO, Severity.INFO, 0, Pattern.compile("void\\s+onBackPressed\\s*\\("),
                    "onBackPressed is deprecated",
                    "Overriding onBackPressed was deprecated in Android 13 (API 33); predictive back needs the OnBackPressedDispatcher.",
                    "Register an OnBackPressedCallback with the activity's onBackPressedDispatcher."),
            new CodeRule("compat.wifi-set-enabled", Severity.INFO, Severity.WARNING, 29, Pattern.compile("\\.\\s*setWifiEnabled\\s*\\("),
                    "WifiManager.setWifiEnabled does nothing on Android 10",
                    "From Android 10 (API 29) apps can no longer switch Wi-Fi on or off; the call is ignored.",
                    "Send the user to the Wi-Fi settings panel with Settings.Panel.ACTION_INTERNET_CONNECTIVITY."),
            new CodeRule("compat.device-identifiers", Severity.INFO, Severity.WARNING, 29, Pattern.compile("\\.\\s*(?:getDeviceId|getImei|getMeid|getSimSerialNumber|getSubscriberId)\\s*\\(|\\bBuild\\s*\\.\\s*SERIAL\\b"),
                    "Hardware identifiers are not available to apps",
                    "From Android 10 (API 29) the IMEI, serial number and similar identifiers are restricted to system apps; the calls throw a SecurityException or return nothing.",
                    "Use an identifier your app creates itself (a random UUID kept in its storage), or the advertising ID if you need one.")
    );

    private static final Pattern PENDING_INTENT = Pattern.compile("PendingIntent\\s*\\.\\s*get(?:Activity|Activities|Broadcast|Service|ForegroundService)\\s*\\(");
    private static final Pattern SUPPORT_IMPORT = Pattern.compile("^\\s*import\\s+android\\.support\\.", Pattern.MULTILINE);
    private static final Pattern NOTIFICATION_USE = Pattern.compile("\\bNotificationManager(?:Compat)?\\b|\\bNotificationCompat\\b");

    private CompatibilityAnalyzer() {
    }

    public static List<Finding> analyze(ProjectFacts facts) {
        List<Finding> findings = new ArrayList<>();
        checkSdk(facts, findings);
        checkPermissions(facts, findings);
        checkCode(facts, findings);
        checkNativeLibraries(facts, findings);
        checkScreens(facts, findings);
        return findings;
    }

    // ---- SDK levels

    private static void checkSdk(ProjectFacts facts, List<Finding> out) {
        int min = facts.minSdk();
        int target = facts.targetSdk();
        if (min > 0 && target > 0 && min > target) {
            out.add(new Finding("compat.min-above-target", Category.COMPATIBILITY, Severity.ERROR,
                    "Minimum SDK (" + min + ") is higher than target SDK (" + target + ")",
                    "An app cannot require a newer Android than the one it is built for; the build is refused.",
                    "Lower the minimum SDK or raise the target SDK in the project's build settings.", null));
        }
        if (target > 0 && target < 33) {
            out.add(new Finding("compat.target-sdk-old", Category.COMPATIBILITY, Severity.WARNING,
                    "Target SDK " + target + " is old",
                    "Google Play only accepts new apps and updates that target a recent version (" + PLAY_TARGET_SDK + " or higher since 2025), and Android applies compatibility behaviour to apps that target old versions.",
                    "Raise the target SDK in the project's build settings, then test storage, notifications and background work, which behave differently on newer versions.",
                    "targetSdk = " + target));
        }
        if (min > 0 && min < 21 && facts.usesAndroidX()) {
            out.add(new Finding("compat.min-sdk-low-androidx", Category.COMPATIBILITY, Severity.WARNING,
                    "Minimum SDK " + min + " is too low for the AndroidX and Material libraries",
                    "Recent versions of AndroidX AppCompat and Material Components need Android 5.0 (API 21) or higher.",
                    "Set the minimum SDK to 21 or higher, or use older versions of the libraries.", "minSdk = " + min));
        }
        if (target >= 35) {
            out.add(new Finding("compat.edge-to-edge", Category.COMPATIBILITY, Severity.INFO,
                    "Android 15 draws the app edge to edge",
                    "When an app targets API 35 or higher, Android 15 lets it draw behind the status bar and the navigation bar, so content at the screen edges can end up under them.",
                    "Test every screen on Android 15. Add padding for the system bars (WindowInsets) to screens whose content touches the top or bottom edge.",
                    "targetSdk = " + target));
        }
    }

    // ---- permissions

    private static void checkPermissions(ProjectFacts facts, List<Finding> out) {
        int target = facts.targetSdk();
        boolean unknownTarget = target == 0;

        if (has(facts, "READ_EXTERNAL_STORAGE") && (unknownTarget || target >= 33)) {
            out.add(permission("compat.perm-read-storage", Severity.WARNING, "READ_EXTERNAL_STORAGE no longer gives access to media",
                    "From Android 13 (API 33) the permission has no effect for photos, videos and audio.",
                    "Ask for READ_MEDIA_IMAGES, READ_MEDIA_VIDEO or READ_MEDIA_AUDIO instead, or use the system photo picker, which needs no permission.",
                    "android.permission.READ_EXTERNAL_STORAGE"));
        }
        if (has(facts, "WRITE_EXTERNAL_STORAGE") && (unknownTarget || target >= 30)) {
            out.add(permission("compat.perm-write-storage", Severity.INFO, "WRITE_EXTERNAL_STORAGE has no effect on Android 11 and later",
                    "From Android 11 (API 30), apps that target it use scoped storage and the permission is ignored.",
                    "Write to the app's own folders, or ask the user where to save with the system file picker.",
                    "android.permission.WRITE_EXTERNAL_STORAGE"));
        }
        if (has(facts, "MANAGE_EXTERNAL_STORAGE")) {
            out.add(permission("compat.perm-manage-storage", Severity.WARNING, "All-files access is restricted",
                    "MANAGE_EXTERNAL_STORAGE needs the user to grant it in settings, and Google Play only allows it for a few kinds of app.",
                    "Use the system file picker or MediaStore if the app does not need to manage all files.",
                    "android.permission.MANAGE_EXTERNAL_STORAGE"));
        }
        if (has(facts, "ACCESS_BACKGROUND_LOCATION")) {
            out.add(permission("compat.perm-background-location", Severity.WARNING, "Background location needs a justification",
                    "Since Android 10 background location is a separate permission, asked after foreground location, and Google Play reviews apps that use it.",
                    "Remove it if the app only needs location while it is open.", "android.permission.ACCESS_BACKGROUND_LOCATION"));
        }
        for (String sensitive : List.of("READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS")) {
            if (has(facts, sensitive)) {
                out.add(permission("compat.perm-restricted-" + sensitive.toLowerCase(Locale.ROOT).replace('_', '-'), Severity.WARNING,
                        sensitive + " is restricted by Google Play",
                        "Google Play only allows SMS and call-log permissions for apps whose main purpose needs them (a default SMS or phone app).",
                        "Use another way to reach the goal (the system share sheet, an intent to the dialer or messages app), or remove the permission.",
                        "android.permission." + sensitive));
            }
        }
        if (has(facts, "QUERY_ALL_PACKAGES")) {
            out.add(permission("compat.perm-query-all-packages", Severity.WARNING, "QUERY_ALL_PACKAGES is restricted by Google Play",
                    "Since Android 11 apps cannot list other apps unless they declare it, and Google Play allows it only for a few kinds of app.",
                    "Declare only the specific packages or intents you need in a queries element of the manifest.",
                    "android.permission.QUERY_ALL_PACKAGES"));
        }
        boolean usesNotifications = facts.sources().stream().anyMatch(source -> NOTIFICATION_USE.matcher(source.content()).find());
        if (usesNotifications && (unknownTarget || target >= 33) && !has(facts, "POST_NOTIFICATIONS")) {
            out.add(new Finding("compat.perm-post-notifications", Category.COMPATIBILITY, Severity.WARNING,
                    "Notifications need the POST_NOTIFICATIONS permission",
                    "From Android 13 (API 33) an app must declare POST_NOTIFICATIONS and ask the user before it can show notifications.",
                    "Add the POST_NOTIFICATIONS permission and request it at run time before showing the first notification.", null));
        }
        if (has(facts, "ACCESS_FINE_LOCATION") && !has(facts, "ACCESS_COARSE_LOCATION") && (unknownTarget || target >= 31)) {
            out.add(permission("compat.perm-coarse-location", Severity.INFO, "Fine location should come with coarse location",
                    "From Android 12 (API 31) users can give only approximate location; an app that asks for fine location must also declare coarse location.",
                    "Add ACCESS_COARSE_LOCATION next to ACCESS_FINE_LOCATION.", "android.permission.ACCESS_FINE_LOCATION"));
        }
    }

    private static boolean has(ProjectFacts facts, String shortName) {
        return facts.permissions().contains("android.permission." + shortName) || facts.permissions().contains(shortName);
    }

    private static Finding permission(String id, Severity severity, String title, String cause, String solution, String evidence) {
        return new Finding(id, Category.COMPATIBILITY, severity, title, cause, solution, evidence);
    }

    // ---- custom code

    private static void checkCode(ProjectFacts facts, List<Finding> out) {
        int target = facts.targetSdk();
        for (CodeRule rule : CODE_RULES) {
            List<String> hits = new ArrayList<>();
            List<Location> at = new ArrayList<>();
            int count = 0;
            for (SourceFile source : facts.sources()) {
                String[] lines = source.content().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (isCommentLine(lines[i])) continue;
                    if (rule.pattern().matcher(stripLineComment(lines[i])).find()) {
                        count++;
                        if (hits.size() < MAX_EVIDENCE_LINES) hits.add(source.name() + ":" + (i + 1) + ": " + lines[i].strip());
                        at.add(Location.source(source.openPath(), source.name(), i + 1));
                    }
                }
            }
            if (count == 0) continue;
            // Rules that get worse for newer targets use the stronger severity when the target is that new or unknown
            boolean newTarget = rule.newTargetFrom() > 0 && (target == 0 || target >= rule.newTargetFrom());
            Severity severity = newTarget ? rule.severityOnNewTarget() : rule.severity();
            out.add(new Finding(rule.id(), Category.COMPATIBILITY, severity, rule.title() + countSuffix(count),
                    rule.cause(), rule.solution(), String.join("\n", hits), at));
        }
        checkPendingIntents(facts, out);
        checkSupportLibrary(facts, out);
    }

    private static void checkPendingIntents(ProjectFacts facts, List<Finding> out) {
        List<String> hits = new ArrayList<>();
        List<Location> at = new ArrayList<>();
        int count = 0;
        for (SourceFile source : facts.sources()) {
            Matcher matcher = PENDING_INTENT.matcher(source.content());
            while (matcher.find()) {
                int end = source.content().indexOf(';', matcher.end());
                String call = source.content().substring(matcher.start(), end < 0 ? Math.min(source.content().length(), matcher.end() + 300) : end);
                if (call.contains("FLAG_IMMUTABLE") || call.contains("FLAG_MUTABLE")) continue;
                count++;
                at.add(Location.source(source.openPath(), source.name(), Location.lineOf(source.content(), matcher.start())));
                if (hits.size() < MAX_EVIDENCE_LINES) {
                    int line = 1 + (int) source.content().substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                    hits.add(source.name() + ":" + line + ": " + call.strip().split("\n")[0]);
                }
            }
        }
        if (count == 0) return;
        boolean crashes = facts.targetSdk() == 0 || facts.targetSdk() >= 31;
        out.add(new Finding("compat.pending-intent-flag", Category.COMPATIBILITY, crashes ? Severity.ERROR : Severity.INFO,
                "PendingIntent without FLAG_IMMUTABLE or FLAG_MUTABLE" + countSuffix(count),
                "Apps that target Android 12 (API 31) or higher crash with an IllegalArgumentException when a PendingIntent is created without saying whether it is mutable.",
                "Add PendingIntent.FLAG_IMMUTABLE to the flags (or FLAG_MUTABLE if the intent must be changed by the receiver).",
                String.join("\n", hits), at));
    }

    private static void checkSupportLibrary(ProjectFacts facts, List<Finding> out) {
        List<String> hits = new ArrayList<>();
        List<Location> at = new ArrayList<>();
        int count = 0;
        for (SourceFile source : facts.sources()) {
            Matcher matcher = SUPPORT_IMPORT.matcher(source.content());
            while (matcher.find()) {
                count++;
                at.add(Location.source(source.openPath(), source.name(), Location.lineOf(source.content(), matcher.start())));
                if (hits.size() < MAX_EVIDENCE_LINES) {
                    int line = 1 + (int) source.content().substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                    hits.add(source.name() + ":" + line + ": " + matcher.group().strip());
                }
            }
        }
        if (count == 0) return;
        out.add(new Finding("compat.support-library", Category.COMPATIBILITY, facts.usesAndroidX() ? Severity.ERROR : Severity.WARNING,
                "Code uses the old Android Support Library" + countSuffix(count),
                "The android.support packages were replaced by AndroidX in 2018. They are not included in builds that use AndroidX, so the code does not compile.",
                "Replace imports starting with android.support. by their androidx. equivalents (android.support.v7.app.AppCompatActivity becomes androidx.appcompat.app.AppCompatActivity).",
                String.join("\n", hits), at));
    }

    private static boolean isCommentLine(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static String countSuffix(int count) {
        return count > 1 ? " (" + count + " uses)" : "";
    }

    // ---- native libraries

    private static void checkNativeLibraries(ProjectFacts facts, List<Finding> out) {
        if (facts.nativeAbis().isEmpty()) return;
        boolean has64 = facts.nativeAbis().contains("arm64-v8a") || facts.nativeAbis().contains("x86_64");
        boolean hasArm32 = facts.nativeAbis().contains("armeabi-v7a");
        String abis = String.join(", ", new java.util.TreeSet<>(facts.nativeAbis()));
        if (!has64) {
            out.add(new Finding("compat.abi-no-64bit", Category.COMPATIBILITY, Severity.ERROR,
                    "Native libraries have no 64-bit version",
                    "Google Play requires 64-bit native code, and many recent phones can only run 64-bit apps, where this app would crash or not install.",
                    "Add the arm64-v8a build of every native library (.so) used by the project.", "ABIs: " + abis));
        } else if (!hasArm32 && facts.nativeAbis().contains("arm64-v8a")) {
            out.add(new Finding("compat.abi-no-arm32", Category.COMPATIBILITY, Severity.INFO,
                    "Native libraries have no 32-bit ARM version",
                    "Older phones that only run 32-bit apps cannot use this app.",
                    "Add the armeabi-v7a build of the native libraries if those phones matter to you.", "ABIs: " + abis));
        }
    }

    // ---- screens: accessibility, right-to-left, dark mode

    private static void checkScreens(ProjectFacts facts, List<Finding> out) {
        List<String> noDescription = new ArrayList<>();
        List<String> smallTargets = new ArrayList<>();
        List<String> lowContrast = new ArrayList<>();
        List<String> smallText = new ArrayList<>();
        List<String> leftRight = new ArrayList<>();
        List<String> hardcodedText = new ArrayList<>();
        int lightLiteralBackgrounds = 0;
        java.util.Map<String, List<Location>> at = new java.util.HashMap<>();

        for (ViewFacts view : facts.views()) {
            if (view.kind() == Kind.IMAGE && !view.hasContentDescription()) {
                mark(at, "noDescription", view);
                noDescription.add(view.label());
            }
            if (view.clickable() && ((view.widthDp() >= 0 && view.widthDp() < MIN_TOUCH_TARGET_DP)
                    || (view.heightDp() >= 0 && view.heightDp() < MIN_TOUCH_TARGET_DP))) {
                mark(at, "smallTargets", view);
                smallTargets.add(view.label() + " (" + describeSize(view) + ")");
            }
            if (view.textColor() != null && view.backgroundColor() != null) {
                double ratio = contrastRatio(view.textColor(), view.backgroundColor());
                double needed = view.textSizeSp() >= LARGE_TEXT_SP ? MIN_CONTRAST_LARGE_TEXT : MIN_CONTRAST;
                if (ratio < needed) {
                    mark(at, "lowContrast", view);
                    lowContrast.add(view.label() + String.format(Locale.ROOT, " (%.1f:1)", ratio));
                }
            }
            if (view.textSizeSp() > 0 && view.textSizeSp() < SMALL_TEXT_SP) {
                mark(at, "smallText", view);
                smallText.add(view.label() + " (" + view.textSizeSp() + "sp)");
            }
            if (view.marginLeft() != view.marginRight() || view.paddingLeft() != view.paddingRight()) {
                mark(at, "leftRight", view);
                leftRight.add(view.label());
            }
            if (view.hardcodedText()) {
                mark(at, "hardcodedText", view);
                hardcodedText.add(view.label());
            }
            if (view.backgroundColor() != null && luminance(view.backgroundColor()) > 0.8) {
                lightLiteralBackgrounds++;
                mark(at, "light", view);
            }
        }

        if (!noDescription.isEmpty()) {
            out.add(new Finding("a11y.content-description", Category.COMPATIBILITY, Severity.WARNING,
                    "Images without a description (" + noDescription.size() + ")",
                    "Screen readers such as TalkBack cannot say what an image is for when it has no contentDescription.",
                    "Add a contentDescription to each informative image (through the widget's injected attributes), or mark purely decorative images as not important for accessibility.",
                    sample(noDescription)).withLocations(at.get("noDescription")));
        }
        if (!smallTargets.isEmpty()) {
            out.add(new Finding("a11y.touch-target", Category.COMPATIBILITY, Severity.WARNING,
                    "Tappable widgets smaller than 48 dp (" + smallTargets.size() + ")",
                    "Small buttons are hard to hit for everyone, and difficult or impossible for people with motor impairments. Android's guideline is at least 48 x 48 dp.",
                    "Make the widget at least 48 dp wide and tall, or keep it small and add padding or a larger touch area around it.",
                    sample(smallTargets)).withLocations(at.get("smallTargets")));
        }
        if (!lowContrast.isEmpty()) {
            out.add(new Finding("a11y.contrast", Category.COMPATIBILITY, Severity.WARNING,
                    "Text with too little contrast (" + lowContrast.size() + ")",
                    "Text that is close in colour to its background is hard to read, especially in sunlight or for low vision. The guideline is a ratio of at least 4.5:1 (3:1 for large text).",
                    "Darken the text or lighten the background (or the reverse) until the ratio reaches the guideline.",
                    sample(lowContrast)).withLocations(at.get("lowContrast")));
        }
        if (!smallText.isEmpty()) {
            out.add(new Finding("a11y.small-text", Category.COMPATIBILITY, Severity.INFO,
                    "Text smaller than 12 sp (" + smallText.size() + ")",
                    "Very small text is hard to read and does not help people who rely on large fonts.",
                    "Use 12 sp or more for body text.", sample(smallText)).withLocations(at.get("smallText")));
        }
        if (!leftRight.isEmpty()) {
            out.add(new Finding("rtl.asymmetric-spacing", Category.COMPATIBILITY, Severity.INFO,
                    "Different left and right spacing (" + leftRight.size() + " widgets)",
                    "Left and right margins and padding do not flip in right-to-left languages such as Arabic and Hebrew, so the layout looks mirrored wrongly there.",
                    "If the app should support right-to-left languages, use start and end margins and padding instead of left and right (through injected attributes) and declare supportsRtl in the manifest.",
                    sample(leftRight)).withLocations(at.get("leftRight")));
        }
        if (!hardcodedText.isEmpty()) {
            out.add(new Finding("quality.hardcoded-text", Category.QUALITY, Severity.INFO,
                    "Text typed into widgets instead of string resources (" + hardcodedText.size() + ")",
                    "Text that is not a string resource cannot be translated and is harder to change in one place.",
                    "Move the texts to the string resources of the project (Resources editor) and refer to them.", sample(hardcodedText)).withLocations(at.get("hardcodedText")));
        }
        if (!facts.hasNightResources() && lightLiteralBackgrounds >= 1) {
            out.add(new Finding("dark.fixed-light-colors", Category.COMPATIBILITY, lightLiteralBackgrounds >= 5 ? Severity.WARNING : Severity.INFO,
                    "Light colours are fixed and there are no dark-mode resources (" + lightLiteralBackgrounds + " widgets)",
                    "Widgets with a fixed light background stay light when the phone is in dark mode, and the project has no values-night resources, so the screens glare next to other dark apps.",
                    "Use colour resources and add a night version of them (values-night), or choose a theme that follows the system.", null).withLocations(at.get("light")));
        }
    }

    private static void mark(java.util.Map<String, List<Location>> at, String key, ViewFacts view) {
        at.computeIfAbsent(key, k -> new ArrayList<>()).add(Location.widget(view.screen(), view.id()));
    }

    private static String describeSize(ViewFacts view) {
        return (view.widthDp() >= 0 ? view.widthDp() + "dp" : "auto") + " x " + (view.heightDp() >= 0 ? view.heightDp() + "dp" : "auto");
    }

    private static String sample(List<String> items) {
        List<String> shown = items.subList(0, Math.min(items.size(), 5));
        return String.join(", ", shown) + (items.size() > shown.size() ? ", …" : "");
    }

    // ---- colour maths (WCAG)

    public static double luminance(int argb) {
        return 0.2126 * linear((argb >> 16) & 0xFF) + 0.7152 * linear((argb >> 8) & 0xFF) + 0.0722 * linear(argb & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double contrastRatio(int foreground, int background) {
        double a = luminance(foreground);
        double b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
