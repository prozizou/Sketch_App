package pro.sketchware.evolve;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a community post may contain, and the cleaning of free text, before anything is published. These are the
 * app-side twin of the database rules: the rules refuse what breaks the limits, the app says why before sending.
 * Pure Java (the Java check uses JavaParser, which the app already bundles), so it is tested directly.
 */
public final class PostRules {

    public static final int MAX_TAGS = 5;
    public static final int MIN_TAG_LENGTH = 2;
    public static final int MAX_TAG_LENGTH = 24;
    public static final int MAX_VERSION_LENGTH = 16;
    public static final int MIN_SDK_LOWEST = 21;
    public static final int MIN_SDK_HIGHEST = 40;
    public static final int MAX_DEPENDENCIES = 10;
    public static final int MAX_DEPENDENCY_LENGTH = 120;
    public static final int MAX_SCREENSHOTS = 4;
    public static final int MAX_SCREENSHOT_URL_LENGTH = 600;
    public static final int MAX_FILE_NAME_LENGTH = 64;
    public static final int MAX_COMMENT_LENGTH = 500;
    /** Largest screenshot the app uploads, after it was scaled down. */
    public static final int MAX_SCREENSHOT_BYTES = 400 * 1024;

    private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9+#._-]*");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._+-]*");
    private static final Pattern DEPENDENCY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:+@/-]*");
    private static final Pattern FILE_BASE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATS = Pattern.compile("(.)\\1{14,}", Pattern.DOTALL);
    private static final Set<String> STORAGE_HOSTS = Set.of("firebasestorage.googleapis.com");

    /** Why a post can't be published, or {@link #NONE}. */
    public enum Problem {
        NONE,
        TOO_MANY_TAGS,
        TAG_INVALID,
        VERSION_INVALID,
        MIN_SDK_INVALID,
        TOO_MANY_DEPENDENCIES,
        DEPENDENCY_INVALID,
        TOO_MANY_SCREENSHOTS,
        SCREENSHOT_INVALID,
        CODE_EMPTY,
        CODE_TOO_LARGE,
        JAVA_SYNTAX,
        COMPONENT_INVALID,
        FILE_NAME_INVALID
    }

    private PostRules() {
    }

    /** @return the tags lowercased, trimmed, without duplicates or empties, in first-use order */
    public static List<String> normalizeTags(List<String> tags) {
        Set<String> out = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) continue;
            String clean = tag.trim().toLowerCase(Locale.ROOT);
            while (clean.startsWith("#")) clean = clean.substring(1);
            if (!clean.isEmpty()) out.add(clean);
        }
        return new ArrayList<>(out);
    }

    /** @return the tags typed as one text, separated by spaces or commas */
    public static List<String> splitTags(String text) {
        List<String> tags = new ArrayList<>();
        if (text != null) {
            for (String part : text.split("[,\\s]+")) {
                tags.add(part);
            }
        }
        return normalizeTags(tags);
    }

    public static Problem checkTags(List<String> tags) {
        if (tags.size() > MAX_TAGS) return Problem.TOO_MANY_TAGS;
        for (String tag : tags) {
            if (tag.length() < MIN_TAG_LENGTH || tag.length() > MAX_TAG_LENGTH || !TAG.matcher(tag).matches()) {
                return Problem.TAG_INVALID;
            }
        }
        return Problem.NONE;
    }

    /** @return {@link Problem#NONE} for an empty version (it is optional) or a short word like 1.2 or 2.0-beta */
    public static Problem checkVersion(String version) {
        if (version == null || version.isEmpty()) return Problem.NONE;
        return version.length() <= MAX_VERSION_LENGTH && VERSION.matcher(version).matches() ? Problem.NONE : Problem.VERSION_INVALID;
    }

    /** @return {@link Problem#NONE} for 0 (not given) or a plausible Android API level */
    public static Problem checkMinSdk(int minSdk) {
        return minSdk == 0 || minSdk >= MIN_SDK_LOWEST && minSdk <= MIN_SDK_HIGHEST ? Problem.NONE : Problem.MIN_SDK_INVALID;
    }

    public static Problem checkDependencies(List<String> dependencies) {
        if (dependencies.size() > MAX_DEPENDENCIES) return Problem.TOO_MANY_DEPENDENCIES;
        for (String dependency : dependencies) {
            if (dependency.length() > MAX_DEPENDENCY_LENGTH || !DEPENDENCY.matcher(dependency).matches()) {
                return Problem.DEPENDENCY_INVALID;
            }
        }
        return Problem.NONE;
    }

    /** @return the dependencies typed one per line (or separated by commas), trimmed, without duplicates */
    public static List<String> splitDependencies(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,\\n]+")) {
                String clean = part.trim();
                if (!clean.isEmpty()) out.add(clean);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Screenshots are only ever shown from the app's own Firebase Storage, over https, so a post can't make the
     * app fetch from an address of the author's choosing.
     */
    public static Problem checkScreenshots(List<String> urls) {
        if (urls.size() > MAX_SCREENSHOTS) return Problem.TOO_MANY_SCREENSHOTS;
        for (String url : urls) {
            if (!isStorageUrl(url)) return Problem.SCREENSHOT_INVALID;
        }
        return Problem.NONE;
    }

    public static boolean isStorageUrl(String url) {
        if (url == null || url.length() > MAX_SCREENSHOT_URL_LENGTH || !url.startsWith("https://")) return false;
        int hostEnd = url.indexOf('/', 8);
        String host = hostEnd < 0 ? url.substring(8) : url.substring(8, hostEnd);
        return STORAGE_HOSTS.contains(host) || host.endsWith(".firebasestorage.app");
    }

    /**
     * @return the first reason {@code code} can't be shared as {@code kind}: it must not be empty or too large, Java
     * must parse, a component must be a list of components with the keys the component manager needs. Kotlin has no parser here, so it only has the size limits.
     */
    public static Problem checkCode(SharedKind kind, String code) {
        if (code == null || code.isBlank()) return Problem.CODE_EMPTY;
        if (code.length() > SharedBlockRules.MAX_JSON_LENGTH) return Problem.CODE_TOO_LARGE;
        return switch (kind) {
            case JAVA -> javaParses(code) ? Problem.NONE : Problem.JAVA_SYNTAX;
            case COMPONENT -> ImportPlanner.readComponents(code, new ArrayList<>(), new ArrayList<>()) ? Problem.NONE : Problem.COMPONENT_INVALID;
            default -> Problem.NONE;
        };
    }

    private static boolean javaParses(String code) {
        try {
            ParserConfiguration configuration = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(code);
            return result.isSuccessful() && result.getResult().isPresent();
        } catch (RuntimeException | StackOverflowError e) {
            return false;
        }
    }

    /** @return whether {@code fileName} is a plain file name with the extension {@code kind} is saved as */
    public static Problem checkFileName(SharedKind kind, String fileName) {
        if (!kind.isFile() || kind == SharedKind.VIEW) return Problem.NONE;
        if (fileName == null || fileName.length() > MAX_FILE_NAME_LENGTH) return Problem.FILE_NAME_INVALID;
        String suffix = "." + kind.extension();
        if (!fileName.endsWith(suffix)) return Problem.FILE_NAME_INVALID;
        String base = fileName.substring(0, fileName.length() - suffix.length());
        return FILE_BASE.matcher(base).matches() ? Problem.NONE : Problem.FILE_NAME_INVALID;
    }

    /** @return a file name suggested for {@code name}: letters, digits and underscores, first letter capital, with the extension */
    public static String suggestFileName(SharedKind kind, String name) {
        StringBuilder base = new StringBuilder();
        boolean upper = true;
        for (char c : (name == null ? "" : name).toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                base.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (base.length() == 0 || !Character.isLetter(base.charAt(0))) {
            base.insert(0, "Shared");
        }
        String cut = base.length() > MAX_FILE_NAME_LENGTH - 8 ? base.substring(0, MAX_FILE_NAME_LENGTH - 8) : base.toString();
        return cut + "." + kind.extension();
    }

    /** @return the distinct imports of a Java or Kotlin source that are not part of the Android or Java platform */
    public static List<String> thirdPartyImports(String code) {
        Set<String> imports = new LinkedHashSet<>();
        if (code != null) {
            for (String line : code.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) continue;
                String name = trimmed.substring(7).replace(";", "").trim();
                if (name.startsWith("static ")) name = name.substring(7).trim();
                if (name.startsWith("android.") || name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("kotlin.") || name.startsWith("dalvik.") || name.startsWith("org.json.")
                        || name.startsWith("org.w3c.") || name.startsWith("org.xml.")) {
                    continue;
                }
                imports.add(name);
            }
        }
        return new ArrayList<>(imports);
    }

    // ---- free text: comments, descriptions

    /**
     * @return {@code text} as it is stored: control characters (other than line breaks and tabs) and invisible
     * direction marks removed, three or more line breaks cut to two, and trimmed. The text is only ever shown as plain
     * text, never as HTML or Markdown, so nothing in it can run or restyle the page.
     */
    public static String clean(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') continue;
            if (c == '\n' || c == '\t') {
                out.append(c);
            } else if (!Character.isISOControl(c) && !isBidiMark(c)) {
                out.append(c);
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static boolean isBidiMark(char c) {
        return c >= '‪' && c <= '‮' || c >= '⁦' && c <= '⁩' || c == '‎' || c == '‏';
    }

    /** What is wrong with a comment, or {@link #NONE}: it can't be empty, too long or look like spam. */
    public enum CommentProblem {NONE, EMPTY, TOO_LONG, SPAM}

    public static CommentProblem checkComment(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) return CommentProblem.EMPTY;
        if (clean.length() > MAX_COMMENT_LENGTH) return CommentProblem.TOO_LONG;
        return looksLikeSpam(clean) ? CommentProblem.SPAM : CommentProblem.NONE;
    }

    /** Spam, as far as the app can tell: several links, or a long run of the same character. */
    public static boolean looksLikeSpam(String text) {
        int links = 0;
        java.util.regex.Matcher matcher = URL.matcher(text);
        while (matcher.find()) links++;
        return links > 2 || REPEATS.matcher(text).find();
    }
}
