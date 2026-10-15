package pro.sketchware.resolver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Runs the same two AAPT2 steps the build runs on a {@code res} folder (compile, then link), and returns what AAPT2 printed. */
final class Aapt2Build {
    private Aapt2Build() {
    }

    /** @return AAPT2's output ("" when the resources compile and link) */
    static String run(File aapt2, File androidJar, File resDir, File manifest, File work) throws IOException, InterruptedException {
        work.mkdirs();
        File compiled = new File(work, "res.zip");
        compiled.delete();
        String compile = exec(aapt2.getAbsolutePath(), "compile", "--dir", resDir.getAbsolutePath(), "-o", compiled.getAbsolutePath());
        if (!compile.isEmpty()) return compile;
        return exec(aapt2.getAbsolutePath(), "link", "--auto-add-overlay", "-I", androidJar.getAbsolutePath(), "-R", compiled.getAbsolutePath(),
                "--manifest", manifest.getAbsolutePath(), "-o", new File(work, "out.apk").getAbsolutePath());
    }

    private static String exec(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output.trim();
    }

    static File manifest(File dir, String body) throws IOException {
        File file = new File(dir, "AndroidManifest.xml");
        Files.write(file.toPath(), ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.my.app\">"
                + body + "</manifest>").getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
