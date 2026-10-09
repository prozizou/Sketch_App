package pro.sketchware.designer;

import java.util.Locale;

/** Field types the designers understand, with their Java and SQLite forms. */
public enum FieldType {
    TEXT("String", "TEXT"),
    INT("long", "INTEGER"),
    REAL("double", "REAL"),
    BOOL("boolean", "INTEGER"),
    BLOB("byte[]", "BLOB");

    public final String javaType;
    public final String sqlType;

    FieldType(String javaType, String sqlType) {
        this.javaType = javaType;
        this.sqlType = sqlType;
    }

    /** Accepts the usual spellings: text/string, int/integer/long, real/double/float, bool/boolean, blob/bytes. */
    public static FieldType parse(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "text", "string", "str" -> TEXT;
            case "int", "integer", "long", "number" -> INT;
            case "real", "double", "float", "decimal" -> REAL;
            case "bool", "boolean" -> BOOL;
            case "blob", "bytes", "byte[]" -> BLOB;
            default -> null;
        };
    }
}
