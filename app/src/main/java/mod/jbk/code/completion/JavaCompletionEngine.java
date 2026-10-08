package mod.jbk.code.completion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggests what to type next in a Java source file: members after a dot, class names (with the import
 * they need) while typing a name, and fully qualified names after {@code import}.
 * <p>
 * It works on the text alone, without a full parse, so it is fast and tolerant of code that doesn't
 * compile yet. Members come from reflection, which on a device means the classes of the Android runtime.
 */
public final class JavaCompletionEngine {
    public enum Type {METHOD, FIELD, CLASS}

    /**
     * @param insertText   what replaces the typed prefix
     * @param importFqn    class to import after inserting, or null
     * @param caretBack    how many characters to move the caret back after inserting (to sit inside the parentheses)
     * @param rank         lower sorts first
     */
    public record Suggestion(Type type, String label, String detail, String insertText, String importFqn, int caretBack, int rank) {
    }

    /**
     * @param prefixLength characters before the caret that the suggestion replaces
     * @param exclusive    whether the context is one where other completion sources (keywords, words in the file) are noise
     */
    public record Result(int prefixLength, boolean exclusive, List<Suggestion> suggestions) {
        static final Result NONE = new Result(0, false, List.of());
    }

    /** Where to insert an import and what. */
    public record ImportEdit(int offset, String text) {
    }

    private static final int MAX_SUGGESTIONS = 80;
    private static final int MIN_NAME_PREFIX = 2;
    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]*)$");
    private static final Pattern MEMBER_ACCESS = Pattern.compile("(?<![\\w$.)\\]>])([A-Za-z_$][\\w$]*)\\s*\\.\\s*([A-Za-z_$][\\w$]*)?$");
    private static final Pattern NAME_PREFIX = Pattern.compile("(?<![\\w$.])([A-Za-z_$][\\w$]*)$");
    private static final Pattern IMPORT_STATEMENT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);
    private static final Pattern PACKAGE_STATEMENT = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern MEMBER_DECLARATION_FIELD = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|final|volatile|transient)\\s+)*[\\w$<>\\[\\],.? ]+?\\s+([A-Za-z_$][\\w$]*)\\s*(?:=|;)");
    private static final Pattern MEMBER_DECLARATION_METHOD = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|final|synchronized|abstract)\\s+)*([\\w$<>\\[\\],.? ]+?)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[\\w$., ]+)?\\s*\\{");
    private static final List<String> NOT_A_TYPE = List.of("return", "new", "else", "throw", "case", "package", "import", "assert");

    private final ClassIndex index;
    private final Function<String, Class<?>> classLoader;

    /**
     * @param classLoader loads a class by its qualified name without initializing it, null if unavailable
     */
    public JavaCompletionEngine(ClassIndex index, Function<String, Class<?>> classLoader) {
        this.index = index;
        this.classLoader = classLoader;
    }

    public Result complete(String source, int offset) {
        if (offset < 0 || offset > source.length()) return Result.NONE;

        int lineStart = source.lastIndexOf('\n', offset - 1) + 1;
        String lineBefore = source.substring(lineStart, offset);

        Matcher importLine = IMPORT_LINE.matcher(lineBefore);
        if (importLine.matches()) {
            return importSuggestions(importLine.group(1) != null, importLine.group(2));
        }
        if (isInsideStringOrComment(lineBefore) || isInsideBlockComment(source, offset)) return Result.NONE;

        String before = source.substring(0, offset);
        Matcher member = MEMBER_ACCESS.matcher(lineBefore);
        if (member.find()) {
            return memberSuggestions(source, offset, member.group(1), member.group(2) == null ? "" : member.group(2));
        }

        Matcher name = NAME_PREFIX.matcher(lineBefore);
        if (name.find()) {
            return classSuggestions(source, name.group(1), before);
        }
        return Result.NONE;
    }

    // ---- imports

    private Result importSuggestions(boolean isStatic, String typed) {
        if (isStatic) return Result.NONE;
        List<Suggestion> suggestions = new ArrayList<>();
        for (String name : index.withQualifiedPrefix(typed)) {
            suggestions.add(new Suggestion(Type.CLASS, ClassIndex.simpleName(name), ClassIndex.packageOf(name), name + ";", null, 0, 0));
            if (suggestions.size() >= MAX_SUGGESTIONS) break;
        }
        return new Result(typed.length(), true, suggestions);
    }

    /**
     * Where and what to insert to import {@code qualifiedName}, or null if it's already visible
     * without an import (same package, {@code java.lang}, or already imported).
     */
    public static ImportEdit importEdit(String source, String qualifiedName) {
        String pkg = ClassIndex.packageOf(qualifiedName);
        if (pkg.isEmpty() || pkg.equals("java.lang")) return null;
        Matcher packageMatch = PACKAGE_STATEMENT.matcher(source);
        if (packageMatch.find() && packageMatch.group(1).equals(pkg)) return null;

        int afterLastImport = -1;
        Matcher imports = IMPORT_STATEMENT.matcher(source);
        while (imports.find()) {
            if (imports.group(1) == null) {
                boolean wildcard = imports.group(3) != null;
                String imported = imports.group(2);
                if (wildcard ? imported.equals(pkg) : imported.equals(qualifiedName)) return null;
            }
            afterLastImport = endOfLine(source, imports.end());
        }
        String statement = "import " + qualifiedName + ";\n";
        if (afterLastImport >= 0) return new ImportEdit(afterLastImport, statement);
        if (packageMatch.find(0)) return new ImportEdit(endOfLine(source, packageMatch.end()), "\n" + statement);
        return new ImportEdit(0, statement + "\n");
    }

    private static int endOfLine(String source, int from) {
        int newline = source.indexOf('\n', from);
        return newline < 0 ? source.length() : newline + 1;
    }

    // ---- class names

    private Result classSuggestions(String source, String prefix, String before) {
        boolean afterNew = before.substring(0, before.length() - prefix.length()).stripTrailing().endsWith("new");
        if (prefix.length() < MIN_NAME_PREFIX && !afterNew) return Result.NONE;
        boolean upper = Character.isUpperCase(prefix.charAt(0));
        List<Suggestion> suggestions = new ArrayList<>();
        List<String> matches = new ArrayList<>(index.withSimpleNamePrefix(prefix));
        matches.sort(Comparator
                .comparing((String n) -> !ClassIndex.simpleName(n).startsWith(prefix))
                .thenComparingInt(JavaCompletionEngine::packagePriority)
                .thenComparingInt(n -> ClassIndex.simpleName(n).length())
                .thenComparing(Comparator.naturalOrder()));
        for (String name : matches) {
            ImportEdit edit = importEdit(source, name);
            suggestions.add(new Suggestion(Type.CLASS, ClassIndex.simpleName(name), ClassIndex.packageOf(name),
                    ClassIndex.simpleName(name), edit == null ? null : name, 0, upper ? 0 : 2));
            if (suggestions.size() >= MAX_SUGGESTIONS) break;
        }
        return new Result(prefix.length(), false, suggestions);
    }

    private static int packagePriority(String qualifiedName) {
        if (qualifiedName.startsWith("android.") || qualifiedName.startsWith("androidx.")) return 0;
        if (qualifiedName.startsWith("java.")) return 1;
        return 2;
    }

    // ---- members

    private Result memberSuggestions(String source, int offset, String receiver, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        Map<String, Suggestion> byLabel = new LinkedHashMap<>();

        if (receiver.equals("this")) {
            for (Suggestion s : declaredMembers(source)) {
                if (s.label().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) byLabel.putIfAbsent(s.label(), s);
            }
        } else {
            boolean isStatic;
            Class<?> type;
            String declared = declaredTypeOf(source.substring(0, offset - prefix.length()), receiver);
            if (declared != null) {
                type = resolve(source, declared);
                isStatic = false;
            } else {
                type = resolve(source, receiver);
                isStatic = true;
            }
            if (type == null) return Result.NONE;

            try {
                for (Method method : type.getMethods()) {
                    if (method.isSynthetic() || method.isBridge()) continue;
                    if (isStatic && !Modifier.isStatic(method.getModifiers())) continue;
                    if (!method.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) continue;
                    String label = method.getName() + "(" + parameterList(method.getParameterTypes()) + ")";
                    boolean hasParameters = method.getParameterCount() > 0;
                    byLabel.putIfAbsent(label, new Suggestion(Type.METHOD, label, method.getReturnType().getSimpleName(),
                            method.getName() + "()", null, hasParameters ? 1 : 0, 1));
                }
                for (Field field : type.getFields()) {
                    if (field.isSynthetic()) continue;
                    if (isStatic && !Modifier.isStatic(field.getModifiers())) continue;
                    if (!field.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) continue;
                    byLabel.putIfAbsent(field.getName(), new Suggestion(Type.FIELD, field.getName(), field.getType().getSimpleName(),
                            field.getName(), null, 0, 0));
                }
            } catch (LinkageError | RuntimeException e) {
                // A class whose dependencies aren't available: nothing to offer
                return Result.NONE;
            }
        }

        List<Suggestion> suggestions = new ArrayList<>(byLabel.values());
        suggestions.sort(Comparator.comparingInt(Suggestion::rank).thenComparing(Suggestion::label, String.CASE_INSENSITIVE_ORDER));
        if (suggestions.size() > MAX_SUGGESTIONS) suggestions = new ArrayList<>(suggestions.subList(0, MAX_SUGGESTIONS));
        return new Result(prefix.length(), true, suggestions);
    }

    private static String parameterList(Class<?>[] parameters) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) text.append(", ");
            text.append(parameters[i].getSimpleName());
        }
        return text.toString();
    }

    /** The declared type of a variable, field or parameter named {@code variable}, nearest declaration first. */
    static String declaredTypeOf(String sourceBeforeCaret, String variable) {
        String name = Pattern.quote(variable);
        Pattern typed = Pattern.compile("(?<![\\w$.])((?:[A-Za-z_$][\\w$]*\\.)*[A-Z][\\w$]*)(?:<[^;(){}=]*?>)?(?:\\[\\])*\\s+" + name + "\\s*(?==|;|,|\\)|:)");
        Pattern inferred = Pattern.compile("\\bvar\\s+" + name + "\\s*=\\s*new\\s+((?:[A-Za-z_$][\\w$]*\\.)*[A-Z][\\w$]*)");
        String found = null;
        int foundAt = -1;
        Matcher m = typed.matcher(sourceBeforeCaret);
        while (m.find()) {
            if (!NOT_A_TYPE.contains(m.group(1))) {
                found = m.group(1);
                foundAt = m.start();
            }
        }
        m = inferred.matcher(sourceBeforeCaret);
        while (m.find()) {
            if (m.start() > foundAt) {
                found = m.group(1);
                foundAt = m.start();
            }
        }
        return found;
    }

    /** Resolves a type name as written in {@code source} to a loaded class, null if it can't be. */
    private Class<?> resolve(String source, String typeName) {
        if (typeName.indexOf('.') >= 0) return load(typeName);

        Matcher imports = IMPORT_STATEMENT.matcher(source);
        List<String> wildcards = new ArrayList<>();
        while (imports.find()) {
            if (imports.group(1) != null) continue;
            if (imports.group(3) != null) wildcards.add(imports.group(2));
            else if (ClassIndex.simpleName(imports.group(2)).equals(typeName)) return load(imports.group(2));
        }
        Matcher pkg = PACKAGE_STATEMENT.matcher(source);
        if (pkg.find()) {
            Class<?> samePackage = load(pkg.group(1) + "." + typeName);
            if (samePackage != null) return samePackage;
        }
        Class<?> javaLang = load("java.lang." + typeName);
        if (javaLang != null) return javaLang;
        for (String wildcard : wildcards) {
            Class<?> viaWildcard = load(wildcard + "." + typeName);
            if (viaWildcard != null) return viaWildcard;
        }
        List<String> candidates = new ArrayList<>(index.withSimpleName(typeName));
        candidates.sort(Comparator.comparingInt(JavaCompletionEngine::packagePriority).thenComparing(Comparator.naturalOrder()));
        for (String candidate : candidates) {
            Class<?> loaded = load(candidate);
            if (loaded != null) return loaded;
        }
        return null;
    }

    private Class<?> load(String qualifiedName) {
        try {
            return classLoader.apply(qualifiedName);
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Fields and methods declared directly in the file's top-level class, for {@code this.}. */
    static List<Suggestion> declaredMembers(String source) {
        String code = blankOutStringsAndComments(source);
        List<Suggestion> members = new ArrayList<>();
        int depth = 0;
        int start = 0;
        while (start <= code.length()) {
            int end = code.indexOf('\n', start);
            if (end < 0) end = code.length();
            String line = code.substring(start, end);
            if (depth == 1) {
                Matcher method = MEMBER_DECLARATION_METHOD.matcher(line);
                Matcher field = MEMBER_DECLARATION_FIELD.matcher(line);
                if (method.find() && !NOT_A_TYPE.contains(method.group(1).trim())) {
                    String parameters = method.group(3).trim();
                    String label = method.group(2) + "(" + simplifyParameters(parameters) + ")";
                    members.add(new Suggestion(Type.METHOD, label, method.group(1).trim(), method.group(2) + "()", null, parameters.isEmpty() ? 0 : 1, 1));
                } else if (field.find() && !line.contains("(") && !NOT_A_TYPE.contains(field.group(0).trim().split("\\s+")[0])) {
                    members.add(new Suggestion(Type.FIELD, field.group(1), "", field.group(1), null, 0, 0));
                }
            }
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '{') depth++;
                else if (line.charAt(i) == '}') depth--;
            }
            start = end + 1;
        }
        return members;
    }

    private static String simplifyParameters(String parameters) {
        if (parameters.isEmpty()) return "";
        List<String> types = new ArrayList<>();
        for (String parameter : parameters.split(",")) {
            String[] words = parameter.trim().split("\\s+");
            types.add(words.length >= 2 ? words[words.length - 2] : parameter.trim());
        }
        return String.join(", ", types);
    }

    /** Same length as {@code source}, with the content of string literals and comments replaced by spaces. */
    static String blankOutStringsAndComments(String source) {
        char[] out = source.toCharArray();
        int i = 0;
        while (i < out.length) {
            char c = out[i];
            if (c == '/' && i + 1 < out.length && out[i + 1] == '/') {
                while (i < out.length && out[i] != '\n') out[i++] = ' ';
            } else if (c == '/' && i + 1 < out.length && out[i + 1] == '*') {
                out[i++] = ' ';
                out[i++] = ' ';
                while (i < out.length && !(out[i] == '*' && i + 1 < out.length && out[i + 1] == '/')) {
                    if (out[i] != '\n') out[i] = ' ';
                    i++;
                }
                if (i < out.length) out[i++] = ' ';
                if (i < out.length) out[i++] = ' ';
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < out.length && out[i] != c && out[i] != '\n') {
                    if (out[i] == '\\' && i + 1 < out.length) out[i++] = ' ';
                    out[i++] = ' ';
                }
                if (i < out.length && out[i] == c) i++;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    private static boolean isInsideStringOrComment(String lineBefore) {
        return hasOpenQuote(lineBefore);
    }

    /** Whether the end of the line is inside a string, a character literal or a {@code //} comment. */
    private static boolean hasOpenQuote(String line) {
        char open = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (open != 0) {
                if (c == '\\') i++;
                else if (c == open) open = 0;
            } else if (c == '"' || c == '\'') {
                open = c;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                return true;
            }
        }
        return open != 0;
    }

    private static boolean isInsideBlockComment(String source, int offset) {
        int open = source.lastIndexOf("/*", offset - 1);
        if (open < 0) return false;
        int close = source.indexOf("*/", open + 2);
        return close < 0 || close >= offset;
    }
}
