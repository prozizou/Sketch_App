package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Asks the real AAPT2 whether it accepts the resource, by compiling it alone in a scratch folder, the way the build's
 * "compile" step does. Nothing of the project is touched. As in the build, any output from {@code aapt2 compile} is
 * an error.
 */
public final class Aapt2Validator implements ResourceValidator {
    private static final long TIMEOUT_SECONDS = 30;

    private final File aapt2;
    private final File scratchRoot;

    /**
     * @param aapt2       the AAPT2 binary (the app unpacks it to its cache folder when it builds)
     * @param scratchRoot a folder to make scratch folders in
     */
    public Aapt2Validator(File aapt2, File scratchRoot) {
        this.aapt2 = aapt2;
        this.scratchRoot = scratchRoot;
    }

    /** Whether the binary is there and can run; if not, the built-in checks are all there is. */
    public boolean isAvailable() {
        return aapt2.isFile() && aapt2.canExecute();
    }

    @NonNull
    @Override
    public Result validateFile(@NonNull String relativePath, @NonNull byte[] content) {
        return compile(relativePath, content);
    }

    @NonNull
    @Override
    public Result validateValues(@NonNull String relativePath, @NonNull String xml) {
        return compile(relativePath, xml.getBytes(StandardCharsets.UTF_8));
    }

    private Result compile(String relativePath, byte[] content) {
        File scratch = null;
        try {
            if (!scratchRoot.isDirectory() && !scratchRoot.mkdirs()) return Result.fail("Couldn't prepare the AAPT2 check");
            scratch = Files.createTempDirectory(scratchRoot.toPath(), "aapt2-check-").toFile();
            File res = new File(scratch, "res");
            File target = new File(res, relativePath);
            if (!target.getCanonicalPath().startsWith(res.getCanonicalPath() + File.separator)) return Result.fail("Not a path under res");
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) return Result.fail("Couldn't prepare the AAPT2 check");
            Files.write(target.toPath(), content);

            List<String> command = new ArrayList<>(List.of(aapt2.getAbsolutePath(), "compile", "--dir", res.getAbsolutePath(), "-o", new File(scratch, "out.zip").getAbsolutePath()));
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            Process process = builder.start();
            java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            try (java.io.InputStream in = process.getInputStream()) {
                int read;
                while ((read = in.read(chunk)) != -1) captured.write(chunk, 0, read);
            }
            byte[] output = captured.toByteArray();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Result.fail("AAPT2 took too long to check the file");
            }
            String text = new String(output, StandardCharsets.UTF_8).trim();
            if (process.exitValue() == 0 && text.isEmpty()) return Result.OK;
            return Result.fail("AAPT2: " + summarise(text, scratch));
        } catch (IOException e) {
            return Result.fail("AAPT2 couldn't check the file: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("The AAPT2 check was interrupted");
        } finally {
            if (scratch != null) deleteQuietly(scratch);
        }
    }

    /** The first error line, without the scratch folder's path. */
    private static String summarise(String output, File scratch) {
        String cleaned = output.replace(scratch.getAbsolutePath() + File.separator, "");
        for (String line : cleaned.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.endsWith("file failed to compile.")) continue;
            return trimmed.replaceFirst("^res" + java.util.regex.Pattern.quote(File.separator), "");
        }
        return cleaned.isEmpty() ? "the file was refused" : cleaned;
    }

    private static void deleteQuietly(File root) {
        try (Stream<java.nio.file.Path> walk = Files.walk(root.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A leftover scratch folder is harmless; the next check uses a new one.
        }
    }
}
