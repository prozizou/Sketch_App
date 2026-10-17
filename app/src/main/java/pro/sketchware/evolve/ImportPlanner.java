package pro.sketchware.evolve;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what adding a post to the user's own material would do, before anything is written: where it goes, what
 * it would be called, what clashes with what is already there, and what the user should know first (a newer Android,
 * missing libraries, raw Java, resources it refers to). The result is shown as a preview and only applied after the
 * user confirms. Nothing here runs shared code or touches a file: it is text in, a plan out. Pure Java.
 */
public final class ImportPlanner {

    /** Where an entry goes. */
    public enum Target {
        /** My Block Collection, to be dropped into any event from the drawer. */
        BLOCK_COLLECTION,
        /** The more block collection, to be imported into any screen from the Events list. */
        MOREBLOCK_COLLECTION,
        /** A new layout file of a project. */
        LAYOUT_FILE,
        JAVA_FILE,
        KOTLIN_FILE,
        /** The custom components of the app (not tied to a project). */
        COMPONENT_LIST
    }

    public enum Severity {BLOCKER, WARNING, INFO}

    public enum Code {
        DAMAGED, INVALID_CODE, NEWER_APP, NEEDS_NEWER_ANDROID_THAN_DEVICE, PROJECT_MIN_SDK_TOO_LOW, MISSING_LIBRARIES,
        UNDECLARED_IMPORTS, RAW_JAVA, RENAMED, FILE_EXISTS, DUPLICATE_CONTENT, REFERENCES_RESOURCES, PACKAGE_CHANGED,
        COMPONENT_EXISTS, REPLACES_EXISTING
    }

    /** One thing to tell the user. @param detail the specifics (names, versions), may be empty */
    public record Issue(Severity severity, Code code, String detail) {
    }

    /**
     * What is already there. Sets are compared without regard to case.
     *
     * @param packageName the project's package, "" when adding to nothing project-specific
     * @param minSdk      the project's minimum Android API level, 0 when unknown
     */
    public record Context(String projectName, String packageName, int minSdk, Set<String> javaFiles, Set<String> layoutFiles,
                          Set<String> libraries, Set<String> blockNames, Set<String> moreBlockNames, Set<String> componentNames,
                          Set<String> componentIds, List<String> existingBlocksJson) {
        public static Context empty() {
            return new Context("", "", 0, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of());
        }
    }

    /** What to do when the target name is taken. */
    public enum OnConflict {RENAME, REPLACE}

    /**
     * The result.
     *
     * @param name       the name or file name it will have
     * @param content    for files and components, what is written (the package already set); for blocks, "" (the blocks are decoded again when applied)
     * @param preview    the start of what is written, to show before confirming
     * @param replaces   whether it overwrites something that exists (the old content is kept for undo)
     */
    public record Plan(Target target, String name, String content, String preview, List<Issue> issues, boolean replaces) {
        public boolean canApply() {
            for (Issue issue : issues) {
                if (issue.severity() == Severity.BLOCKER) return false;
            }
            return true;
        }

        /** Whether anything in the plan deserves the user's explicit attention. */
        public boolean needsAttention() {
            for (Issue issue : issues) {
                if (issue.severity() != Severity.INFO) return true;
            }
            return false;
        }
    }

    private static final Pattern RESOURCE_REF = Pattern.compile("@(drawable|mipmap|string|color|dimen|style|anim|font)/([A-Za-z0-9_.]+)");
    private static final Pattern KOTLIN_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+[ \\t]*;?");
    private static final String[] COMPONENT_KEYS = {"name", "id", "icon", "varName", "typeName", "buildClass", "class", "description",
            "url", "additionalVar", "defineAdditionalVar", "imports"};
    private static final int PREVIEW_LINES = 25;

    private ImportPlanner() {
    }

    /** @return the target an entry of {@code kind} is added to */
    public static Target targetOf(SharedKind kind) {
        return switch (kind) {
            case BLOCKS -> Target.BLOCK_COLLECTION;
            case MOREBLOCK -> Target.MOREBLOCK_COLLECTION;
            case VIEW -> Target.LAYOUT_FILE;
            case JAVA -> Target.JAVA_FILE;
            case KOTLIN -> Target.KOTLIN_FILE;
            case COMPONENT -> Target.COMPONENT_LIST;
        };
    }

    /** @return whether the target belongs to one project, so the user must choose which */
    public static boolean needsProject(Target target) {
        return target == Target.LAYOUT_FILE || target == Target.JAVA_FILE || target == Target.KOTLIN_FILE;
    }

    public static Plan plan(SharedBlock post, Context context, int appVersion, int deviceSdk, OnConflict onConflict) {
        Target target = targetOf(post.kind());
        List<Issue> issues = new ArrayList<>();
        if (EvolveChecks.madeWithNewerApp(post.appVersion(), appVersion)) {
            issues.add(new Issue(Severity.WARNING, Code.NEWER_APP, String.valueOf(post.appVersion())));
        }
        checkCompatibility(post, context, deviceSdk, issues);
        Plan plan = switch (target) {
            case BLOCK_COLLECTION, MOREBLOCK_COLLECTION -> planBlocks(post, context, target, issues);
            case LAYOUT_FILE -> planLayout(post, context, onConflict, issues);
            case JAVA_FILE, KOTLIN_FILE -> planSource(post, context, target, onConflict, issues);
            case COMPONENT_LIST -> planComponents(post, context, onConflict, issues);
        };
        List<Issue> sorted = new ArrayList<>(plan.issues());
        sorted.sort(Comparator.comparingInt((Issue issue) -> issue.severity().ordinal()));
        return new Plan(plan.target(), plan.name(), plan.content(), plan.preview(), sorted, plan.replaces());
    }

    // ---- compatibility and dependencies

    private static void checkCompatibility(SharedBlock post, Context context, int deviceSdk, List<Issue> issues) {
        int needs = post.meta().minSdk();
        if (needs > 0 && deviceSdk > 0 && needs > deviceSdk) {
            issues.add(new Issue(Severity.INFO, Code.NEEDS_NEWER_ANDROID_THAN_DEVICE, needs + "/" + deviceSdk));
        }
        if (needs > 0 && context.minSdk() > 0 && needs > context.minSdk() && needsProject(targetOf(post.kind()))) {
            issues.add(new Issue(Severity.WARNING, Code.PROJECT_MIN_SDK_TOO_LOW, needs + "/" + context.minSdk()));
        }
        List<String> missing = missingLibraries(post.meta().dependencies(), context.libraries());
        if (!missing.isEmpty() && needsProject(targetOf(post.kind()))) {
            issues.add(new Issue(Severity.WARNING, Code.MISSING_LIBRARIES, String.join(", ", missing)));
        }
    }

    /**
     * @return the dependencies no library of the project seems to provide. A coordinate {@code group:artifact:version}
     * is matched by its artifact name; anything else by the name itself. Matching is loose on purpose (library folders
     * are named in many ways), so a library that is present is never reported as missing.
     */
    public static List<String> missingLibraries(List<String> dependencies, Set<String> libraries) {
        List<String> missing = new ArrayList<>();
        for (String dependency : dependencies) {
            String[] parts = dependency.split(":");
            String artifact = (parts.length >= 2 ? parts[1] : parts[0]).toLowerCase(Locale.ROOT);
            boolean found = false;
            for (String library : libraries) {
                String lower = library.toLowerCase(Locale.ROOT);
                if (lower.contains(artifact) || artifact.contains(lower) && !lower.isEmpty()) {
                    found = true;
                    break;
                }
            }
            if (!found) missing.add(dependency);
        }
        return missing;
    }

    // ---- blocks and more blocks

    private static Plan planBlocks(SharedBlock post, Context context, Target target, List<Issue> issues) {
        ArrayList<BlockBean> blocks = SharedBlockCodec.decode(post.blocksJson());
        if (blocks == null || blocks.isEmpty()) {
            issues.add(new Issue(Severity.BLOCKER, Code.DAMAGED, ""));
            return new Plan(target, post.name(), "", "", issues, false);
        }
        Set<String> taken = target == Target.BLOCK_COLLECTION ? context.blockNames() : context.moreBlockNames();
        String name = SharedBlockRules.uniqueName(post.name(), taken);
        if (!name.equals(post.name().trim())) {
            issues.add(new Issue(Severity.INFO, Code.RENAMED, name));
        }
        if (target == Target.BLOCK_COLLECTION && EvolveChecks.alreadyHave(post.blocksJson(), context.existingBlocksJson())) {
            issues.add(new Issue(Severity.WARNING, Code.DUPLICATE_CONTENT, ""));
        }
        List<EvolveChecks.RawSnippet> raw = EvolveChecks.rawCode(blocks);
        if (post.usesRawCode() || !raw.isEmpty() || SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks))) {
            issues.add(new Issue(Severity.WARNING, Code.RAW_JAVA, String.valueOf(raw.size())));
        }
        StringBuilder preview = new StringBuilder();
        for (BlockBean bean : blocks) {
            preview.append("• ").append(bean.opCode);
            if (bean.spec != null && !bean.spec.isBlank()) preview.append(": ").append(bean.spec);
            preview.append('\n');
        }
        return new Plan(target, name, "", firstLines(preview.toString()), issues, false);
    }

    // ---- layouts

    private static Plan planLayout(SharedBlock post, Context context, OnConflict onConflict, List<Issue> issues) {
        String xml = post.xml();
        if (SharedXml.check(xml).problem() != SharedBlockRules.Problem.NONE) {
            issues.add(new Issue(Severity.BLOCKER, Code.INVALID_CODE, "xml"));
            return new Plan(Target.LAYOUT_FILE, "", "", "", issues, false);
        }
        String base = layoutName(post.name());
        String file = base + ".xml";
        boolean exists = containsIgnoreCase(context.layoutFiles(), file);
        boolean replaces = false;
        if (exists && onConflict == OnConflict.REPLACE) {
            replaces = true;
            issues.add(new Issue(Severity.WARNING, Code.REPLACES_EXISTING, file));
        } else if (exists) {
            for (int i = 2; ; i++) {
                String candidate = base + "_" + i + ".xml";
                if (!containsIgnoreCase(context.layoutFiles(), candidate)) {
                    file = candidate;
                    break;
                }
            }
            issues.add(new Issue(Severity.INFO, Code.RENAMED, file));
        }
        Set<String> refs = resourceReferences(xml);
        if (!refs.isEmpty()) {
            issues.add(new Issue(Severity.INFO, Code.REFERENCES_RESOURCES, String.join(", ", refs)));
        }
        return new Plan(Target.LAYOUT_FILE, file, xml, firstLines(xml), issues, replaces);
    }

    /** @return the resources an XML refers to ({@code @drawable/logo}...), in first-use order, so the user can add them */
    public static Set<String> resourceReferences(String xml) {
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = RESOURCE_REF.matcher(xml == null ? "" : xml);
        while (matcher.find()) {
            refs.add("@" + matcher.group(1) + "/" + matcher.group(2));
        }
        return refs;
    }

    /** @return a layout file name (without extension) for {@code title}: lowercase letters, digits and underscores, starting with a letter */
    public static String layoutName(String title) {
        StringBuilder out = new StringBuilder();
        boolean separator = false;
        for (char c : (title == null ? "" : title.trim()).toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                if (separator && out.length() > 0) out.append('_');
                out.append(c);
                separator = false;
            } else {
                separator = true;
            }
        }
        if (out.length() == 0 || !(out.charAt(0) >= 'a' && out.charAt(0) <= 'z')) {
            out.insert(0, "shared_");
        }
        return out.length() > 40 ? out.substring(0, 40) : out.toString();
    }

    // ---- Java and Kotlin

    private static Plan planSource(SharedBlock post, Context context, Target target, OnConflict onConflict, List<Issue> issues) {
        String code = post.meta().code();
        PostRules.Problem problem = PostRules.checkCode(post.kind(), code);
        String file = post.meta().fileName();
        if (problem != PostRules.Problem.NONE || PostRules.checkFileName(post.kind(), file) != PostRules.Problem.NONE) {
            issues.add(new Issue(Severity.BLOCKER, Code.INVALID_CODE, problem.name()));
            return new Plan(target, file, "", "", issues, false);
        }
        boolean replaces = false;
        if (containsIgnoreCase(context.javaFiles(), file)) {
            if (onConflict == OnConflict.REPLACE) {
                replaces = true;
                issues.add(new Issue(Severity.WARNING, Code.REPLACES_EXISTING, file));
            } else {
                // A class can't be renamed safely by text, so a taken name stops the import until the user chooses to replace
                issues.add(new Issue(Severity.BLOCKER, Code.FILE_EXISTS, file));
            }
        }
        String content = code;
        if (!context.packageName().isEmpty()) {
            String rewritten = post.kind() == SharedKind.JAVA ? withJavaPackage(code, context.packageName())
                    : withKotlinPackage(code, context.packageName());
            if (!rewritten.equals(code)) {
                issues.add(new Issue(Severity.INFO, Code.PACKAGE_CHANGED, context.packageName()));
                content = rewritten;
            }
        }
        List<String> imports = PostRules.thirdPartyImports(code);
        imports.removeIf(name -> !context.packageName().isEmpty() && name.startsWith(context.packageName() + "."));
        List<String> declared = post.meta().dependencies();
        if (!imports.isEmpty() && declared.isEmpty() && !librariesCover(imports, context.libraries())) {
            issues.add(new Issue(Severity.INFO, Code.UNDECLARED_IMPORTS, String.join(", ", imports)));
        }
        return new Plan(target, file, content, firstLines(content), issues, replaces);
    }

    private static boolean librariesCover(List<String> imports, Set<String> libraries) {
        for (String name : imports) {
            String[] parts = name.split("\\.");
            boolean covered = false;
            for (String library : libraries) {
                String lower = library.toLowerCase(Locale.ROOT);
                for (String part : parts) {
                    if (part.length() > 2 && lower.contains(part.toLowerCase(Locale.ROOT))) {
                        covered = true;
                        break;
                    }
                }
                if (covered) break;
            }
            if (!covered) return false;
        }
        return true;
    }

    /** @return {@code source} with its package declaration set to {@code packageName} (added when it has none) */
    public static String withJavaPackage(String source, String packageName) {
        try {
            ParserConfiguration configuration = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) return source;
            CompilationUnit unit = result.getResult().get();
            PackageDeclaration declaration = unit.getPackageDeclaration().orElse(null);
            if (declaration == null) {
                return "package " + packageName + ";\n\n" + source;
            }
            if (declaration.getNameAsString().equals(packageName)) return source;
            Range range = declaration.getRange().orElse(null);
            if (range == null) return source;
            int start = offset(source, range.begin.line, range.begin.column);
            int end = offset(source, range.end.line, range.end.column) + 1;
            return source.substring(0, start) + "package " + packageName + ";" + source.substring(Math.min(end, source.length()));
        } catch (RuntimeException | StackOverflowError e) {
            return source;
        }
    }

    private static int offset(String text, int line, int column) {
        int at = 0;
        for (int current = 1; current < line; current++) {
            int next = text.indexOf('\n', at);
            if (next < 0) return text.length();
            at = next + 1;
        }
        return Math.min(text.length(), at + Math.max(0, column - 1));
    }

    /** @return {@code source} with its package line set to {@code packageName} (added when it has none) */
    public static String withKotlinPackage(String source, String packageName) {
        Matcher matcher = KOTLIN_PACKAGE.matcher(source);
        if (matcher.find()) {
            String current = matcher.group().trim().replace(";", "").trim();
            if (current.equals("package " + packageName)) return source;
            return source.substring(0, matcher.start()) + "package " + packageName + source.substring(matcher.end());
        }
        return "package " + packageName + "\n\n" + source;
    }

    // ---- components

    private static Plan planComponents(SharedBlock post, Context context, OnConflict onConflict, List<Issue> issues) {
        String json = post.meta().code();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (!readComponents(json, names, ids)) {
            issues.add(new Issue(Severity.BLOCKER, Code.INVALID_CODE, "component"));
            return new Plan(Target.COMPONENT_LIST, post.name(), "", "", issues, false);
        }
        boolean clash = false;
        for (String name : names) {
            if (containsIgnoreCase(context.componentNames(), name)) clash = true;
        }
        for (String id : ids) {
            if (containsIgnoreCase(context.componentIds(), id)) clash = true;
        }
        boolean replaces = false;
        if (clash) {
            if (onConflict == OnConflict.REPLACE) {
                replaces = true;
                issues.add(new Issue(Severity.WARNING, Code.REPLACES_EXISTING, String.join(", ", names)));
            } else {
                issues.add(new Issue(Severity.BLOCKER, Code.COMPONENT_EXISTS, String.join(", ", names)));
            }
        }
        return new Plan(Target.COMPONENT_LIST, String.join(", ", names), json, firstLines(json), issues, replaces);
    }

    /**
     * @return whether {@code json} is a non-empty list of components with every key the component manager needs;
     * fills {@code names} and {@code ids} with what the components are called
     */
    public static boolean readComponents(String json, List<String> names, List<String> ids) {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : null;
            if (array == null && root.isJsonObject()) {
                array = new JsonArray();
                array.add(root);
            }
            if (array == null || array.isEmpty()) return false;
            for (JsonElement element : array) {
                if (!element.isJsonObject()) return false;
                for (String key : COMPONENT_KEYS) {
                    if (!element.getAsJsonObject().has(key)) return false;
                }
                names.add(element.getAsJsonObject().get("name").getAsString());
                ids.add(element.getAsJsonObject().get("id").getAsString());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- helpers

    private static boolean containsIgnoreCase(Set<String> set, String value) {
        for (String item : set) {
            if (item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static String firstLines(String text) {
        String[] lines = text.split("\\R", PREVIEW_LINES + 1);
        if (lines.length <= PREVIEW_LINES) return text.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < PREVIEW_LINES; i++) out.append(lines[i]).append('\n');
        return out.append("…").toString();
    }
}
