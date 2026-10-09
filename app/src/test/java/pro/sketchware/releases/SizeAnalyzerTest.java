package pro.sketchware.releases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pro.sketchware.analysis.Finding;
import pro.sketchware.releases.SizeAnalyzer.Part;
import pro.sketchware.releases.SizeAnalyzer.Report;

public class SizeAnalyzerTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static byte[] random(int size) {
        byte[] bytes = new byte[size];
        new Random(7).nextBytes(bytes); // does not compress
        return bytes;
    }

    private File zip(String name, Object... namesAndSizes) throws Exception {
        File file = temp.newFile(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (int i = 0; i < namesAndSizes.length; i += 2) {
                // Stored without compression, so the sizes in the archive are exactly the sizes given here
                byte[] content = random((Integer) namesAndSizes[i + 1]);
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(content);
                ZipEntry entry = new ZipEntry((String) namesAndSizes[i]);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(crc.getValue());
                out.putNextEntry(entry);
                out.write(content);
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void apkEntriesAreSortedIntoParts() throws Exception {
        File apk = zip("app.apk", "classes.dex", 3000, "classes2.dex", 1000, "res/layout/main.xml", 500, "resources.arsc", 700, "AndroidManifest.xml", 200,
                "assets/data.json", 400, "lib/arm64-v8a/libx.so", 2000, "META-INF/MANIFEST.MF", 100);
        Report report = SizeAnalyzer.analyze(apk, 3);
        assertEquals(4000, (long) report.byPart().get(Part.CODE));
        assertEquals(1400, (long) report.byPart().get(Part.RESOURCES));
        assertEquals(400, (long) report.byPart().get(Part.ASSETS));
        assertEquals(2000, (long) report.byPart().get(Part.NATIVE_LIBRARIES));
        assertEquals(100, (long) report.byPart().get(Part.OTHER));
        assertEquals("classes.dex", report.largest().get(0).name());
        assertEquals(3, report.largest().size());
        assertEquals(apk.length(), report.fileSize());
    }

    @Test
    public void appBundleModulesAreUnderstood() {
        assertEquals(Part.CODE, SizeAnalyzer.partOf("base/dex/classes.dex"));
        assertEquals(Part.RESOURCES, SizeAnalyzer.partOf("base/res/drawable/a.png"));
        assertEquals(Part.RESOURCES, SizeAnalyzer.partOf("base/resources.pb"));
        assertEquals(Part.NATIVE_LIBRARIES, SizeAnalyzer.partOf("base/lib/arm64-v8a/libx.so"));
        assertEquals("arm64-v8a", SizeAnalyzer.abiOf("base/lib/arm64-v8a/libx.so"));
        assertEquals("x86", SizeAnalyzer.abiOf("lib/x86/libx.so"));
        assertNull(SizeAnalyzer.abiOf("res/lib/x.png"));
        assertEquals(Part.OTHER, SizeAnalyzer.partOf("BUNDLE-METADATA/x/y"));
    }

    @Test
    public void extraArchitecturesAreReportedWithTheirSize() throws Exception {
        File apk = zip("abis.apk", "lib/arm64-v8a/a.so", 300_000, "lib/armeabi-v7a/a.so", 300_000, "lib/x86/a.so", 300_000, "lib/x86_64/a.so", 300_000);
        Report report = SizeAnalyzer.analyze(apk, 5);
        Finding f = report.findings().stream().filter(x -> x.id().equals("size.extra-abis")).findFirst().orElseThrow();
        assertEquals("x86, x86_64", f.evidence());
        assertTrue(f.title(), f.title().contains("585.9 KB"));
        assertEquals(4, report.byAbi().size());

        File fine = zip("fine.apk", "lib/arm64-v8a/a.so", 300_000, "lib/armeabi-v7a/a.so", 300_000);
        assertTrue(SizeAnalyzer.analyze(fine, 5).findings().isEmpty());
    }

    @Test
    public void largeAssetsAndSeveralDexFilesAreReported() throws Exception {
        File apk = zip("big.apk", "assets/video.mp4", 1_200_000, "res/drawable/small.png", 1000, "classes.dex", 100, "classes2.dex", 100);
        Report report = SizeAnalyzer.analyze(apk, 5);
        assertTrue(report.findings().stream().anyMatch(f -> f.id().equals("size.large-files") && f.evidence().startsWith("assets/video.mp4 (1.1 MB)")));
        assertTrue(report.findings().stream().anyMatch(f -> f.id().equals("size.multidex") && f.title().contains("2 dex")));
        assertFalse(report.findings().stream().anyMatch(f -> f.id().equals("size.extra-abis")));
    }

    @Test
    public void sizesAreFormattedForPeople() {
        assertEquals("512 B", SizeAnalyzer.format(512));
        assertEquals("3.5 KB", SizeAnalyzer.format(3584));
        assertEquals("2.0 MB", SizeAnalyzer.format(2 * 1024 * 1024));
    }

    @Test(expected = java.io.IOException.class)
    public void aFileThatIsNotAnArchiveIsRefused() throws Exception {
        File notZip = temp.newFile("not.apk");
        Files.writeString(notZip.toPath(), "hello");
        SizeAnalyzer.analyze(notZip, 3);
    }
}
