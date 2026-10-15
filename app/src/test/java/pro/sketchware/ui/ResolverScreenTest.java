package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import pro.sketchware.R;
import pro.sketchware.resolver.BuiltInValidator;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.MissingResourceParser;
import pro.sketchware.resolver.PackIconLibrary;
import pro.sketchware.resolver.ResolverFlow;
import pro.sketchware.resolver.ResolverSession;
import pro.sketchware.resolver.ResourceInventory;
import pro.sketchware.resolver.ResourceValidator;
import pro.sketchware.resolver.ui.IconPickerSheet;
import pro.sketchware.resolver.ui.ResourceResolverSheet;

/** The resolver screen: each page and state, driven by taps, in light and dark, on a narrow and a normal phone. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
public class ResolverScreenTest {
    private static final File OUTPUT = new File("build/reports/resolver-renders");
    private static final String LOG = "/p/res/layout/main.xml:12: error: resource drawable/ic_add (aka x:drawable/ic_add) not found.\n"
            + "/p/res/layout/main.xml:20: error: resource drawable/ic_search (aka x:drawable/ic_search) not found.\n"
            + "/p/res/layout/main.xml:31: error: resource color/brand (aka x:color/brand) not found.\n"
            + "/p/res/layout/main.xml:40: error: resource mipmap/ic_launcher (aka x:mipmap/ic_launcher) not found.\n"
            + "/p/res/layout/main.xml:44: error: resource android:drawable/nope not found.\n";
    private static final String ADD = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></svg>";
    private static final String VECTOR = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" "
            + "android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:fillColor=\"#FF000000\" "
            + "android:pathData=\"M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></vector>";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Hosts the sheet, like the compile log does. */
    public static class HostActivity extends AppCompatActivity implements ResourceResolverSheet.Host {
        static ResolverFlow flow;
        static IconLibrary icons;
        static Boolean finishedWithRebuild;
        static List<String> finishedKeys;
        static String openedLocation;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_SketchwarePro);
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }

        @Override
        public ResolverFlow resolverFlow() {
            return flow;
        }

        @Override
        public IconLibrary resolverIcons() {
            return icons;
        }

        @Override
        public void onResolverFinished(boolean rebuild, List<String> keysBefore) {
            finishedWithRebuild = rebuild;
            finishedKeys = keysBefore;
        }

        @Override
        public void onResolverOpenLocation(MissingResource problem, MissingResource.Occurrence occurrence) {
            openedLocation = occurrence.path() + ":" + occurrence.line();
        }
    }

    private File res;

    @Before
    public void setUp() throws Exception {
        HostActivity.finishedWithRebuild = null;
        HostActivity.finishedKeys = null;
        HostActivity.openedLocation = null;
        res = temp.newFolder("res");
        File pack = temp.newFolder("pack");
        write(pack, "svg/add/baseline.svg", ADD);
        write(pack, "svg/add/outline.svg", ADD);
        write(pack, "svg/search/baseline.svg", ADD);
        write(res, "drawable/ic_search_24dp.xml", VECTOR);
        HostActivity.icons = new PackIconLibrary(new File(pack, "svg"));
    }

    @After
    public void tearDown() {
        HostActivity.flow = null;
        HostActivity.icons = null;
    }

    private static void write(File root, String path, String content) throws Exception {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        java.nio.file.Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Refuse implements ResourceValidator {
        @Override
        public Result validateFile(String relativePath, byte[] content) {
            return Result.fail("refused " + relativePath);
        }

        @Override
        public Result validateValues(String relativePath, String xml) {
            return Result.fail("refused " + relativePath);
        }
    }

    private ResolverFlow flow(ResourceValidator validator) {
        ResolverSession session = new ResolverSession(MissingResourceParser.parse(LOG), ResourceInventory.scan(res, null),
                HostActivity.icons, validator, res, new File(temp.getRoot(), "journal"), "#000000");
        return new ResolverFlow(session, Runnable::run, Runnable::run);
    }

    private ResourceResolverSheet open(boolean dark, ResourceValidator validator) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        HostActivity.flow = flow(validator);
        HostActivity activity = Robolectric.buildActivity(HostActivity.class).setup().get();
        ResourceResolverSheet sheet = ResourceResolverSheet.newInstance(null);
        sheet.show(activity.getSupportFragmentManager(), "resolver");
        idle();
        assertNotNull(sheet.getView());
        return sheet;
    }

    private static <T extends View> T find(ResourceResolverSheet sheet, int id) {
        return sheet.requireView().findViewById(id);
    }

    /** Runs what is due now on the main thread, without letting the clock run (the countdown must not finish by itself). */
    private static void idle() {
        try {
            Class.forName("org.robolectric.shadows.ShadowLooper").getMethod("idleMainLooper").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Lets {@code millis} of the app's time pass. */
    private static void advance(long millis) {
        try {
            Class.forName("org.robolectric.shadows.ShadowLooper").getMethod("idleMainLooper", long.class, java.util.concurrent.TimeUnit.class)
                    .invoke(null, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void layout(View root, int widthPx) {
        layout(root, widthPx, 2000);
    }

    private static void layout(View root, int widthPx, int heightPx) {
        root.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, widthPx, heightPx);
    }

    private static File render(View root, String name, int widthPx) throws Exception {
        layout(root, widthPx);
        Bitmap bitmap = Bitmap.createBitmap(widthPx, 2000, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        OUTPUT.mkdirs();
        File file = new File(OUTPUT, name + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private static void click(View view) {
        assertTrue("the button is shown", view.getVisibility() == View.VISIBLE);
        assertTrue("the button is enabled", view.isEnabled());
        view.performClick();
        idle();
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
            Thread.sleep(20);
            idle();
        }
        assertTrue("timed out waiting", condition.getAsBoolean());
    }

    private static List<String> texts(View root) {
        List<String> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(View view, List<String> out) {
        if (view instanceof TextView text && view.getVisibility() == View.VISIBLE && text.getText().length() > 0) out.add(text.getText().toString());
        if (view instanceof android.view.ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
    }

    // ---- the list ----

    @Test
    public void everyMissingResourceIsACardWithItsStateAndItsPlace() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        RecyclerView list = find(sheet, R.id.resolver_list);
        layout(sheet.requireView(), 1080, 6000);
        assertEquals(5, list.getAdapter().getItemCount());
        List<String> shown = texts(list);
        assertTrue(shown.toString(), shown.contains("ic_add"));
        assertTrue("the place is shown with its line", shown.contains("layout/main.xml:12"));
        assertTrue("a sure match is a suggestion", shown.contains(sheet.getString(R.string.resolver_status_suggested)));
        assertTrue("a colour needs the person", shown.contains(sheet.getString(R.string.resolver_status_needs)));
        assertTrue("a framework resource can't be fixed here", shown.contains(sheet.getString(R.string.resolver_status_unfixable)));
        assertEquals("5 found · 4 can be fixed here", ((TextView) find(sheet, R.id.resolver_subtitle)).getText().toString());
        assertFalse("nothing is chosen, so there is nothing to review", find(sheet, R.id.resolver_primary).isEnabled());
        render(sheet.requireView(), "list-dark", 1080);
    }

    @Test
    public void listRendersInLightAndOnANarrowPhone() throws Exception {
        ResourceResolverSheet sheet = open(false, new BuiltInValidator());
        File light = render(sheet.requireView(), "list-light", 1080);
        File narrow = render(sheet.requireView(), "list-narrow-320dp", 720);
        assertTrue(light.length() > 5_000);
        assertTrue(narrow.length() > 5_000);
    }

    @Test
    public void tappingAPlaceOpensThatFileAtThatLine() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        RecyclerView list = find(sheet, R.id.resolver_list);
        layout(sheet.requireView(), 1080);
        View location = list.getChildAt(0).findViewById(R.id.item_location);
        location.performClick();
        assertEquals("/p/res/layout/main.xml:12", HostActivity.openedLocation);
    }

    @Test
    public void aTypedValueIsCheckedBeforeItIsChosen() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        RecyclerView list = find(sheet, R.id.resolver_list);
        layout(sheet.requireView(), 1080);
        int colorCard = -1;
        for (int i = 0; i < list.getAdapter().getItemCount(); i++) {
            if (texts(list.getChildAt(i)).contains("brand")) colorCard = i;
        }
        assertTrue(colorCard >= 0);
        View card = list.getChildAt(colorCard);
        android.widget.EditText value = card.findViewById(R.id.item_value);
        Button use = card.findViewById(R.id.item_value_use);
        value.setText("not a colour");
        use.performClick();
        idle();
        MissingResource brand = HostActivity.flow.session().problems().stream().filter(p -> p.key().equals("color/brand")).findFirst().orElseThrow();
        assertEquals("a wrong value is refused", ResolverSession.Status.NEEDS_INPUT, HostActivity.flow.session().status(brand));

        layout(sheet.requireView(), 1080);
        card = list.getChildAt(colorCard);
        value = card.findViewById(R.id.item_value);
        value.setText("#FF5722");
        card.findViewById(R.id.item_value_use).performClick();
        idle();
        assertEquals(ResolverSession.Status.CHOSEN, HostActivity.flow.session().status(brand));
        assertTrue("Review counts it", ((Button) find(sheet, R.id.resolver_primary)).getText().toString().contains("1"));
    }

    // ---- review, apply, undo ----

    @Test
    public void fixAllReviewsThenWritesThenOffersUndoAndBuild() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        click(find(sheet, R.id.resolver_secondary)); // Fix all
        assertEquals(ResolverFlow.Page.REVIEW, HostActivity.flow.page());
        assertEquals(View.VISIBLE, find(sheet, R.id.resolver_review_list).getVisibility());
        assertFalse("nothing is written before the person confirms", new File(res, "drawable/ic_add.xml").exists());
        layout(sheet.requireView(), 1080);
        assertTrue(texts(find(sheet, R.id.resolver_review_list)).toString(), texts(find(sheet, R.id.resolver_review_list)).contains("drawable/ic_add"));
        render(sheet.requireView(), "review-dark", 1080);

        click(find(sheet, R.id.resolver_primary)); // Apply
        assertEquals(ResolverFlow.Page.DONE, HostActivity.flow.page());
        assertTrue(new File(res, "drawable/ic_add.xml").isFile());
        assertEquals("2 resources added", ((TextView) find(sheet, R.id.resolver_status_title)).getText().toString());
        assertEquals(View.VISIBLE, find(sheet, R.id.resolver_secondary).getVisibility());
        assertEquals(sheet.getString(R.string.resolver_undo), ((Button) find(sheet, R.id.resolver_secondary)).getText().toString());
        render(sheet.requireView(), "done-dark", 1080);

        click(find(sheet, R.id.resolver_secondary)); // Undo
        waitFor(() -> HostActivity.flow.page() == ResolverFlow.Page.LIST);
        assertFalse("the file is taken back", new File(res, "drawable/ic_add.xml").exists());
        assertTrue("the person's own file stays", new File(res, "drawable/ic_search_24dp.xml").isFile());
    }

    @Test
    public void afterWritingTheSheetBuildsAgainOnItsOwnUnlessTheChangesAreTakenBack() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        click(find(sheet, R.id.resolver_secondary));
        click(find(sheet, R.id.resolver_primary));
        assertEquals(ResolverFlow.Page.DONE, HostActivity.flow.page());
        assertEquals("the detail counts down from 4 s", "Building again in 4 s\u2026", ((TextView) find(sheet, R.id.resolver_status_detail)).getText().toString());
        assertEquals("not before the countdown ends", null, HostActivity.finishedWithRebuild);
        advance(3_000);
        assertEquals("one second left", null, HostActivity.finishedWithRebuild);
        advance(2_000);
        assertEquals(Boolean.TRUE, HostActivity.finishedWithRebuild);
        assertTrue("the resources that were missing are handed over to compare with the next build", HostActivity.finishedKeys.contains("drawable/ic_add"));
    }

    @Test
    public void undoStopsTheCountdown() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        click(find(sheet, R.id.resolver_secondary));
        click(find(sheet, R.id.resolver_primary));
        click(find(sheet, R.id.resolver_secondary)); // Undo
        advance(10_000);
        assertEquals("no build after the changes were taken back", null, HostActivity.finishedWithRebuild);
    }

    @Test
    public void buildNowDoesNotWaitForTheCountdown() throws Exception {
        ResourceResolverSheet sheet = open(false, new BuiltInValidator());
        click(find(sheet, R.id.resolver_secondary));
        click(find(sheet, R.id.resolver_primary));
        click(find(sheet, R.id.resolver_primary)); // Build now
        assertEquals(Boolean.TRUE, HostActivity.finishedWithRebuild);
    }

    @Test
    public void aRefusedChangeShowsFailureAndLeavesTheProjectAsItWas() throws Exception {
        ResourceResolverSheet sheet = open(true, new Refuse());
        click(find(sheet, R.id.resolver_secondary));
        click(find(sheet, R.id.resolver_primary));
        assertEquals(ResolverFlow.Page.FAILED, HostActivity.flow.page());
        assertEquals(sheet.getString(R.string.resolver_failed_title), ((TextView) find(sheet, R.id.resolver_status_title)).getText().toString());
        assertTrue(((TextView) find(sheet, R.id.resolver_status_detail)).getText().toString().contains("refused"));
        assertFalse(new File(res, "drawable/ic_add.xml").exists());
        render(sheet.requireView(), "failed-dark", 1080);
        click(find(sheet, R.id.resolver_primary)); // Back
        assertEquals(ResolverFlow.Page.REVIEW, HostActivity.flow.page());
    }

    @Test
    public void unticking_everything_disablesApply() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        click(find(sheet, R.id.resolver_secondary));
        int count = HostActivity.flow.review().changes.size();
        for (int i = 0; i < count; i++) HostActivity.flow.setChecked(i, false);
        idle();
        assertFalse(find(sheet, R.id.resolver_primary).isEnabled());
        click(find(sheet, R.id.resolver_secondary)); // Back
        assertEquals(ResolverFlow.Page.LIST, HostActivity.flow.page());
    }

    // ---- the icon picker ----

    @Test
    public void theOfflineLibraryOffersIconsThatCanBeChosenForAMissingDrawable() throws Exception {
        ResourceResolverSheet sheet = open(true, new BuiltInValidator());
        MissingResource problem = HostActivity.flow.session().problems().stream().filter(p -> p.key().equals("drawable/ic_add")).findFirst().orElseThrow();
        IconPickerSheet picker = IconPickerSheet.newInstance(problem.key(), problem.name());
        picker.show(sheet.getChildFragmentManager(), "icons");
        idle();
        assertNotNull(picker.getView());
        RecyclerView grid = picker.requireView().findViewById(R.id.picker_grid);
        waitFor(() -> grid.getAdapter().getItemCount() > 0);
        assertEquals("add and search are in the offline library", 2, grid.getAdapter().getItemCount());
        render(picker.requireView(), "picker-dark", 1080);

        layout(picker.requireView(), 1080);
        grid.getChildAt(0).performClick();
        waitFor(() -> HostActivity.flow.session().status(problem) == ResolverSession.Status.CHOSEN);
        assertEquals(ResolverSession.Status.CHOSEN, HostActivity.flow.session().status(problem));
    }
}
