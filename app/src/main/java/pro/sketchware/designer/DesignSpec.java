package pro.sketchware.designer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plain-text descriptions the designers read, and their parser. Errors carry the line number, so the screen can
 * point at the line to fix. Blank lines and lines starting with {@code #} are ignored.
 *
 * <pre>
 * table Note            (SQLite and Firebase models)
 *   title text
 *   done bool
 *
 * GET users /users      (REST endpoints: method, name, path; {x} in the path becomes a parameter)
 * POST addUser /users body
 * </pre>
 */
public final class DesignSpec {
    public record Field(String name, FieldType type) {
    }

    public record Table(String name, List<Field> fields) {
    }

    /** No PATCH: Android's HttpURLConnection refuses it. */
    public enum Method {GET, POST, PUT, DELETE}

    public record Endpoint(Method method, String name, String path, boolean hasBody, List<String> pathParams) {
    }

    public record Parsed<T>(List<T> items, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty() && !items.isEmpty();
        }
    }

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}/]*)\\}");

    private DesignSpec() {
    }

    public static Parsed<Table> parseTables(String text) {
        List<Table> tables = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String currentName = null;
        List<Field> fields = null;
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int n = i + 1;
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] words = line.split("\\s+");
            if (words[0].equalsIgnoreCase("table")) {
                if (currentName != null) tables.add(close(currentName, fields, errors));
                if (words.length != 2 || !Identifiers.isSqlSafe(words[1])) {
                    errors.add("Line " + n + ": write 'table Name' with a name made of letters, digits and _ that is not a reserved word");
                    currentName = null;
                    fields = null;
                } else {
                    if (tables.stream().anyMatch(t -> t.name().equalsIgnoreCase(words[1])) || words[1].equalsIgnoreCase(currentName)) {
                        errors.add("Line " + n + ": table " + words[1] + " is defined twice");
                    }
                    currentName = words[1];
                    fields = new ArrayList<>();
                }
                continue;
            }
            if (currentName == null) {
                errors.add("Line " + n + ": a field must come after a 'table Name' line");
                continue;
            }
            if (words.length != 2) {
                errors.add("Line " + n + ": write a field as 'name type', like 'title text'");
                continue;
            }
            FieldType type = FieldType.parse(words[1]);
            if (type == null) {
                errors.add("Line " + n + ": unknown type '" + words[1] + "' (use text, int, real, bool or blob)");
            } else if (!Identifiers.isSqlSafe(words[0]) || words[0].equalsIgnoreCase("id")) {
                errors.add("Line " + n + ": '" + words[0] + "' cannot be a field name (letters, digits and _, not a reserved word, and 'id' is added for you)");
            } else if (fields.stream().anyMatch(f -> f.name().equalsIgnoreCase(words[0]))) {
                errors.add("Line " + n + ": field " + words[0] + " is defined twice");
            } else {
                fields.add(new Field(words[0], type));
            }
        }
        if (currentName != null) tables.add(close(currentName, fields, errors));
        if (tables.isEmpty() && errors.isEmpty()) errors.add("Describe at least one table");
        return new Parsed<>(tables, errors);
    }

    private static Table close(String name, List<Field> fields, List<String> errors) {
        if (fields.isEmpty()) errors.add("Table " + name + " has no fields");
        return new Table(name, List.copyOf(fields));
    }

    public static Parsed<Endpoint> parseEndpoints(String text) {
        List<Endpoint> endpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int n = i + 1;
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] words = line.split("\\s+");
            if (words.length < 3 || words.length > 4 || (words.length == 4 && !words[3].equalsIgnoreCase("body"))) {
                errors.add("Line " + n + ": write 'METHOD name /path' and optionally 'body', like 'POST addUser /users body'");
                continue;
            }
            Method method;
            if (words[0].equalsIgnoreCase("PATCH")) {
                errors.add("Line " + n + ": PATCH is not supported by Android's HttpURLConnection; use PUT or POST");
                continue;
            }
            try {
                method = Method.valueOf(words[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("Line " + n + ": unknown method '" + words[0] + "' (GET, POST, PUT or DELETE)");
                continue;
            }
            String name = words[1];
            if (!Identifiers.isJavaName(name)) {
                errors.add("Line " + n + ": '" + name + "' cannot be a method name");
                continue;
            }
            if (endpoints.stream().anyMatch(e -> e.name().equals(name)) || name.equals("setHeader") || name.equals("shutdown")) {
                errors.add("Line " + n + ": the name " + name + " is already used");
                continue;
            }
            String path = words[2];
            if (!path.startsWith("/") && !path.startsWith("http")) {
                errors.add("Line " + n + ": the path must start with / (it is added to the base address)");
                continue;
            }
            boolean body = words.length == 4;
            if (body && method == Method.GET) {
                errors.add("Line " + n + ": a GET request cannot have a body");
                continue;
            }
            List<String> params = new ArrayList<>();
            Matcher matcher = PATH_PARAM.matcher(path);
            boolean badParam = false;
            while (matcher.find()) {
                String param = matcher.group(1);
                if (!Identifiers.isJavaName(param) || params.contains(param) || param.equals("body") || param.equals("callback")) {
                    errors.add("Line " + n + ": '{" + param + "}' is not a usable parameter name");
                    badParam = true;
                    break;
                }
                params.add(param);
            }
            if (badParam) continue;
            endpoints.add(new Endpoint(method, name, path, body, List.copyOf(params)));
        }
        if (endpoints.isEmpty() && errors.isEmpty()) errors.add("Describe at least one request");
        return new Parsed<>(endpoints, errors);
    }
}
