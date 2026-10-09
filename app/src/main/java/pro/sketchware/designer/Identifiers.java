package pro.sketchware.designer;

import java.util.Locale;
import java.util.Set;

/** Checks and builds Java and SQL names for generated code. */
public final class Identifiers {
    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
            "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits");
    private static final Set<String> SQL_KEYWORDS = Set.of("select", "from", "where", "table", "insert", "update",
            "delete", "order", "group", "by", "index", "create", "drop", "values", "into", "and", "or", "not", "null",
            "primary", "key", "default", "check", "unique", "references", "join", "limit", "offset", "as", "on", "set");

    private Identifiers() {
    }

    public static boolean isJavaName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*") && !JAVA_KEYWORDS.contains(name);
    }

    public static boolean isSqlSafe(String name) {
        return isJavaName(name) && !SQL_KEYWORDS.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isPackageName(String name) {
        if (name == null || name.isEmpty()) return false;
        for (String part : name.split("\\.", -1)) {
            if (!isJavaName(part)) return false;
        }
        return true;
    }

    public static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** A Java string literal for {@code text}. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
