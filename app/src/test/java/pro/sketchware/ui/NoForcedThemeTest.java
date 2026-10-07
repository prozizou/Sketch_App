package pro.sketchware.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Only {@code ThemeManager} may decide between light and dark. A screen that pins its own mode would
 * ignore the system setting and the user's choice in Appearance.
 */
public class NoForcedThemeTest {
    private static final Pattern FORCED = Pattern.compile("setLocalNightMode|setDefaultNightMode|(?<![A-Za-z_])MODE_NIGHT_(YES|NO)\\b");
    private static final String ALLOWED = "ThemeManager.java";

    @Test
    public void onlyThemeManagerPicksTheNightMode() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                    .filter(p -> !p.getFileName().toString().equals(ALLOWED))
                    .forEach(p -> {
                        try {
                            if (FORCED.matcher(Files.readString(p)).find()) {
                                offenders.add(p.toString());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertTrue("These files force a light/dark mode instead of following the system theme: " + offenders, offenders.isEmpty());
    }
}
