package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class ResourceValidatorTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ResourceValidator.Result file(ResourceValidator validator, String path, byte[] content) {
        return validator.validateFile(path, content);
    }

    @Test
    public void builtInAcceptsWellPlacedFiles() {
        BuiltInValidator v = new BuiltInValidator();
        assertTrue(file(v, "drawable/ic_add.xml", bytes(TestFiles.VECTOR)).ok());
        assertTrue(file(v, "drawable-xhdpi/logo.png", TestFiles.png(8, 8)).ok());
        assertTrue(file(v, "mipmap-xxhdpi/ic_launcher.png", TestFiles.png(144, 144)).ok());
        assertTrue(file(v, "drawable-nodpi/bg.webp", TestFiles.webpLossy(10, 10)).ok());
        assertTrue(file(v, "mipmap-anydpi-v26/ic_launcher.xml", bytes("<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\"><foreground android:drawable=\"@drawable/f\"/></adaptive-icon>")).ok());
        assertTrue("AAPT2 accepts uppercase, so a name the log asks for is kept", file(v, "drawable/IcAdd.xml", bytes(TestFiles.VECTOR)).ok());
    }

    @Test
    public void builtInRefusesBadNamesPathsAndExtensions() {
        BuiltInValidator v = new BuiltInValidator();
        assertTrue(file(v, "drawable/ic.add.xml", bytes(TestFiles.VECTOR)).message().contains("path") || !file(v, "drawable/ic.add.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "drawable/1icon.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "drawable/my icon.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "layout/main.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "../drawable/x.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "drawable/x.jpg", TestFiles.png(2, 2)).ok());
        assertFalse(file(v, "drawable-night/x.xml", bytes(TestFiles.VECTOR)).ok());
        assertFalse("anydpi is for mipmap only", file(v, "drawable-anydpi-v26/x.xml", bytes(TestFiles.VECTOR)).ok());
    }

    @Test
    public void builtInRefusesContentThatDoesNotMatch() {
        BuiltInValidator v = new BuiltInValidator();
        assertFalse(file(v, "drawable/x.png", bytes(TestFiles.VECTOR)).ok());
        assertFalse(file(v, "drawable/x.xml", TestFiles.png(2, 2)).ok());
        assertFalse(file(v, "drawable/x.webp", TestFiles.png(2, 2)).ok());
        assertFalse(file(v, "drawable/x.png", bytes("not an image")).ok());
        assertFalse(file(v, "drawable/x.xml", bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"><path></vector>")).ok());
        assertTrue(file(v, "drawable/x.xml", bytes("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><path d=\"M0 0h1v1z\"/></svg>")).message().contains("converted"));
    }

    @Test
    public void builtInChecksValuesFiles() {
        BuiltInValidator v = new BuiltInValidator();
        assertTrue(v.validateValues("values/colors.xml", "<resources><color name=\"a\">#FF0000</color></resources>").ok());
        assertTrue(v.validateValues("values-night/colors.xml", "<resources/>").ok());
        ResourceValidator.Result broken = v.validateValues("values/colors.xml", "<resources><color name=\"a\">#FF0000</resources>");
        assertFalse(broken.ok());
        assertTrue(broken.message(), broken.message().contains("isn't valid XML"));
        ResourceValidator.Result duplicate = v.validateValues("values/colors.xml", "<resources><color name=\"a\">#FF0000</color><color name=\"a\">#00FF00</color></resources>");
        assertFalse(duplicate.ok());
        assertTrue(duplicate.message(), duplicate.message().contains("defined twice"));
        assertFalse("a.b and a_b are one resource", v.validateValues("values/s.xml", "<resources><string name=\"a.b\">x</string><string name=\"a_b\">y</string></resources>").ok());
        assertFalse(v.validateValues("res/colors.xml", "<resources/>").ok());
        assertFalse(v.validateValues("values/colors.xml", "<other/>").ok());
    }

    // ---- the real AAPT2 ----

    private Aapt2Validator aapt2() throws Exception {
        File binary = Aapt2Locator.find();
        assumeTrue("needs the Android SDK's aapt2", binary != null);
        return new Aapt2Validator(binary, temp.newFolder());
    }

    @Test
    public void aapt2AcceptsWhatItShouldAndSaysWhyWhenNot() throws Exception {
        Aapt2Validator v = aapt2();
        assertTrue(v.isAvailable());
        assertTrue(file(v, "drawable/ic_ok.xml", bytes(TestFiles.VECTOR)).message(), file(v, "drawable/ic_ok.xml", bytes(TestFiles.VECTOR)).ok());
        assertTrue(file(v, "drawable-xhdpi/ok.png", TestFiles.png(16, 16)).ok());
        assertTrue(file(v, "mipmap-xxhdpi/ic_launcher.png", TestFiles.png(144, 144)).ok());

        ResourceValidator.Result broken = file(v, "drawable/ic_bad.xml", bytes("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"><path></vector>"));
        assertFalse(broken.ok());
        assertTrue(broken.message(), broken.message().startsWith("AAPT2: "));
        assertTrue(broken.message(), broken.message().contains("mismatched tag"));
        assertFalse("the scratch folder isn't shown", broken.message().contains(temp.getRoot().getName()));

        ResourceValidator.Result fakePng = file(v, "drawable/fake.png", bytes("not a png"));
        assertFalse(fakePng.ok());
        assertTrue(fakePng.message(), fakePng.message().contains("PNG signature"));

        ResourceValidator.Result dotted = file(v, "drawable/ic.dot.xml", bytes(TestFiles.VECTOR));
        assertFalse(dotted.ok());
        assertTrue(dotted.message(), dotted.message().contains("'.'"));
    }

    @Test
    public void aapt2ChecksValuesAndAliases() throws Exception {
        Aapt2Validator v = aapt2();
        String ok = "<resources><color name=\"a\">#FF0000</color><color name=\"b\">@color/a</color>"
                + "<drawable name=\"d\">@drawable/ic</drawable><string name=\"s\">It\\'s</string></resources>";
        assertTrue(v.validateValues("values/alias.xml", ok).message(), v.validateValues("values/alias.xml", ok).ok());
        ResourceValidator.Result bad = v.validateValues("values/bad.xml", "<resources><string name=\"s\">unterminated</resources>");
        assertFalse(bad.ok());
        assertTrue(bad.message(), bad.message().startsWith("AAPT2: "));
    }

    @Test
    public void aapt2NeverWritesOutsideItsScratchFolder() throws Exception {
        Aapt2Validator v = aapt2();
        ResourceValidator.Result escape = file(v, "../../escape.xml", bytes("<a/>"));
        assertFalse(escape.ok());
        assertFalse(new File(temp.getRoot().getParentFile(), "escape.xml").exists());
    }

    @Test
    public void theStandardValidatorUsesAapt2OnlyWhenItCanRun() throws Exception {
        ResourceValidator without = CompositeValidator.standard(new Aapt2Validator(new File(temp.getRoot(), "no-aapt2"), temp.newFolder()));
        assertTrue("built-in checks alone", without instanceof BuiltInValidator);
        assertTrue(CompositeValidator.standard(null) instanceof BuiltInValidator);
        File binary = Aapt2Locator.find();
        if (binary != null) {
            ResourceValidator both = CompositeValidator.standard(new Aapt2Validator(binary, temp.newFolder()));
            assertTrue(both instanceof CompositeValidator);
            assertEquals(false, file(both, "drawable/fake.png", bytes("x")).ok());
            assertTrue(file(both, "drawable/ok.xml", bytes(TestFiles.VECTOR)).ok());
        }
    }
}
