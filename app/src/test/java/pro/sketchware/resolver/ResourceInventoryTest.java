package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import pro.sketchware.resolver.ResourceInventory.Definition;

public class ResourceInventoryTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private ResourceInventory project() throws Exception {
        File res = temp.newFolder("res");
        File images = temp.newFolder("images");
        TestFiles.write(res, "drawable/ic_a.xml", TestFiles.VECTOR);
        TestFiles.write(res, "drawable-night/ic_a.xml", TestFiles.VECTOR);
        TestFiles.write(res, "drawable-xhdpi/logo.9.png", TestFiles.png(4, 4));
        TestFiles.write(res, "mipmap-xxhdpi/ic_launcher.png", TestFiles.png(144, 144));
        TestFiles.write(res, "layout/main.xml", "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"/>");
        TestFiles.write(res, "values/colors.xml", "<resources><color name=\"brand\">#FF5722</color></resources>");
        TestFiles.write(res, "values-night/colors.xml", "<resources><color name=\"brand\">#FFAB91</color><color name=\"night_only\">#000000</color></resources>");
        TestFiles.write(res, "values/strings.xml", "<resources><string name=\"app_name\">App</string></resources>");
        TestFiles.write(res, "values/broken.xml", "<resources><string name=\"x\">oops</resources>");
        TestFiles.write(res, "drawable/.hidden.xml", TestFiles.VECTOR);
        TestFiles.write(images, "photo.png", TestFiles.png(2, 2));
        return ResourceInventory.scan(res, images);
    }

    @Test
    public void listsFilesAndValuesWithTheirQualifiers() throws Exception {
        ResourceInventory inventory = project();
        List<Definition> icon = inventory.find("drawable", "ic_a");
        assertEquals(List.of("", "-night"), icon.stream().map(Definition::qualifier).toList());
        assertEquals(ResourceInventory.Kind.FILE, icon.get(0).kind());
        assertEquals(".xml", icon.get(0).extension());

        Definition logo = inventory.find("drawable", "logo").get(0);
        assertEquals("a .9.png is the resource 'logo'", "-xhdpi", logo.qualifier());
        assertEquals(".9.png", logo.extension());

        assertEquals("#FF5722", inventory.find("color", "brand").get(0).value());
        assertEquals(List.of("", "-night"), inventory.find("color", "brand").stream().map(Definition::qualifier).toList());
        assertEquals("mipmap", inventory.find("mipmap", "ic_launcher").get(0).type());
        assertEquals("layout", inventory.find("layout", "main").get(0).type());
    }

    @Test
    public void imagesOfTheImageManagerAreDrawables() throws Exception {
        Definition photo = project().find("drawable", "photo").get(0);
        assertEquals(ResourceInventory.Origin.PROJECT_IMAGES, photo.origin());
        assertEquals("", photo.qualifier());
    }

    @Test
    public void hiddenFilesAreIgnoredAndBrokenValuesFilesAreReported() throws Exception {
        ResourceInventory inventory = project();
        assertTrue(inventory.find("drawable", ".hidden").isEmpty());
        assertEquals(1, inventory.unreadableValuesFiles().size());
        assertTrue(inventory.unreadableValuesFiles().get(0).endsWith("values/broken.xml"));
    }

    @Test
    public void collisionsAreCheckedPerQualifierAndWithoutCase() throws Exception {
        ResourceInventory inventory = project();
        assertTrue(inventory.has("drawable", "ic_a", ""));
        assertTrue(inventory.has("drawable", "IC_A", "-night"));
        assertFalse(inventory.has("drawable", "ic_a", "-xhdpi"));
        assertFalse(inventory.has("color", "night_only", ""));
        assertTrue(inventory.has("color", "night_only", "-night"));
    }

    @Test
    public void aDotAndAnUnderscoreAreTheSameRName() throws Exception {
        File res = temp.newFolder("res2");
        TestFiles.write(res, "values/s.xml", "<resources><string name=\"a.b\">x</string></resources>");
        ResourceInventory inventory = ResourceInventory.scan(res, null);
        assertEquals(1, inventory.find("string", "a_b").size());
        assertTrue(inventory.has("string", "a_b", ""));
    }

    @Test
    public void aMissingFolderIsAnEmptyInventory() {
        ResourceInventory inventory = ResourceInventory.scan(new File(temp.getRoot(), "nope"), null);
        assertTrue(inventory.all().isEmpty());
        assertTrue(ResourceInventory.scan(null, null).all().isEmpty());
    }
}
