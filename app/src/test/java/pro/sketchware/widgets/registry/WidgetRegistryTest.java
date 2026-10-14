package pro.sketchware.widgets.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.widgets.registry.WidgetSpec.Base;
import pro.sketchware.widgets.registry.WidgetSpec.Kind;
import pro.sketchware.widgets.registry.WidgetSpec.Preview;
import pro.sketchware.widgets.registry.WidgetSpec.Status;

public class WidgetRegistryTest {

    /** Every component asked for in the enrichment request, by title. */
    private static final List<String> REQUESTED = List.of(
            "ConstraintLayout", "FrameLayout", "CoordinatorLayout", "MotionLayout", "DrawerLayout", "GridLayout",
            "TableLayout", "TableRow", "NestedScrollView", "HorizontalScrollView", "FlexboxLayout",
            "FragmentContainerView", "ViewStub",
            "MaterialToolbar", "AppBarLayout", "NavigationRailView", "NavigationView", "ViewPager2",
            "FloatingActionButton", "ExtendedFloatingActionButton", "Chip", "ChipGroup", "Slider", "RangeSlider",
            "MaterialButtonToggleGroup", "MaterialSwitch", "MaterialCardView", "ShapeableImageView",
            "MaterialDivider", "SearchBar", "BottomSheetDragHandleView",
            "TextInputEditText", "MaterialAutoCompleteTextView", "NumberPicker", "TextClock", "Chronometer",
            "MultiAutoCompleteTextView", "CheckedTextView", "ToggleButton", "MultiSelectSpinner",
            "SurfaceView", "TextureView", "PlayerView (Media3)", "LottieAnimationView", "PhotoView",
            "ShimmerFrameLayout", "ComposeView", "GraphView", "MPAndroidChart",
            "CircularProgressIndicator", "LinearProgressIndicator", "MaterialButton", "SlidingPaneLayout",
            "ViewFlipper", "ViewAnimator",
            "BadgeDrawable", "CircularReveal", "MaterialDatePicker", "MaterialTimePicker", "BottomSheetDialog",
            "Snackbar", "PopupMenu", "MaterialAlertDialogBuilder", "Navigation Component", "MaterialSideSheet",
            "RecyclerView ItemTouchHelper");

    @Test
    public void everyRequestedComponentIsListedExactlyOnce() {
        Set<String> titles = new HashSet<>();
        for (WidgetSpec spec : WidgetRegistry.all()) {
            assertTrue("duplicate title " + spec.title(), titles.add(spec.title()));
        }
        for (String requested : REQUESTED) {
            assertTrue("missing " + requested, titles.contains(requested));
        }
        assertEquals(REQUESTED.size(), WidgetRegistry.all().size());
    }

    @Test
    public void idsAreUniqueAndStable() {
        Set<String> ids = new HashSet<>();
        for (WidgetSpec spec : WidgetRegistry.all()) {
            assertTrue("duplicate id " + spec.id(), ids.add(spec.id()));
            assertTrue("id format " + spec.id(), spec.id().matches("[a-z]+\\.[a-z0-9]+"));
        }
        // Ids that were shipped are part of the contract: renaming one would orphan saved data.
        assertNotNull(WidgetRegistry.byId("layout.table"));
        assertNotNull(WidgetRegistry.byId("material.switch"));
        assertNotNull(WidgetRegistry.byId("form.textclock"));
    }

    @Test
    public void classNamesAreFullyQualifiedExceptForTheOneNamingNoClass() {
        for (WidgetSpec spec : WidgetRegistry.all()) {
            if (spec.kind() == Kind.CUSTOM_COMPONENT) {
                assertEquals(Status.BLOCKED, spec.status());
                continue;
            }
            assertTrue("not qualified: " + spec.className(), spec.className().contains("."));
            assertTrue(spec.minApi() >= 1);
            assertTrue(spec.phase() >= 1 && spec.phase() <= 4);
        }
    }

    @Test
    public void everyEntryExplainsItsStateExceptThoseAlreadyWorking() {
        for (WidgetSpec spec : WidgetRegistry.all()) {
            assertFalse("empty note: " + spec.id(), spec.note().isBlank());
        }
    }

    @Test
    public void integratedEntriesAreCompleteAndSafe() {
        List<WidgetSpec> integrated = WidgetRegistry.integrated();
        assertFalse(integrated.isEmpty());
        for (WidgetSpec spec : integrated) {
            assertTrue(spec.id() + " must be visual", spec.isVisual());
            assertTrue(spec.id() + " needs a base type", spec.base() != Base.NONE);
            assertTrue(spec.id() + " needs a preview level", spec.preview() != Preview.NONE);
            assertTrue(spec.id() + " must build with what NWS bundles", spec.dependency().bundled());
            assertTrue(spec.id() + " names a class", spec.className().contains("."));
        }
    }

    @Test
    public void containersUseContainerTypesAndLeavesDoNot() {
        for (WidgetSpec spec : WidgetRegistry.integrated()) {
            boolean containerBase = spec.base() == Base.LINEAR_VERTICAL || spec.base() == Base.LINEAR_HORIZONTAL
                    || spec.base() == Base.SCROLL_VERTICAL || spec.base() == Base.CARD_VIEW;
            assertEquals(spec.id() + " container/leaf mismatch", spec.kind() == Kind.VIEW_GROUP, containerBase);
        }
    }

    @Test
    public void nothingNotIntegratedClaimsABaseType() {
        for (WidgetSpec spec : WidgetRegistry.all()) {
            if (spec.status() != Status.INTEGRATED) {
                assertEquals(spec.id(), Base.NONE, spec.base());
                assertEquals(spec.id(), Preview.NONE, spec.preview());
            }
        }
    }

    @Test
    public void nothingIsOfferedTwice() {
        for (WidgetSpec spec : WidgetRegistry.integrated()) {
            assertFalse(spec.id() + " duplicates a palette entry", WidgetRegistry.isAlreadyOffered(spec, Set.of()));
        }
        // Entries marked EXISTING are exactly the ones the palette had before.
        for (WidgetSpec spec : WidgetRegistry.byStatus(Status.EXISTING)) {
            boolean builtInClass = WidgetRegistry.builtInSimpleNames().contains(spec.simpleName());
            boolean otherwise = spec.id().equals("layout.drawer") || spec.id().equals("action.snackbar");
            assertTrue(spec.id() + " is marked existing", builtInClass || otherwise);
        }
    }

    @Test
    public void aUserWidgetNamingTheSameClassCountsAsAlreadyOffered() {
        WidgetSpec table = WidgetRegistry.byId("layout.table");
        assertFalse(WidgetRegistry.isAlreadyOffered(table, Set.of("android.widget.TableRow")));
        assertTrue(WidgetRegistry.isAlreadyOffered(table, Set.of(" android.widget.TableLayout ")));
        assertTrue(WidgetRegistry.isAlreadyOffered(table, Set.of("ANDROID.WIDGET.TABLELAYOUT")));
    }

    @Test
    public void nonVisualElementsAreNeverOfferedAsWidgets() {
        for (WidgetSpec spec : WidgetRegistry.all()) {
            if (spec.kind() == Kind.DIALOG || spec.kind() == Kind.EFFECT || spec.kind() == Kind.API) {
                assertFalse(spec.id(), spec.isVisual());
                assertTrue(spec.id() + " must not be integrated as a widget", spec.status() != Status.INTEGRATED);
            }
        }
    }

    @Test
    public void dependenciesNotBundledAreNeverIntegrated() {
        for (WidgetSpec spec : WidgetRegistry.all()) {
            if (!spec.dependency().bundled()) {
                assertTrue(spec.id(), spec.status() == Status.PLANNED || spec.status() == Status.BLOCKED);
                assertFalse(spec.id(), spec.dependency().coordinates().isEmpty());
            }
        }
    }

    @Test
    public void composeIsBlockedNotSimulated() {
        WidgetSpec compose = WidgetRegistry.byId("media.compose");
        assertEquals(Status.BLOCKED, compose.status());
        assertEquals(Base.NONE, compose.base());
    }
}
