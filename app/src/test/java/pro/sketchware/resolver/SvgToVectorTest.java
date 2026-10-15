package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SvgToVectorTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static VectorModel convert(String svg) throws Exception {
        return SvgToVector.convert(svg, null);
    }

    private static String svg(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">" + body + "</svg>";
    }

    @Test
    public void aPathBecomesAFilledPath() throws Exception {
        VectorModel model = convert(svg("<path d=\"M1 1h4v4z\"/>"));
        assertEquals(24, model.width(), 0);
        assertEquals(24, model.viewportWidth(), 0);
        assertEquals(1, model.shapes().size());
        assertEquals(0xFF000000, model.shapes().get(0).fillColor());
        assertEquals(0, model.shapes().get(0).strokeColor());
    }

    @Test
    public void theChosenColourReplacesCurrentColorAndTheDefaultFill() throws Exception {
        VectorModel model = SvgToVector.convert(svg("<path fill=\"currentColor\" d=\"M1 1h4v4z\"/><path d=\"M2 2h4v4z\"/><path fill=\"#00FF00\" d=\"M3 3h1v1z\"/>"), "#FF336699");
        assertEquals(0xFF336699, model.shapes().get(0).fillColor());
        assertEquals(0xFF336699, model.shapes().get(1).fillColor());
        assertEquals("an explicit colour is kept", 0xFF00FF00, model.shapes().get(2).fillColor());
        assertTrue(model.toXml().contains("android:fillColor=\"#FF336699\""));
    }

    @Test
    public void groupsPassColoursDownAndTranslate() throws Exception {
        VectorModel model = convert(svg("<g fill=\"#FF0000\" transform=\"translate(2,3)\"><path d=\"M0 0h1v1z\"/></g>"));
        VectorModel.Shape shape = model.shapes().get(0);
        assertEquals(0xFFFF0000, shape.fillColor());
        assertEquals(2, shape.translateX(), 0);
        assertEquals(3, shape.translateY(), 0);
        String xml = model.toXml();
        assertTrue(xml.contains("<group"));
        assertTrue(xml.contains("android:translateX=\"2\""));
    }

    @Test
    public void shapesBecomePaths() throws Exception {
        VectorModel model = convert(svg("<rect x=\"2\" y=\"2\" width=\"10\" height=\"6\"/><circle cx=\"12\" cy=\"12\" r=\"4\"/>"
                + "<line x1=\"0\" y1=\"0\" x2=\"5\" y2=\"5\" stroke=\"#000\"/><polygon points=\"0,0 4,0 4,4\"/>"));
        assertEquals(4, model.shapes().size());
        assertEquals("M2,2h10v6h-10z", model.shapes().get(0).pathData());
        assertTrue(model.shapes().get(1).pathData().startsWith("M8,12a4,4 0 1,0 8,0"));
        assertEquals("a line is only a stroke", 0, model.shapes().get(2).fillColor());
        assertEquals(0xFF000000, model.shapes().get(2).strokeColor());
        assertEquals("M0,0L4,0L4,4z", model.shapes().get(3).pathData());
    }

    @Test
    public void strokesKeepTheirStyle() throws Exception {
        VectorModel model = convert(svg("<path d=\"M1 1l5 5\" fill=\"none\" stroke=\"#FF0000\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"bevel\"/>"));
        VectorModel.Shape shape = model.shapes().get(0);
        assertEquals(2, shape.strokeWidth(), 0);
        assertEquals("round", shape.strokeCap());
        assertEquals("bevel", shape.strokeJoin());
        String xml = model.toXml();
        assertTrue(xml.contains("android:strokeLineCap=\"round\""));
        assertTrue(xml.contains("android:strokeLineJoin=\"bevel\""));
        assertFalse(xml.contains("fillColor"));
    }

    @Test
    public void opacityAndEvenOddArePreserved() throws Exception {
        VectorModel model = convert(svg("<path d=\"M1 1h4v4z\" opacity=\".5\" fill-rule=\"evenodd\"/>"));
        assertEquals(0x80, model.shapes().get(0).fillColor() >>> 24);
        assertTrue(model.shapes().get(0).evenOdd());
        assertTrue(model.toXml().contains("android:fillType=\"evenOdd\""));
    }

    @Test
    public void aViewBoxWithoutWidthAndHeightSetsTheSize() throws Exception {
        VectorModel model = convert("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 256\"><path d=\"M0 0h1v1z\"/></svg>");
        assertEquals(512, model.width(), 0);
        assertEquals(256, model.height(), 0);
        assertEquals(256, model.viewportHeight(), 0);
    }

    @Test
    public void whatAnAndroidVectorCannotShowIsRefusedWithAReason() {
        for (String[] bad : new String[][]{
                {"<image href=\"a.png\"/>", "<image>"},
                {"<text>hi</text>", "<text>"},
                {"<filter id=\"f\"/>", "<filter>"},
                {"<mask id=\"m\"/>", "<mask>"},
                {"<linearGradient id=\"g\"/>", "<linearGradient>"},
                {"<script>alert(1)</script>", "<script>"},
                {"<use href=\"#x\"/>", "<use>"},
                {"<style>p{}</style>", "<style>"},
                {"<foreignObject/>", "<foreignObject>"},
                {"<a href=\"http://x\"><path d=\"M0 0h1v1z\"/></a>", "<a>"},
                {"<path fill=\"url(#g)\" d=\"M0 0h1v1z\"/>", "gradient"},
                {"<g transform=\"rotate(45)\"><path d=\"M0 0h1v1z\"/></g>", "translate"},
                {"<path fill=\"chartreuse\" d=\"M0 0h1v1z\"/>", "colour"},
                {"<path d=\"M0 0 javascript:alert(1)\"/>", "characters"},
        }) {
            SvgToVector.Unsupported e = assertThrows(bad[0], SvgToVector.Unsupported.class, () -> convert(svg(bad[0])));
            assertTrue(bad[0] + " -> " + e.getMessage(), e.getMessage().contains(bad[1]));
        }
    }

    @Test
    public void notAnSvgOrNotValidIsRefused() {
        assertThrows(SvgToVector.Unsupported.class, () -> convert(""));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("<vector/>"));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("<svg><path d=\"M0 0\"></svg>"));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"></svg>"));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("<!DOCTYPE svg [<!ENTITY x \"y\">]><svg viewBox=\"0 0 1 1\"><path d=\"M0 0h1v1z\"/></svg>"));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("<svg viewBox=\"1 1 24 24\"><path d=\"M0 0h1v1z\"/></svg>"));
        assertThrows(SvgToVector.Unsupported.class, () -> convert("x".repeat(SvgToVector.MAX_BYTES + 1)));
    }

    @Test
    public void theColourTheUserPicksMustBeAnAndroidColour() throws Exception {
        assertEquals(0xFF123456, SvgToVector.androidColor("#123456"));
        assertEquals(0x80123456, SvgToVector.androidColor("#80123456"));
        assertThrows(SvgToVector.Unsupported.class, () -> SvgToVector.androidColor("red"));
        assertThrows(SvgToVector.Unsupported.class, () -> SvgToVector.androidColor("#12"));
    }

    /** The whole shipped library, converted, must be accepted by the real AAPT2 in one compile. */
    @Test
    public void everyIconOfTheShippedPackConvertsAndCompiles() throws Exception {
        assumeTrue(IconPackFixture.available());
        File aapt2 = Aapt2Locator.find();
        assumeTrue("needs the Android SDK's aapt2", aapt2 != null);
        File svgRoot = IconPackFixture.unpack(temp.newFolder());
        File res = temp.newFolder();
        int converted = 0;
        List<String> refused = new ArrayList<>();
        File[] icons = svgRoot.listFiles(File::isDirectory);
        for (File icon : icons) {
            for (String style : new String[]{"baseline", "outline"}) {
                File file = new File(icon, style + ".svg");
                if (!file.isFile()) continue;
                try {
                    VectorModel model = SvgToVector.convert(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), "#FF000000");
                    TestFiles.write(res, "drawable/ic_" + icon.getName() + "_" + style + ".xml", model.toXml());
                    converted++;
                } catch (SvgToVector.Unsupported e) {
                    refused.add(icon.getName() + "/" + style + ": " + e.getMessage());
                }
            }
        }
        System.out.println("PACK-STATS converted=" + converted + " refused=" + refused.size() + " " + refused.subList(0, Math.min(8, refused.size())));
        assertTrue("converted " + converted + ", refused: " + refused.subList(0, Math.min(5, refused.size())), converted > 4000);
        assertTrue("almost every icon converts (refused " + refused.size() + "): " + refused.subList(0, Math.min(5, refused.size())),
                refused.size() <= converted / 50);

        File out = new File(temp.newFolder(), "out.zip");
        Process process = new ProcessBuilder(aapt2.getAbsolutePath(), "compile", "--dir", new File(res, "drawable").getParentFile().getAbsolutePath(), "-o", out.getAbsolutePath())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.waitFor());
        assertEquals("AAPT2 printed: " + output.substring(0, Math.min(output.length(), 600)), "", output.trim());
        assertTrue(out.length() > 0);
    }
}
