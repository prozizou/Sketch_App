package pro.sketchware.widgets.registry;

import static pro.sketchware.widgets.registry.WidgetSpec.Base;
import static pro.sketchware.widgets.registry.WidgetSpec.Category;
import static pro.sketchware.widgets.registry.WidgetSpec.Dependency;
import static pro.sketchware.widgets.registry.WidgetSpec.Kind;
import static pro.sketchware.widgets.registry.WidgetSpec.Preview;
import static pro.sketchware.widgets.registry.WidgetSpec.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single list of the components the Visual Editor offers or plans to offer, with what is true about each:
 * what it really is, its class, what it needs to build and how far its integration goes.
 *
 * <p>Only {@link Status#INTEGRATED} entries reach the palette ({@link #integrated()}); an entry moves to
 * {@code INTEGRATED} once its preview, properties, events and generated XML/Java were checked. Everything else
 * stays listed with the reason it isn't offered yet, so nothing is announced that doesn't work.
 *
 * <p>Integrated entries reuse the built-in view type named by {@link WidgetSpec#base()} (the same mechanism as the
 * Widgets Creator): the project file keeps that type plus the real class in {@code convert}, so existing projects,
 * blocks, events and export/import keep working unchanged.
 */
public final class WidgetRegistry {

    /**
     * Classes the palette already offered before the registry existed, by simple name. A registry entry that
     * matches one of them is never added a second time.
     */
    private static final Set<String> BUILT_IN_SIMPLE_NAMES = Set.of(
            "LinearLayout", "RelativeLayout", "ScrollView", "HorizontalScrollView", "TextView", "Button", "EditText",
            "ImageView", "WebView", "ProgressBar", "ListView", "Spinner", "CheckBox", "Switch", "SeekBar",
            "CalendarView", "FloatingActionButton", "AdView", "MapView", "RadioButton", "RatingBar", "VideoView",
            "SearchView", "AutoCompleteTextView", "MultiAutoCompleteTextView", "GridView", "AnalogClock",
            "DatePicker", "TimePicker", "DigitalClock", "TabLayout", "ViewPager", "BottomNavigationView",
            "BadgeView", "PatternLockView", "WaveSideBar", "CardView", "CollapsingToolbarLayout", "TextInputLayout",
            "SwipeRefreshLayout", "RadioGroup", "MaterialButton", "SignInButton", "CircleImageView",
            "LottieAnimationView", "YouTubePlayerView", "OTPView", "CodeView", "RecyclerView");

    private static final List<WidgetSpec> ALL = build();

    private WidgetRegistry() {
    }

    /** @return every entry, in palette order. */
    public static List<WidgetSpec> all() {
        return ALL;
    }

    /** @return the entries the palette offers. */
    public static List<WidgetSpec> integrated() {
        List<WidgetSpec> result = new ArrayList<>();
        for (WidgetSpec spec : ALL) {
            if (spec.status() == Status.INTEGRATED) {
                result.add(spec);
            }
        }
        return result;
    }

    public static WidgetSpec byId(String id) {
        for (WidgetSpec spec : ALL) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }

    public static List<WidgetSpec> byStatus(Status status) {
        List<WidgetSpec> result = new ArrayList<>();
        for (WidgetSpec spec : ALL) {
            if (spec.status() == status) {
                result.add(spec);
            }
        }
        return result;
    }

    /** @return the simple names of the classes the palette offered before the registry. */
    public static Set<String> builtInSimpleNames() {
        return BUILT_IN_SIMPLE_NAMES;
    }

    /**
     * @param existingClasses fully qualified classes already offered by the user's own widgets (spaces and case are
     *                        ignored)
     * @return whether {@code spec} is already offered, so adding it would duplicate a palette entry: either the
     * palette has always had it, or a user widget already names the same class
     */
    public static boolean isAlreadyOffered(WidgetSpec spec, Set<String> existingClasses) {
        if (BUILT_IN_SIMPLE_NAMES.contains(spec.simpleName())) {
            return true;
        }
        String wanted = spec.className().toLowerCase(Locale.ROOT);
        for (String name : existingClasses) {
            if (name.replace(" ", "").toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------

    private static final class Builder {
        private final List<WidgetSpec> specs = new ArrayList<>();

        /** An entry the palette already offered: listed so the audit is complete, never added twice. */
        void existing(String id, String title, Category category, Kind kind, String className, Dependency dependency, int minApi, int phase, String note) {
            specs.add(new WidgetSpec(id, title, category, kind, className, dependency, minApi, phase, Status.EXISTING,
                    Base.NONE, "", "", Preview.NONE, note));
        }

        /** An entry integrated through a built-in view type. */
        void integrated(String id, String title, Category category, Kind kind, String className, Dependency dependency,
                        int minApi, int phase, Base base, String defaultText, String defaultInject, Preview preview, String note) {
            specs.add(new WidgetSpec(id, title, category, kind, className, dependency, minApi, phase, Status.INTEGRATED,
                    base, defaultText, defaultInject, preview, note));
        }

        void planned(String id, String title, Category category, Kind kind, String className, Dependency dependency, int minApi, int phase, String note) {
            specs.add(new WidgetSpec(id, title, category, kind, className, dependency, minApi, phase, Status.PLANNED,
                    Base.NONE, "", "", Preview.NONE, note));
        }

        void blocked(String id, String title, Category category, Kind kind, String className, Dependency dependency, int minApi, int phase, String note) {
            specs.add(new WidgetSpec(id, title, category, kind, className, dependency, minApi, phase, Status.BLOCKED,
                    Base.NONE, "", "", Preview.NONE, note));
        }
    }

    private static List<WidgetSpec> build() {
        Builder b = new Builder();

        // ---------------------------------------------------------------- P1: layouts, navigation, containers
        b.planned("layout.constraint", "ConstraintLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.constraintlayout.widget.ConstraintLayout", Dependency.CONSTRAINT_LAYOUT, 21, 1,
                "Children are placed by constraints (layout_constraint*): needs a constraint property editor and a canvas that draws them. Without them a preview would not match the app.");
        b.planned("layout.frame", "FrameLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "android.widget.FrameLayout", Dependency.PLATFORM, 1, 1,
                "Needs its own view type: children stack and use layout_gravity, which no built-in container preview does.");
        b.planned("layout.coordinator", "CoordinatorLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.coordinatorlayout.widget.CoordinatorLayout", Dependency.COORDINATOR_LAYOUT, 21, 1,
                "Needs its own view type and layout_behavior / layout_anchor properties.");
        b.blocked("layout.motion", "MotionLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.constraintlayout.motion.widget.MotionLayout", Dependency.CONSTRAINT_LAYOUT, 21, 1,
                "Needs a MotionScene XML resource (res/xml) that NWS has no editor for, on top of ConstraintLayout support.");
        b.existing("layout.drawer", "DrawerLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.drawerlayout.widget.DrawerLayout", Dependency.DRAWER_LAYOUT, 21, 1,
                "Already offered as the project's Drawer view (generated _drawer_ views), not as a free widget.");
        b.planned("layout.grid", "GridLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "android.widget.GridLayout", Dependency.PLATFORM, 14, 1,
                "Needs rowCount / columnCount and per-child row / column / span properties plus a grid preview.");
        b.integrated("layout.table", "TableLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "android.widget.TableLayout", Dependency.PLATFORM, 1, 1, Base.LINEAR_VERTICAL, "", "", Preview.EXACT,
                "A vertical LinearLayout subclass. Put TableRow children in it.");
        b.integrated("layout.tablerow", "TableRow", Category.LAYOUTS, Kind.VIEW_GROUP,
                "android.widget.TableRow", Dependency.PLATFORM, 1, 1, Base.LINEAR_HORIZONTAL, "", "", Preview.EXACT,
                "A horizontal LinearLayout subclass. Meant to sit in a TableLayout; the editor doesn't force that.");
        b.integrated("layout.nestedscroll", "NestedScrollView", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.core.widget.NestedScrollView", Dependency.ANDROIDX_CORE, 21, 1, Base.SCROLL_VERTICAL, "", "", Preview.EXACT,
                "Scrolls one child vertically like ScrollView. Needs AndroidX, so the project's AppCompat library has to be enabled.");
        b.existing("layout.hscroll", "HorizontalScrollView", Category.LAYOUTS, Kind.VIEW_GROUP,
                "android.widget.HorizontalScrollView", Dependency.PLATFORM, 1, 1, "Already in the palette.");
        b.planned("layout.flexbox", "FlexboxLayout", Category.LAYOUTS, Kind.VIEW_GROUP,
                "com.google.android.flexbox.FlexboxLayout", Dependency.FLEXBOX, 14, 1,
                "Library not bundled with NWS (flexbox 3.0.0 exists on Google Maven). Needs flexDirection / flexWrap / alignItems properties and a flex preview.");
        b.planned("layout.fragmentcontainer", "FragmentContainerView", Category.LAYOUTS, Kind.VIEW_GROUP,
                "androidx.fragment.app.FragmentContainerView", Dependency.FRAGMENT, 14, 1,
                "Needs android:name set to a Fragment class of the project, which NWS projects don't manage as classes.");
        b.planned("layout.viewstub", "ViewStub", Category.LAYOUTS, Kind.VIEW,
                "android.view.ViewStub", Dependency.PLATFORM, 1, 1,
                "Needs a reference to another layout file (android:layout) and inflatedId; no plain View type to base it on.");

        // ---------------------------------------------------------------- Material and navigation
        b.planned("material.toolbar", "MaterialToolbar", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.appbar.MaterialToolbar", Dependency.MATERIAL, 21, 1,
                "Needs a Toolbar preview with title, navigation icon and menu properties.");
        b.integrated("material.appbar", "AppBarLayout", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.appbar.AppBarLayout", Dependency.MATERIAL, 21, 1, Base.LINEAR_VERTICAL, "", "", Preview.EXACT,
                "A vertical LinearLayout subclass; scrolling behaviour only works inside a CoordinatorLayout.");
        b.planned("material.navrail", "NavigationRailView", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.navigationrail.NavigationRailView", Dependency.MATERIAL, 21, 1,
                "Needs a menu resource (app:menu) and a menu editor.");
        b.planned("material.navview", "NavigationView", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.navigation.NavigationView", Dependency.MATERIAL, 21, 1,
                "Needs a menu resource and a header layout; offered today only through the project Drawer.");
        b.planned("material.viewpager2", "ViewPager2", Category.MATERIAL, Kind.VIEW_GROUP,
                "androidx.viewpager2.widget.ViewPager2", Dependency.VIEWPAGER2, 21, 1,
                "Its events (registerOnPageChangeCallback) and adapter differ from ViewPager, so it can't borrow that type's blocks. NWS bundles 1.1.0-beta02; 1.1.0 stable exists.");
        b.existing("material.fab", "FloatingActionButton", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.floatingactionbutton.FloatingActionButton", Dependency.MATERIAL, 21, 2, "Already in the palette (FAB view type).");
        b.integrated("material.efab", "ExtendedFloatingActionButton", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton", Dependency.MATERIAL, 21, 2,
                Base.MATERIAL_BUTTON, "Extended FAB", "", Preview.APPROXIMATE,
                "A MaterialButton subclass: same text, click and enabled handling. The canvas draws it as a button.");
        b.planned("material.chip", "Chip", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.chip.Chip", Dependency.MATERIAL, 21, 2,
                "Needs its own preview: drawing it as a CheckBox would not look like a chip.");
        b.planned("material.chipgroup", "ChipGroup", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.chip.ChipGroup", Dependency.MATERIAL, 21, 2,
                "Wraps children onto new lines (not a LinearLayout) and has single-selection events: needs its own type.");
        b.planned("material.slider", "Slider", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.slider.Slider", Dependency.MATERIAL, 21, 2,
                "Not a SeekBar: events (addOnChangeListener) and value / valueFrom / valueTo differ, so SeekBar blocks would not work.");
        b.planned("material.rangeslider", "RangeSlider", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.slider.RangeSlider", Dependency.MATERIAL, 21, 2,
                "Two thumbs and a list of values: needs its own events and blocks.");
        b.integrated("material.togglegroup", "MaterialButtonToggleGroup", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.button.MaterialButtonToggleGroup", Dependency.MATERIAL, 21, 2,
                Base.LINEAR_HORIZONTAL, "", "", Preview.EXACT,
                "A horizontal LinearLayout subclass; put MaterialButton children in it.");
        b.integrated("material.switch", "MaterialSwitch", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.materialswitch.MaterialSwitch", Dependency.MATERIAL, 21, 2,
                Base.SWITCH, "MaterialSwitch", "", Preview.APPROXIMATE,
                "A CompoundButton: checked state and onCheckedChange blocks work like Switch.");
        b.integrated("material.card", "MaterialCardView", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.card.MaterialCardView", Dependency.MATERIAL, 21, 2,
                Base.CARD_VIEW, "", "app:cardElevation=\"2dp\"\napp:cardCornerRadius=\"20dp\"", Preview.APPROXIMATE,
                "A CardView subclass: corner radius, elevation and padding work like CardView. Stroke needs an extra attribute.");
        b.integrated("material.shapeableimage", "ShapeableImageView", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.imageview.ShapeableImageView", Dependency.MATERIAL, 21, 2,
                Base.IMAGE_VIEW, "", "", Preview.APPROXIMATE,
                "An ImageView subclass: image, scale type and click work like ImageView. Shape needs app:shapeAppearance.");
        b.planned("material.divider", "MaterialDivider", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.divider.MaterialDivider", Dependency.MATERIAL, 21, 2,
                "A plain View: no built-in plain View type, and a container type would let children be dropped into it.");
        b.planned("material.searchbar", "SearchBar", Category.MATERIAL, Kind.VIEW_GROUP,
                "com.google.android.material.search.SearchBar", Dependency.MATERIAL, 21, 2,
                "Works with SearchView in a CoordinatorLayout and needs its own events.");
        b.planned("material.draghandle", "BottomSheetDragHandleView", Category.MATERIAL, Kind.VIEW,
                "com.google.android.material.bottomsheet.BottomSheetDragHandleView", Dependency.MATERIAL, 21, 2,
                "Only meaningful inside a bottom sheet, which NWS has no widget for yet.");

        // ---------------------------------------------------------------- Forms
        b.integrated("form.textinputedit", "TextInputEditText", Category.FORMS, Kind.VIEW,
                "com.google.android.material.textfield.TextInputEditText", Dependency.MATERIAL, 21, 2,
                Base.EDIT_TEXT, "", "", Preview.APPROXIMATE,
                "An EditText subclass: hint, text, input type and text-changed blocks work like EditText. Meant inside a TextInputLayout.");
        b.integrated("form.materialautocomplete", "MaterialAutoCompleteTextView", Category.FORMS, Kind.VIEW,
                "com.google.android.material.textfield.MaterialAutoCompleteTextView", Dependency.MATERIAL, 21, 2,
                Base.AUTO_COMPLETE_TEXT_VIEW, "", "", Preview.APPROXIMATE,
                "An AutoCompleteTextView subclass, with the same blocks. Meant inside a TextInputLayout.");
        b.planned("form.numberpicker", "NumberPicker", Category.FORMS, Kind.VIEW,
                "android.widget.NumberPicker", Dependency.PLATFORM, 11, 2,
                "A LinearLayout subclass, but a leaf widget: the container type would let children be dropped into it.");
        b.integrated("form.textclock", "TextClock", Category.FORMS, Kind.VIEW,
                "android.widget.TextClock", Dependency.PLATFORM, 17, 2,
                Base.TEXT_VIEW, "", "", Preview.APPROXIMATE,
                "A TextView subclass showing the time. The canvas shows its design-time text; the app shows the time.");
        b.integrated("form.chronometer", "Chronometer", Category.FORMS, Kind.VIEW,
                "android.widget.Chronometer", Dependency.PLATFORM, 1, 2,
                Base.TEXT_VIEW, "", "", Preview.APPROXIMATE,
                "A TextView subclass; start() and stop() are called from Java code blocks.");
        b.existing("form.multiautocomplete", "MultiAutoCompleteTextView", Category.FORMS, Kind.VIEW,
                "android.widget.MultiAutoCompleteTextView", Dependency.PLATFORM, 1, 2, "Already in the palette.");
        b.integrated("form.checkedtext", "CheckedTextView", Category.FORMS, Kind.VIEW,
                "android.widget.CheckedTextView", Dependency.PLATFORM, 1, 2,
                Base.TEXT_VIEW, "", "", Preview.APPROXIMATE,
                "A TextView subclass with a check mark that needs a checkMark drawable to be visible.");
        b.integrated("form.togglebutton", "ToggleButton", Category.FORMS, Kind.VIEW,
                "android.widget.ToggleButton", Dependency.PLATFORM, 1, 2,
                Base.BUTTON, "ToggleButton", "", Preview.APPROXIMATE,
                "A Button subclass with on/off text; checked state works through CompoundButton blocks.");
        b.blocked("form.multiselectspinner", "MultiSelectSpinner", Category.FORMS, Kind.CUSTOM_COMPONENT,
                "", Dependency.PLATFORM, 1, 2,
                "Not an Android or Material class: it names several different third-party libraries. A specific library has to be chosen before it can be integrated.");

        // ---------------------------------------------------------------- P3: media, animation, charts
        b.planned("media.surface", "SurfaceView", Category.MEDIA, Kind.VIEW,
                "android.view.SurfaceView", Dependency.PLATFORM, 1, 3,
                "Drawing happens through a SurfaceHolder callback in Java: needs events and blocks for it.");
        b.planned("media.texture", "TextureView", Category.MEDIA, Kind.VIEW,
                "android.view.TextureView", Dependency.PLATFORM, 14, 3,
                "Needs a SurfaceTextureListener events and blocks.");
        b.planned("media.playerview", "PlayerView (Media3)", Category.MEDIA, Kind.VIEW,
                "androidx.media3.ui.PlayerView", Dependency.MEDIA3_UI, 21, 3,
                "Library not bundled (media3-ui 1.11.1 exists on Google Maven) and needs an ExoPlayer set up in Java.");
        b.existing("media.lottie", "LottieAnimationView", Category.MEDIA, Kind.VIEW,
                "com.airbnb.lottie.LottieAnimationView", new Dependency("com.airbnb.android:lottie:6.6.10", true), 21, 3, "Already in the palette.");
        b.planned("media.photoview", "PhotoView", Category.MEDIA, Kind.VIEW,
                "com.github.chrisbanes.photoview.PhotoView", Dependency.PHOTO_VIEW, 14, 3,
                "Library not bundled (PhotoView 2.3.0 exists on JitPack). An ImageView subclass: integrable through the ImageView type once the library is added.");
        b.planned("media.shimmer", "ShimmerFrameLayout", Category.MEDIA, Kind.VIEW_GROUP,
                "com.facebook.shimmer.ShimmerFrameLayout", Dependency.SHIMMER, 14, 3,
                "Library not bundled (shimmer 0.5.0 exists on Maven Central). A FrameLayout subclass, so it waits for FrameLayout support.");
        b.blocked("media.compose", "ComposeView", Category.MEDIA, Kind.VIEW,
                "androidx.compose.ui.platform.ComposeView", Dependency.COMPOSE_UI, 21, 4,
                "NWS compiles plain Kotlin (KotlinCompiler) but has no Compose compiler plugin or Compose runtime libraries in its on-device toolchain. Nothing is simulated: this needs that toolchain first.");
        b.planned("media.graphview", "GraphView", Category.MEDIA, Kind.VIEW,
                "com.jjoe64.graphview.GraphView", Dependency.GRAPH_VIEW, 14, 3,
                "Library not bundled (graphview 4.2.2 exists on Maven Central). Data is set from Java: needs blocks.");
        b.planned("media.mpchart", "MPAndroidChart", Category.MEDIA, Kind.VIEW,
                "com.github.mikephil.charting.charts.LineChart", Dependency.MP_ANDROID_CHART, 14, 3,
                "Library not bundled (MPAndroidChart v3.1.0 exists on JitPack). Several chart classes, each configured from Java: needs blocks.");

        // ---------------------------------------------------------------- Indicators
        b.planned("indicator.circular", "CircularProgressIndicator", Category.INDICATORS, Kind.VIEW,
                "com.google.android.material.progressindicator.CircularProgressIndicator", Dependency.MATERIAL, 21, 2,
                "The ProgressBar type writes a ProgressBar style attribute that would override the indicator's own Material style: needs a type without it.");
        b.planned("indicator.linear", "LinearProgressIndicator", Category.INDICATORS, Kind.VIEW,
                "com.google.android.material.progressindicator.LinearProgressIndicator", Dependency.MATERIAL, 21, 2,
                "Same ProgressBar style attribute problem as CircularProgressIndicator.");
        b.existing("indicator.materialbutton", "MaterialButton", Category.INDICATORS, Kind.VIEW,
                "com.google.android.material.button.MaterialButton", Dependency.MATERIAL, 21, 2, "Already in the palette.");
        b.planned("indicator.slidingpane", "SlidingPaneLayout", Category.INDICATORS, Kind.VIEW_GROUP,
                "androidx.slidingpanelayout.widget.SlidingPaneLayout", Dependency.SLIDING_PANE_LAYOUT, 21, 1,
                "Two panes with layout_weight and a slide gesture: needs its own container type and preview.");
        b.planned("indicator.viewflipper", "ViewFlipper", Category.INDICATORS, Kind.VIEW_GROUP,
                "android.widget.ViewFlipper", Dependency.PLATFORM, 1, 3,
                "A FrameLayout subclass showing one child at a time: waits for FrameLayout support.");
        b.planned("indicator.viewanimator", "ViewAnimator", Category.INDICATORS, Kind.VIEW_GROUP,
                "android.widget.ViewAnimator", Dependency.PLATFORM, 1, 3,
                "A FrameLayout subclass showing one child at a time: waits for FrameLayout support.");

        // ---------------------------------------------------------------- P4: dialogs, effects, APIs
        b.planned("action.badge", "BadgeDrawable", Category.ACTIONS, Kind.EFFECT,
                "com.google.android.material.badge.BadgeDrawable", Dependency.MATERIAL, 21, 4,
                "Not a widget: attaches to an anchor view from Java (BadgeUtils). Needs logic blocks, not a palette tile.");
        b.planned("action.circularreveal", "CircularReveal", Category.ACTIONS, Kind.EFFECT,
                "android.view.ViewAnimationUtils", Dependency.PLATFORM, 21, 4,
                "An animation effect started from Java: needs logic blocks, not a palette tile.");
        b.planned("action.datepicker", "MaterialDatePicker", Category.ACTIONS, Kind.DIALOG,
                "com.google.android.material.datepicker.MaterialDatePicker", Dependency.MATERIAL, 21, 4,
                "A dialog needing a FragmentActivity and a result listener: needs a component and blocks.");
        b.planned("action.timepicker", "MaterialTimePicker", Category.ACTIONS, Kind.DIALOG,
                "com.google.android.material.timepicker.MaterialTimePicker", Dependency.MATERIAL, 21, 4,
                "A dialog needing a FragmentActivity and a result listener: needs a component and blocks.");
        b.planned("action.bottomsheet", "BottomSheetDialog", Category.ACTIONS, Kind.DIALOG,
                "com.google.android.material.bottomsheet.BottomSheetDialog", Dependency.MATERIAL, 21, 4,
                "A dialog that shows another layout: needs a component taking a layout and blocks.");
        b.existing("action.snackbar", "Snackbar", Category.ACTIONS, Kind.API,
                "com.google.android.material.snackbar.Snackbar", Dependency.MATERIAL, 21, 4,
                "Already available as the showSnackbar logic block.");
        b.planned("action.popupmenu", "PopupMenu", Category.ACTIONS, Kind.API,
                "android.widget.PopupMenu", Dependency.PLATFORM, 11, 4,
                "Needs a menu built from blocks (or a menu resource) and an item-click event.");
        b.planned("action.alertdialog", "MaterialAlertDialogBuilder", Category.ACTIONS, Kind.DIALOG,
                "com.google.android.material.dialog.MaterialAlertDialogBuilder", Dependency.MATERIAL, 21, 4,
                "The Dialog component builds a plain AlertDialog; a Material variant needs a component option.");
        b.blocked("action.navigation", "Navigation Component", Category.ACTIONS, Kind.API,
                "androidx.navigation.fragment.NavHostFragment", Dependency.PLATFORM, 21, 4,
                "Needs the androidx.navigation libraries (not bundled), a navigation graph resource and Fragments, none of which NWS projects manage.");
        b.planned("action.sidesheet", "MaterialSideSheet", Category.ACTIONS, Kind.DIALOG,
                "com.google.android.material.sidesheet.SideSheetDialog", Dependency.MATERIAL, 21, 4,
                "A dialog that shows another layout: needs a component taking a layout and blocks.");
        b.planned("action.itemtouchhelper", "RecyclerView ItemTouchHelper", Category.ACTIONS, Kind.API,
                "androidx.recyclerview.widget.ItemTouchHelper", Dependency.PLATFORM, 21, 4,
                "Attaches to a RecyclerView from Java with a callback: needs logic blocks.");

        return Collections.unmodifiableList(b.specs);
    }
}
