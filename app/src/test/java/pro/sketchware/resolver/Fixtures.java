package pro.sketchware.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Logs captured from the real AAPT2 (build-tools 37) and API answers captured from the real services. */
final class Fixtures {
    private Fixtures() {
    }

    static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/resolver/" + name)) {
            if (in == null) throw new IllegalStateException("Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
