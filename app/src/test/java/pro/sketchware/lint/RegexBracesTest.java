package pro.sketchware.lint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Android's regular expressions (ICU) reject a brace that is not escaped and not part of a {n,m} quantifier, while
 * the desktop JVM that runs these tests accepts it. A pattern like "\\{(\\w+)}" therefore passes every unit test and
 * crashes on the phone. This test reads the app's sources and checks the regular expressions written as literals.
 */
public class RegexBracesTest {
    /** Calls whose first argument is a regular expression. */
    private static final Pattern REGEX_CALL = Pattern.compile(
            "(?:Pattern\\.compile|\\.matches|\\.replaceAll|\\.replaceFirst|\\.split)\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern QUANTIFIER = Pattern.compile("\\{\\d+(?:,\\d*)?\\}");

    /** The regular expression a Java string literal holds (only the escapes that matter here). */
    static String unescapeJava(String literal) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c == '\\' && i + 1 < literal.length()) {
                char next = literal.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Index of the first brace Android would reject, or -1. Braces inside a [character class] are accepted. */
    static int unsafeBrace(String regex) {
        int classDepth = 0;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '[') {
                // A ] right after [ or [^ is a literal ]
                classDepth++;
                if (i + 1 < regex.length() && regex.charAt(i + 1) == '^') i++;
                if (i + 1 < regex.length() && regex.charAt(i + 1) == ']') i++;
                continue;
            }
            if (c == ']' && classDepth > 0) {
                classDepth--;
                continue;
            }
            if (classDepth > 0) continue;
            if (c == '{') {
                Matcher quantifier = QUANTIFIER.matcher(regex).region(i, regex.length());
                if (quantifier.lookingAt()) {
                    i = quantifier.end() - 1;
                    continue;
                }
                return i;
            }
            if (c == '}') return i;
        }
        return -1;
    }

    @Test
    public void recognisesSafeAndUnsafeBraces() {
        assertEquals(-1, unsafeBrace("a{2}b{1,}c{0,3}"));
        assertEquals(-1, unsafeBrace("\\{(\\w+)\\}"));
        assertEquals(10, unsafeBrace("\\{([^}/]*)}"));
        assertTrue(unsafeBrace("{x}") >= 0);
        assertEquals(-1, unsafeBrace("[(),;<>{}*]*"));
        assertEquals(-1, unsafeBrace("[^{;]*\\{\\s*\\}"));
        assertEquals(-1, unsafeBrace("[]{}]"));
        assertEquals("\\{([^}/]*)}", unescapeJava("\\\\{([^}/]*)}"));
    }

    @Test
    public void appRegularExpressionsAreValidOnAndroid() throws IOException {
        Path root = Paths.get("src/main/java");
        if (!Files.isDirectory(root)) root = Paths.get("app/src/main/java");
        assertTrue("Sources not found from " + Paths.get("").toAbsolutePath(), Files.isDirectory(root));
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int n = 0; n < lines.size(); n++) {
                    Matcher call = REGEX_CALL.matcher(lines.get(n));
                    while (call.find()) {
                        String regex = unescapeJava(call.group(1));
                        if (unsafeBrace(regex) >= 0) problems.add(root.relativize(file) + ":" + (n + 1) + "  " + regex);
                    }
                }
            }
        }
        assertFalse("Unescaped braces crash on Android:\n" + String.join("\n", problems), !problems.isEmpty());
    }
}
