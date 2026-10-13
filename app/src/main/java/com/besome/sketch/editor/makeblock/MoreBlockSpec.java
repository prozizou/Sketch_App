package com.besome.sketch.editor.makeblock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure (Android-free) model of a MoreBlock being built: how its parameters become a block spec, how an advanced
 * parameter type is validated, and how the whole form is written to / read from a draft.
 */
public final class MoreBlockSpec {

    /** Advanced parameter types, e.g. {@code m.view} or {@code l.listName}, written without the leading %. */
    public static final Pattern CUSTOM_TYPE_PATTERN = Pattern.compile("[mldb]\\.[a-zA-Z]+");
    /** Type of a plain text label between parameters. */
    public static final String LABEL = "t";
    /** Name shown in the live preview until the user typed one. */
    public static final String PREVIEW_PLACEHOLDER = "newBlock";

    private MoreBlockSpec() {
    }

    /**
     * One entry of a MoreBlock spec after its name: a typed parameter or a label.
     *
     * @param type {@code b}, {@code d}, {@code s}, a dotted type like {@code m.view}, or {@link #LABEL}
     * @param name the parameter name, or the label text
     */
    public record Param(String type, String name) {
        public boolean isLabel() {
            return LABEL.equals(type);
        }
    }

    /** @return the spec of the block: its name followed by every parameter, in order, with the engine's % syntax. */
    public static String buildSpec(String blockName, List<Param> params) {
        StringBuilder spec = new StringBuilder(blockName);
        for (Param param : params) {
            switch (param.type()) {
                case "b", "d", "s" -> spec.append(" %").append(param.type()).append('.').append(param.name());
                default -> {
                    if (param.type().length() > 2 && param.type().contains(".")) {
                        spec.append(" %").append(param.type()).append('.').append(param.name());
                    } else {
                        spec.append(' ').append(param.name());
                    }
                }
            }
        }
        return spec.toString();
    }

    /** @return whether {@code type} is an advanced parameter type the engine understands, e.g. {@code m.view}. */
    public static boolean isValidCustomType(String type) {
        return type != null && CUSTOM_TYPE_PATTERN.matcher(type).matches();
    }

    /** @return the names a new or edited parameter can't take: every other parameter's name (labels don't count). */
    public static List<String> takenParameterNames(List<Param> params, int ignoredIndex) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            if (i != ignoredIndex && !params.get(i).isLabel()) {
                names.add(params.get(i).name());
            }
        }
        return names;
    }

    /** The whole form, as saved in a draft. */
    public record Draft(String name, String returnTypeKey, List<Param> params, boolean advancedExpanded) {

        private static final char SEPARATOR = '\u001F';

        /** @return the draft as a single string. */
        public String encode() {
            StringBuilder out = new StringBuilder("v1");
            append(out, name);
            append(out, returnTypeKey);
            append(out, advancedExpanded ? "1" : "0");
            for (Param param : params) {
                append(out, param.type());
                append(out, param.name());
            }
            return out.toString();
        }

        private static void append(StringBuilder out, String field) {
            out.append(SEPARATOR).append(field.replace("\\", "\\\\").replace(String.valueOf(SEPARATOR), "\\u"));
        }

        /** @return the draft written by {@link #encode()}, or null if {@code encoded} isn't one. */
        public static Draft decode(String encoded) {
            if (encoded == null || !encoded.startsWith("v1")) {
                return null;
            }
            // "v1" + (SEPARATOR + field)*: the first piece is the version, the fields follow
            String[] pieces = encoded.split(String.valueOf(SEPARATOR), -1);
            List<String> fields = new ArrayList<>(List.of(pieces).subList(1, pieces.length));
            // fields: name, returnTypeKey, advanced, then (type, name) pairs
            if (fields.size() < 3 || (fields.size() - 3) % 2 != 0) {
                return null;
            }
            List<Param> params = new ArrayList<>();
            for (int i = 3; i < fields.size(); i += 2) {
                params.add(new Param(unescape(fields.get(i)), unescape(fields.get(i + 1))));
            }
            return new Draft(unescape(fields.get(0)), unescape(fields.get(1)), params, "1".equals(fields.get(2)));
        }

        private static String unescape(String field) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < field.length(); i++) {
                char c = field.charAt(i);
                if (c == '\\' && i + 1 < field.length()) {
                    char next = field.charAt(++i);
                    out.append(next == 'u' ? SEPARATOR : next);
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }
}
