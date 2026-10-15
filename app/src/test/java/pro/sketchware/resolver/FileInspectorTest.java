package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import pro.sketchware.resolver.FileInspector.Format;
import pro.sketchware.resolver.FileInspector.Inspection;

public class FileInspectorTest {
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Inspection inspect(String type, byte[] content) {
        return FileInspector.inspect(type, content, null);
    }

    @Test
    public void readsThePixelSizeOfAPng() {
        Inspection png = inspect("drawable", TestFiles.png(48, 32));
        assertTrue(png.problem(), png.ok());
        assertEquals(Format.PNG, png.format());
        assertEquals(48, png.width());
        assertEquals(32, png.height());
        assertEquals(".png", png.extension());
    }

    @Test
    public void aTruncatedOrDamagedPngIsRefused() {
        byte[] png = TestFiles.png(8, 8);
        Inspection cut = inspect("drawable", Arrays.copyOf(png, png.length - 20));
        assertFalse(cut.ok());
        assertTrue(cut.problem(), cut.problem().contains("incomplete"));
        byte[] header = png.clone();
        header[13] = 'X';
        assertTrue(inspect("drawable", header).problem().contains("header"));
        assertFalse(inspect("drawable", Arrays.copyOf(png, 20)).ok());
    }

    @Test
    public void readsTheSizeOfEveryKindOfWebp() {
        for (byte[] webp : new byte[][]{TestFiles.webpLossy(96, 64), TestFiles.webpLossless(96, 64), TestFiles.webpExtended(96, 64)}) {
            Inspection inspection = inspect("drawable", webp);
            assertTrue(inspection.problem(), inspection.ok());
            assertEquals(Format.WEBP, inspection.format());
            assertEquals(96, inspection.width());
            assertEquals(64, inspection.height());
            assertEquals(".webp", inspection.extension());
        }
        byte[] unknown = TestFiles.webpLossy(10, 10);
        unknown[12] = 'Z';
        assertFalse(inspect("drawable", unknown).ok());
    }

    @Test
    public void imagesThatAreTooLargeOrNotSupportedAreRefusedWithTheReason() {
        assertTrue(inspect("drawable", TestFiles.png(5000, 10)).problem().contains("4096"));
        assertTrue(inspect("drawable", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0}).problem().contains("JPEG"));
        assertTrue(inspect("drawable", new byte[]{1, 2, 3, 4, 5}).problem().contains("isn't a PNG"));
        assertFalse(inspect("drawable", new byte[0]).ok());
        assertFalse(inspect("drawable", null).ok());
        Inspection big = inspect("drawable", TestFiles.png(2000, 2));
        assertTrue("a big image is allowed with a warning", big.ok());
        assertEquals(1, big.notes().size());
    }

    @Test
    public void aValidVectorIsAccepted() {
        Inspection vector = inspect("drawable", bytes(TestFiles.VECTOR));
        assertTrue(vector.problem(), vector.ok());
        assertEquals(Format.VECTOR_XML, vector.format());
        assertNotNull("a preview can be drawn", vector.preview());
        assertEquals(24, vector.preview().viewportWidth(), 0);
        assertEquals(1, vector.preview().shapes().size());
        assertEquals(0xFF000000, vector.preview().shapes().get(0).fillColor());
    }

    @Test
    public void vectorsThatTheBuildWouldRejectAreRefused() {
        String noViewport = TestFiles.VECTOR.replace(" android:viewportWidth=\"24\"", "");
        assertTrue(inspect("drawable", bytes(noViewport)).problem().contains("viewportWidth"));
        String zero = TestFiles.VECTOR.replace("android:viewportHeight=\"24\"", "android:viewportHeight=\"0\"");
        assertTrue(inspect("drawable", bytes(zero)).problem().contains("more than 0"));
        String noNamespace = TestFiles.VECTOR.replace("xmlns:android=\"http://schemas.android.com/apk/res/android\"", "");
        assertTrue(inspect("drawable", bytes(noNamespace)).problem().contains("namespace"));
        String noPath = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"1dp\" android:height=\"1dp\" "
                + "android:viewportWidth=\"1\" android:viewportHeight=\"1\"></vector>";
        assertTrue(inspect("drawable", bytes(noPath)).problem().contains("<path>"));
    }

    @Test
    public void invalidXmlIsRefusedWithItsLine() {
        Inspection broken = inspect("drawable", bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<path>\n</vector>"));
        assertFalse(broken.ok());
        assertTrue(broken.problem(), broken.problem().startsWith("The XML isn't valid"));
        assertTrue(broken.problem(), broken.problem().contains("line 3"));
        assertNull(broken.preview());
    }

    @Test
    public void aDoctypeIsRefused() {
        Inspection xxe = inspect("drawable", bytes("<?xml version=\"1.0\"?><!DOCTYPE v [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><vector>&e;</vector>"));
        assertFalse(xxe.ok());
        assertTrue(xxe.problem(), xxe.problem().contains("DOCTYPE"));
    }

    @Test
    public void otherDrawableXmlIsAcceptedAndLayoutsAreNot() {
        String shape = "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"><solid android:color=\"#FF0000\"/></shape>";
        Inspection ok = inspect("drawable", bytes(shape));
        assertTrue(ok.ok());
        assertEquals(Format.DRAWABLE_XML, ok.format());
        assertTrue(inspect("drawable", bytes("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"/>")).problem().contains("not a drawable"));
        assertTrue(inspect("drawable", bytes("<shape><solid/></shape>")).problem().contains("namespace"));
    }

    @Test
    public void anAdaptiveIconBelongsInMipmap() {
        String adaptive = "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\"><background android:drawable=\"@color/bg\"/>"
                + "<foreground android:drawable=\"@drawable/fg\"/></adaptive-icon>";
        Inspection mipmap = inspect("mipmap", bytes(adaptive));
        assertTrue(mipmap.problem(), mipmap.ok());
        assertEquals(Format.ADAPTIVE_ICON_XML, mipmap.format());
        assertTrue(inspect("drawable", bytes(adaptive)).problem().contains("mipmap"));
        assertTrue(inspect("mipmap", bytes(adaptive.replace("foreground", "foregroundX"))).problem().contains("<foreground>"));
    }

    @Test
    public void anSvgIsConvertedToAVectorDrawable() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path d=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></svg>";
        Inspection converted = inspect("drawable", bytes(svg));
        assertTrue(converted.problem(), converted.ok());
        assertEquals(Format.SVG, converted.format());
        assertEquals(".xml", converted.extension());
        String xml = new String(converted.content(), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<vector"));
        assertTrue(xml.contains("android:pathData=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\""));
        assertTrue(xml.contains("android:fillColor=\"#FF000000\""));
        // and what was written is itself a valid vector
        assertEquals(Format.VECTOR_XML, inspect("drawable", converted.content()).format());
    }

    @Test
    public void anSvgWithUnsupportedPartsIsRefusedNotChanged() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><image href=\"x.png\"/></svg>";
        Inspection refused = inspect("drawable", bytes(svg));
        assertFalse(refused.ok());
        assertTrue(refused.problem(), refused.problem().contains("<image>"));
    }

    @Test
    public void textThatIsNotXmlIsRefused() {
        assertFalse(inspect("drawable", bytes("<not xml")).ok());
        assertFalse(inspect("drawable", bytes("hello")).ok());
    }
}
