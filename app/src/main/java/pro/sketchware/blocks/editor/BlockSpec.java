package pro.sketchware.blocks.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A block spec in the native NWS format: words and parameters separated by spaces. A parameter is {@code %s},
 * {@code %b}, {@code %d}, {@code %s.menu} or {@code %m.menu} (a drop-down or an object of the project, e.g.
 * {@code %m.view}); everything else is text shown on the block.
 *
 * <p>The format has no parameter names or default values: a parameter is its kind and its menu, and the code refers
 * to it by position ({@code %s}, {@code %1$s}…). The spec string the user types is never rewritten unless one of the
 * editing operations here is used; those write the tokens back separated by single spaces.
 */
public final class BlockSpec {
    public interface Token {
        /** The token as written in the spec. */
        String text();
    }

    /** A word of the block. */
    public record Text(String text) implements Token {
    }

    /** A parameter; {@code menu} is null for plain {@code %s}, {@code %b} and {@code %d}. */
    public record Param(char kind, String menu) implements Token {
        @Override
        public String text() {
            return "%" + kind + (menu == null ? "" : "." + menu);
        }
    }

    /** Starts with {@code %} but isn't a parameter; {@code error} says whether the engine refuses it. */
    public record Invalid(String text, String problem, boolean error) implements Token {
    }

    private static final Pattern PARAM = Pattern.compile("%(?:([sbd])(?:\\.(\\w+))?|m\\.(\\w+))");
    private static final Pattern MENU_WITHOUT_NAME = Pattern.compile("%m(?!\\.\\w)");

    private BlockSpec() {
    }

    public static List<Token> parse(String spec) {
        List<Token> tokens = new ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) return tokens;
        for (String word : spec.trim().split("\\s+")) {
            tokens.add(parseToken(word));
        }
        return tokens;
    }

    public static Token parseToken(String word) {
        if (!word.startsWith("%")) return new Text(word);
        Matcher m = PARAM.matcher(word);
        if (m.matches()) {
            if (m.group(3) != null) return new Param('m', m.group(3));
            return new Param(m.group(1).charAt(0), m.group(2));
        }
        if (MENU_WITHOUT_NAME.matcher(word).find() && word.length() <= 2) {
            return new Invalid(word, "'%m' must be followed by '.name' (for example %m.view)", true);
        }
        return new Invalid(word, "'" + word + "' isn't a parameter (%s, %b, %d, %s.menu or %m.menu) and is shown as text; "
                + "parameters must be separated by spaces", false);
    }

    public static String toSpec(List<? extends Token> tokens) {
        StringBuilder spec = new StringBuilder();
        for (Token token : tokens) {
            if (spec.length() > 0) spec.append(' ');
            spec.append(token.text());
        }
        return spec.toString();
    }

    /** @return the parameters, in order */
    public static List<Param> params(List<? extends Token> tokens) {
        List<Param> params = new ArrayList<>();
        for (Token token : tokens) {
            if (token instanceof Param p) params.add(p);
        }
        return params;
    }

    public static int paramCount(String spec) {
        return params(parse(spec)).size();
    }

    /** @return the problems of a spec, as {@code error:} / {@code warning:} messages in reading order */
    public static List<Invalid> problems(List<? extends Token> tokens) {
        List<Invalid> problems = new ArrayList<>();
        for (Token token : tokens) {
            if (token instanceof Invalid invalid) problems.add(invalid);
        }
        return problems;
    }

    /** Index in {@code tokens} of the {@code n}th parameter (0-based), or -1. */
    public static int tokenIndexOfParam(List<? extends Token> tokens, int n) {
        int seen = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Param) {
                if (seen == n) return i;
                seen++;
            }
        }
        return -1;
    }

    // ---- editing (each returns a new list; the argument is left alone)

    public static List<Token> addParam(List<? extends Token> tokens, Param param) {
        List<Token> result = new ArrayList<>(tokens);
        result.add(param);
        return result;
    }

    public static List<Token> addText(List<? extends Token> tokens, String text) {
        List<Token> result = new ArrayList<>(tokens);
        for (String word : text.trim().split("\\s+")) {
            if (!word.isEmpty()) result.add(parseToken(word));
        }
        return result;
    }

    public static List<Token> remove(List<? extends Token> tokens, int tokenIndex) {
        List<Token> result = new ArrayList<>(tokens);
        result.remove(tokenIndex);
        return result;
    }

    public static List<Token> replace(List<? extends Token> tokens, int tokenIndex, Token token) {
        List<Token> result = new ArrayList<>(tokens);
        result.set(tokenIndex, token);
        return result;
    }

    /** Moves a token one place left ({@code delta} -1) or right (+1); other tokens keep their order. */
    public static List<Token> move(List<? extends Token> tokens, int tokenIndex, int delta) {
        int target = tokenIndex + delta;
        if (target < 0 || target >= tokens.size()) return new ArrayList<>(tokens);
        List<Token> result = new ArrayList<>(tokens);
        result.add(target, result.remove(tokenIndex));
        return result;
    }

    /**
     * Puts the {@code from}th parameter in the {@code to}th place among the parameters; words stay where they are,
     * so "show %s.a for %d.b" keeps its text while the parameters trade places.
     */
    public static List<Token> moveParam(List<? extends Token> tokens, int from, int to) {
        List<Param> params = new ArrayList<>(params(tokens));
        if (from < 0 || from >= params.size() || to < 0 || to >= params.size() || from == to) return new ArrayList<>(tokens);
        params.add(to, params.remove(from));
        List<Token> result = new ArrayList<>(tokens);
        int next = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof Param) result.set(i, params.get(next++));
        }
        return result;
    }

    /** Removes the {@code n}th parameter, with no other change. */
    public static List<Token> removeParam(List<? extends Token> tokens, int n) {
        int index = tokenIndexOfParam(tokens, n);
        return index < 0 ? new ArrayList<>(tokens) : remove(tokens, index);
    }

    /** Changes the type of the {@code n}th parameter, keeping its place. */
    public static List<Token> retypeParam(List<? extends Token> tokens, int n, Param param) {
        int index = tokenIndexOfParam(tokens, n);
        return index < 0 ? new ArrayList<>(tokens) : replace(tokens, index, param);
    }
}
