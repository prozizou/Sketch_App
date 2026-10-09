package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Security Scanner's engine: looks in a project's code and files for exposed secrets, risky code, excessive
 * permissions and libraries with known vulnerabilities.
 * <p>
 * It is a text scan, not a full security audit. Secrets are found by their well-known formats and are never copied
 * into a report: the evidence shows where, with the secret hidden. The vulnerable-library check uses a small built-in
 * list of well-known cases and is not a complete vulnerability database.
 */
public final class SecurityScanner {
    private static final int MAX_EVIDENCE = 3;

    private record SecretRule(String id, Severity severity, Pattern pattern, String title, String cause, String solution) {
    }

    private static final List<SecretRule> SECRET_RULES = List.of(
            new SecretRule("security.secret-private-key", Severity.ERROR, Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY"),
                    "A private key is inside the project",
                    "Anyone who gets the project, the APK or a backup can read the key and impersonate you.",
                    "Remove the key from the project, revoke it and create a new one, and load keys from a secure place at run time."),
            new SecretRule("security.secret-aws", Severity.ERROR, Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
                    "An AWS access key is inside the project",
                    "Access keys in an app can be extracted from the APK in minutes and used to run up costs or read data.",
                    "Revoke the key in AWS now, and give the app short-lived credentials from your server instead."),
            new SecretRule("security.secret-github", Severity.ERROR, Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b|\\bgithub_pat_[A-Za-z0-9_]{50,}\\b"),
                    "A GitHub token is inside the project",
                    "A token gives access to your repositories and can be found by anyone who reads the project or APK.",
                    "Revoke the token on GitHub and create a new one with the fewest permissions; keep it out of the project."),
            new SecretRule("security.secret-slack", Severity.ERROR, Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
                    "A Slack token is inside the project",
                    "A token lets anyone post and read as your app or bot.",
                    "Revoke it in Slack and keep the new one on a server."),
            new SecretRule("security.secret-stripe", Severity.ERROR, Pattern.compile("\\b[sr]k_live_[0-9a-zA-Z]{20,}\\b"),
                    "A live Stripe secret key is inside the project",
                    "A secret key can charge and refund customers; it must never leave your server.",
                    "Roll the key in the Stripe dashboard and move payment calls to a server."),
            new SecretRule("security.secret-telegram", Severity.ERROR, Pattern.compile("\\b\\d{8,10}:[A-Za-z0-9_-]{35}\\b"),
                    "A Telegram bot token is inside the project",
                    "Whoever has the token controls the bot.",
                    "Revoke it with @BotFather and keep the new one on a server."),
            new SecretRule("security.secret-google-api-key", Severity.WARNING, Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"),
                    "A Google API key is inside the project",
                    "Keys that ship in an app can be extracted. Firebase keys are meant to be public, but other Google keys can be abused if they are not restricted.",
                    "In the Google Cloud console restrict the key to your app's package name and signature and to the APIs it needs."),
            new SecretRule("security.secret-jwt", Severity.WARNING, Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
                    "A signed token (JWT) is inside the project",
                    "A token that is fixed in the app can be copied and reused until it expires.",
                    "Get tokens from your server at run time instead of embedding them.")
    );

    private static final Pattern GENERIC_SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token|private[_-]?key)[\"']?\\s*[=:]\\s*\"([^\"\\s]{8,})\"");
    private static final List<String> PLACEHOLDERS = List.of("your", "xxx", "changeme", "example", "placeholder", "sample", "test", "dummy", "<", "${", "@string", "%s", "****");

    private static final Pattern CLEARTEXT_URL = Pattern.compile("\"(http://(?!localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|schemas\\.android\\.com|www\\.w3\\.org|www\\.apache\\.org|xmlpull\\.org)[^\"\\s]+)\"");

    private record CodeRule(String id, Severity severity, Pattern pattern, String title, String cause, String solution) {
    }

    private static final List<CodeRule> CODE_RULES = List.of(
            new CodeRule("security.tls-trust-all", Severity.ERROR,
                    Pattern.compile("ALLOW_ALL_HOSTNAME_VERIFIER|void\\s+checkServerTrusted\\s*\\([^)]*\\)[^{;]*\\{\\s*\\}|boolean\\s+verify\\s*\\([^)]*\\)\\s*\\{\\s*return\\s+true\\s*;", Pattern.DOTALL),
                    "The app accepts any HTTPS certificate",
                    "Code that skips certificate or host name checks lets anyone on the same network read and change the app's traffic (a man-in-the-middle attack).",
                    "Remove the custom TrustManager or HostnameVerifier and use the system's default checks. For test servers, install their certificate on the device."),
            new CodeRule("security.world-accessible-file", Severity.ERROR,
                    Pattern.compile("\\bMODE_WORLD_(?:READABLE|WRITEABLE)\\b"),
                    "A file is made readable or writable by other apps",
                    "These modes were removed in Android 7 and now throw a SecurityException; they also exposed the data to every other app before.",
                    "Use MODE_PRIVATE and share data through a FileProvider or the system share sheet."),
            new CodeRule("security.webview-js-bridge", Severity.WARNING,
                    Pattern.compile("\\baddJavascriptInterface\\s*\\("),
                    "A WebView exposes Java code to JavaScript",
                    "Any page loaded in that WebView, including ones injected over an insecure connection, can call the exposed methods.",
                    "Only load pages you control over HTTPS, expose as little as possible, and mark exposed methods with @JavascriptInterface."),
            new CodeRule("security.webview-file-access", Severity.WARNING,
                    Pattern.compile("\\bsetAllow(?:UniversalAccessFromFileURLs|FileAccessFromFileURLs)\\s*\\(\\s*true"),
                    "A WebView lets local files read other files",
                    "With these settings a local HTML file can read any file the app can read and send it away.",
                    "Remove the setting, or serve local content through WebViewAssetLoader."),
            new CodeRule("security.weak-hash", Severity.WARNING,
                    Pattern.compile("MessageDigest\\s*\\.\\s*getInstance\\s*\\(\\s*\"(?:MD5|MD2|SHA-?1)\""),
                    "A weak hash function is used",
                    "MD5 and SHA-1 can be broken; they are not safe for passwords or for checking that data was not changed on purpose.",
                    "Use SHA-256 or better for integrity checks, and a password hash such as PBKDF2, bcrypt or Argon2 for passwords."),
            new CodeRule("security.weak-cipher", Severity.WARNING,
                    Pattern.compile("Cipher\\s*\\.\\s*getInstance\\s*\\(\\s*\"(?:DES|DESede|RC4|AES(?:/ECB[^\"]*)?|[^\"]*/ECB/[^\"]*)\""),
                    "A weak encryption mode is used",
                    "DES and RC4 are broken, and the ECB mode (the default for plain \"AES\") shows patterns of the data.",
                    "Use AES/GCM/NoPadding with a fresh random IV for each message.")
    );

    private static final Set<String> HIGH_RISK_PERMISSIONS = Set.of("SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "REQUEST_INSTALL_PACKAGES",
            "BIND_ACCESSIBILITY_SERVICE", "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "GET_ACCOUNTS", "MANAGE_ACCOUNTS", "USE_BIOMETRIC_UNUSED");
    private static final Set<String> DANGEROUS_PERMISSIONS = Set.of("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALENDAR",
            "WRITE_CALENDAR", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE", "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG", "CALL_PHONE", "READ_PHONE_STATE",
            "BODY_SENSORS", "ACTIVITY_RECOGNITION", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "POST_NOTIFICATIONS", "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN");
    private static final int MANY_DANGEROUS_PERMISSIONS = 8;

    /** A library version below which a well-known vulnerability applies. */
    private record Vulnerable(String artifact, String fixedIn, String problem) {
    }

    private static final List<Vulnerable> VULNERABLE_LIBRARIES = List.of(
            new Vulnerable("log4j-core", "2.17.1", "remote code execution (Log4Shell, CVE-2021-44228) and related flaws"),
            new Vulnerable("commons-text", "1.10.0", "remote code execution through string interpolation (Text4Shell, CVE-2022-42889)"),
            new Vulnerable("commons-collections", "3.2.2", "remote code execution when deserializing data (CVE-2015-7501)"),
            new Vulnerable("commons-collections4", "4.1", "remote code execution when deserializing data (CVE-2015-7501)"),
            new Vulnerable("fastjson", "1.2.83", "remote code execution when parsing JSON"),
            new Vulnerable("gson", "2.8.9", "denial of service when deserializing crafted data (CVE-2022-25647)"),
            new Vulnerable("snakeyaml", "2.0", "arbitrary code execution when parsing YAML (CVE-2022-1471)"),
            new Vulnerable("jsoup", "1.15.3", "cross-site scripting when cleaning HTML (CVE-2021-37714, CVE-2022-36033)"),
            new Vulnerable("httpclient", "4.5.13", "improper input validation (CVE-2020-13956)")
    );
    private static final Pattern VERSIONED_LIBRARY = Pattern.compile("^(?:[\\w.-]+:)?([A-Za-z][\\w.-]*?)[:-](\\d+(?:\\.\\d+){0,3})(?:[-.][A-Za-z][\\w.-]*)?$");

    private SecurityScanner() {
    }

    /**
     * @param files     code and other text files of the project (sources, resources, assets, configuration)
     * @param libraries enabled libraries by name, like {@code gson-2.8.5} or {@code com.google.code.gson:gson:2.8.5}
     */
    public static List<Finding> analyze(ProjectFacts facts, List<SourceFile> files, List<String> libraries) {
        List<Finding> findings = new ArrayList<>();
        List<SourceFile> all = new ArrayList<>(facts.sources());
        all.addAll(files);

        checkSecrets(all, findings);
        checkCleartext(all, findings);
        for (CodeRule rule : CODE_RULES) checkCode(rule, all, findings);
        checkPermissions(facts, findings);
        checkLibraries(libraries, findings);
        return findings;
    }

    // ---- secrets

    private static void checkSecrets(List<SourceFile> files, List<Finding> out) {
        for (SecretRule rule : SECRET_RULES) {
            List<String> hits = new ArrayList<>();
            int count = 0;
            for (SourceFile file : files) {
                Matcher matcher = rule.pattern().matcher(file.content());
                while (matcher.find()) {
                    count++;
                    if (hits.size() < MAX_EVIDENCE) hits.add(location(file, matcher.start()) + ": " + redact(matcher.group()));
                }
            }
            if (count > 0) {
                out.add(new Finding(rule.id(), Category.SECURITY, rule.severity(), rule.title() + suffix(count), rule.cause(), rule.solution(), String.join("\n", hits)));
            }
        }

        List<String> hits = new ArrayList<>();
        int count = 0;
        for (SourceFile file : files) {
            Matcher matcher = GENERIC_SECRET.matcher(file.content());
            while (matcher.find()) {
                String value = matcher.group(2);
                if (isPlaceholder(value)) continue;
                count++;
                if (hits.size() < MAX_EVIDENCE) hits.add(location(file, matcher.start()) + ": " + matcher.group(1) + " = " + redact(value));
            }
        }
        if (count > 0) {
            out.add(new Finding("security.secret-hardcoded", Category.SECURITY, Severity.WARNING, "Passwords or keys written into the code" + suffix(count),
                    "A value typed into the code ends up in the APK, where anyone can read it with free tools.",
                    "Do not keep secrets in the app. Ask the user to sign in, or let your server hold the secret and give the app a short-lived token.",
                    String.join("\n", hits)));
        }
    }

    private static boolean isPlaceholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String placeholder : PLACEHOLDERS) if (lower.contains(placeholder)) return true;
        return value.chars().distinct().count() <= 2;
    }

    /** Shows enough to recognise the value without exposing it. */
    static String redact(String secret) {
        String visible = secret.length() <= 8 ? "" : secret.substring(0, 4);
        return visible + "…[" + secret.length() + " characters hidden]";
    }

    // ---- code

    private static void checkCleartext(List<SourceFile> files, List<Finding> out) {
        List<String> hits = new ArrayList<>();
        int count = 0;
        for (SourceFile file : files) {
            Matcher matcher = CLEARTEXT_URL.matcher(file.content());
            while (matcher.find()) {
                count++;
                if (hits.size() < MAX_EVIDENCE) hits.add(location(file, matcher.start()) + ": " + matcher.group(1));
            }
        }
        if (count > 0) {
            out.add(new Finding("security.cleartext-http", Category.SECURITY, Severity.WARNING, "Addresses that use http instead of https" + suffix(count),
                    "Data sent over http can be read and changed by anyone on the network. Apps that target Android 9 (API 28) or higher also block it by default, so these requests fail.",
                    "Use https:// addresses. If the server has no HTTPS, set one up (free certificates exist) before releasing the app.",
                    String.join("\n", hits)));
        }
    }

    private static void checkCode(CodeRule rule, List<SourceFile> files, List<Finding> out) {
        List<String> hits = new ArrayList<>();
        int count = 0;
        for (SourceFile file : files) {
            Matcher matcher = rule.pattern().matcher(file.content());
            while (matcher.find()) {
                count++;
                if (hits.size() < MAX_EVIDENCE) hits.add(location(file, matcher.start()) + ": " + firstLine(matcher.group()));
            }
        }
        if (count > 0) {
            out.add(new Finding(rule.id(), Category.SECURITY, rule.severity(), rule.title() + suffix(count), rule.cause(), rule.solution(), String.join("\n", hits)));
        }
    }

    // ---- permissions

    private static void checkPermissions(ProjectFacts facts, List<Finding> out) {
        List<String> highRisk = new ArrayList<>();
        int dangerous = 0;
        for (String permission : facts.permissions()) {
            String name = permission.startsWith("android.permission.") ? permission.substring("android.permission.".length()) : permission;
            if (HIGH_RISK_PERMISSIONS.contains(name)) highRisk.add(name);
            if (DANGEROUS_PERMISSIONS.contains(name)) dangerous++;
        }
        java.util.Collections.sort(highRisk);
        if (!highRisk.isEmpty()) {
            out.add(new Finding("security.permission-high-risk", Category.SECURITY, Severity.WARNING, "Permissions that give a lot of power (" + highRisk.size() + ")",
                    "These permissions let an app draw over other apps, change system settings, install apps or read the phone's identity. Users are wary of them and a bug in the app has a bigger impact.",
                    "Remove the ones the app does not strictly need.", String.join(", ", highRisk)));
        }
        if (dangerous >= MANY_DANGEROUS_PERMISSIONS) {
            out.add(new Finding("security.permission-many", Category.SECURITY, Severity.INFO, "Many sensitive permissions (" + dangerous + ")",
                    "Every permission is something the user must trust you with, and something an attacker gains if the app is compromised.",
                    "Review the list in the Permission manager and remove what the app doesn't use.", null));
        }
    }

    // ---- libraries

    private static void checkLibraries(List<String> libraries, List<Finding> out) {
        List<String> hits = new ArrayList<>();
        String firstSolution = null;
        for (String library : libraries) {
            Matcher matcher = VERSIONED_LIBRARY.matcher(library.strip());
            if (!matcher.matches()) continue;
            String artifact = matcher.group(1);
            String version = matcher.group(2);
            for (Vulnerable known : VULNERABLE_LIBRARIES) {
                if (known.artifact().equals(artifact) && compareVersions(version, known.fixedIn()) < 0) {
                    hits.add(artifact + " " + version + ": " + known.problem() + " (fixed in " + known.fixedIn() + ")");
                    if (firstSolution == null) firstSolution = "Update " + artifact + " to " + known.fixedIn() + " or later.";
                }
            }
        }
        if (!hits.isEmpty()) {
            out.add(new Finding("security.vulnerable-library", Category.SECURITY, Severity.ERROR, "Libraries with known vulnerabilities (" + hits.size() + ")",
                    "These versions have publicly known security flaws. The check uses a short built-in list of well-known cases, so a clean result does not prove the other libraries are safe.",
                    firstSolution + (hits.size() > 1 ? " Do the same for the others listed." : ""), String.join("\n", hits)));
        }
    }

    /** Compares dotted numeric versions: 2.8.5 < 2.8.9, 1.10 > 1.9. */
    static int compareVersions(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int x = i < left.length ? parse(left[i]) : 0;
            int y = i < right.length ? parse(right[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- helpers

    private static String location(SourceFile file, int offset) {
        int line = 1;
        String content = file.content();
        for (int i = 0; i < offset && i < content.length(); i++) if (content.charAt(i) == '\n') line++;
        return file.name() + ":" + line;
    }

    private static String firstLine(String text) {
        String line = text.strip().split("\n")[0];
        return line.length() > 100 ? line.substring(0, 100) + "…" : line;
    }

    private static String suffix(int count) {
        return count > 1 ? " (" + count + ")" : "";
    }
}
