# Visual Editor widget audit

Audit of how NWS defines, shows, saves and builds widgets, and which of the requested components are in the
palette. The tables below are generated from `pro.sketchware.widgets.registry.WidgetRegistry`, the single list the
palette is built from; `WidgetRegistryTest` keeps it consistent.

## How a widget works in NWS

A widget is a `ViewBean` with a numeric **view type** (`ViewBean`, `ViewBeans`: 0-48 today), plus two strings:

- `convert`: the class written to the layout XML (`Ox.writeWidget`) and used as the Java field type
  (`Jx.getViewDeclarationAndAddImports`, which also adds the import for a fully qualified name);
- `inject`: extra XML attributes.

Preview (`ViewPane` and the `Item*` classes), property panels, events, view-type-specific logic blocks
(`ExtraPaletteBlock.isWidgetUsed`) and placement rules all key on the **view type**, never on `convert`. A project
file stores both, so a widget saved by one version reopens in another.

Consequence: a component can be offered **correctly** when its class is a subclass of the class behind an existing
view type (events and blocks stay valid, preview has the same bounds). Anything else (a different container model,
different events, a resource it must point at) needs new editor support, not a palette entry. Offering it through
the closest type would produce a preview or an app that doesn't match.

Existing extension points reused here:

- Widgets Creator (`WidgetsCreatorManager`, `widgets.json`): user-defined widgets built the same way. Registry
  components are skipped when a user widget already names the same class.
- `AndroidxOrMaterialView`: marks a tile whose widget needs the project's AppCompat/Material library; dropping it
  without that library shows the existing "set up the library first" message.
- `PaletteWidget` sections, search and the new per-section counters.

NWS builds apps on the device without Gradle. "Dependencies" are the libraries bundled in `BuiltInLibraries` (here:
Material 1.13.0, constraintlayout 2.2.1, coordinatorlayout 1.3.0, drawerlayout 1.1.1, slidingpanelayout 1.0.0,
fragment 1.6.1, viewpager2 1.1.0-beta02, recyclerview 1.4.0, lottie 6.6.10) plus libraries the user adds through the
library manager. Nothing is fetched from Gradle at build time.

Dependency versions of the libraries NWS does not bundle were checked against their repositories on 2026-10-14:
flexbox 3.0.0 (Google Maven), media3-ui 1.11.1 (Google Maven), shimmer 0.5.0 and graphview 4.2.2 (Maven Central),
PhotoView 2.3.0 and MPAndroidChart v3.1.0 (JitPack). Availability was checked; compatibility with NWS's compiler
was not (it can't be without building).

## Status of the requested components

### INTEGRATED (15)

| Component | Kind | Class | Needs | API | Phase | Preview | Note |
|---|---|---|---|---|---|---|---|
| TableLayout | VIEW_GROUP | `android.widget.TableLayout` | platform | 1 | P1 | exact | A vertical LinearLayout subclass. Put TableRow children in it. |
| TableRow | VIEW_GROUP | `android.widget.TableRow` | platform | 1 | P1 | exact | A horizontal LinearLayout subclass. Meant to sit in a TableLayout; the editor doesn't force that. |
| NestedScrollView | VIEW_GROUP | `androidx.core.widget.NestedScrollView` | androidx.core:core:1.17.0 (bundled) | 21 | P1 | exact | Scrolls one child vertically like ScrollView. Needs AndroidX, so the project's AppCompat library has to be enabled. |
| AppBarLayout | VIEW_GROUP | `com.google.android.material.appbar.AppBarLayout` | com.google.android.material:material:1.13.0 (bundled) | 21 | P1 | exact | A vertical LinearLayout subclass; scrolling behaviour only works inside a CoordinatorLayout. |
| ExtendedFloatingActionButton | VIEW | `com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | A MaterialButton subclass: same text, click and enabled handling. The canvas draws it as a button. |
| MaterialButtonToggleGroup | VIEW_GROUP | `com.google.android.material.button.MaterialButtonToggleGroup` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | exact | A horizontal LinearLayout subclass; put MaterialButton children in it. |
| MaterialSwitch | VIEW | `com.google.android.material.materialswitch.MaterialSwitch` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | A CompoundButton: checked state and onCheckedChange blocks work like Switch. |
| MaterialCardView | VIEW_GROUP | `com.google.android.material.card.MaterialCardView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | A CardView subclass: corner radius, elevation and padding work like CardView. Stroke needs an extra attribute. |
| ShapeableImageView | VIEW | `com.google.android.material.imageview.ShapeableImageView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | An ImageView subclass: image, scale type and click work like ImageView. Shape needs app:shapeAppearance. |
| TextInputEditText | VIEW | `com.google.android.material.textfield.TextInputEditText` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | An EditText subclass: hint, text, input type and text-changed blocks work like EditText. Meant inside a TextInputLayout. |
| MaterialAutoCompleteTextView | VIEW | `com.google.android.material.textfield.MaterialAutoCompleteTextView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | approximate | An AutoCompleteTextView subclass, with the same blocks. Meant inside a TextInputLayout. |
| TextClock | VIEW | `android.widget.TextClock` | platform | 17 | P2 | approximate | A TextView subclass showing the time. The canvas shows its design-time text; the app shows the time. |
| Chronometer | VIEW | `android.widget.Chronometer` | platform | 1 | P2 | approximate | A TextView subclass; start() and stop() are called from Java code blocks. |
| CheckedTextView | VIEW | `android.widget.CheckedTextView` | platform | 1 | P2 | approximate | A TextView subclass with a check mark that needs a checkMark drawable to be visible. |
| ToggleButton | VIEW | `android.widget.ToggleButton` | platform | 1 | P2 | approximate | A Button subclass with on/off text; checked state works through CompoundButton blocks. |

### EXISTING (7)

| Component | Kind | Class | Needs | API | Phase | Preview | Note |
|---|---|---|---|---|---|---|---|
| DrawerLayout | VIEW_GROUP | `androidx.drawerlayout.widget.DrawerLayout` | androidx.drawerlayout:drawerlayout:1.1.1 (bundled) | 21 | P1 | - | Already offered as the project's Drawer view (generated _drawer_ views), not as a free widget. |
| HorizontalScrollView | VIEW_GROUP | `android.widget.HorizontalScrollView` | platform | 1 | P1 | - | Already in the palette. |
| FloatingActionButton | VIEW | `com.google.android.material.floatingactionbutton.FloatingActionButton` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Already in the palette (FAB view type). |
| MultiAutoCompleteTextView | VIEW | `android.widget.MultiAutoCompleteTextView` | platform | 1 | P2 | - | Already in the palette. |
| LottieAnimationView | VIEW | `com.airbnb.lottie.LottieAnimationView` | com.airbnb.android:lottie:6.6.10 (bundled) | 21 | P3 | - | Already in the palette. |
| MaterialButton | VIEW | `com.google.android.material.button.MaterialButton` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Already in the palette. |
| Snackbar | API | `com.google.android.material.snackbar.Snackbar` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | Already available as the showSnackbar logic block. |

### PLANNED (40)

| Component | Kind | Class | Needs | API | Phase | Preview | Note |
|---|---|---|---|---|---|---|---|
| ConstraintLayout | VIEW_GROUP | `androidx.constraintlayout.widget.ConstraintLayout` | androidx.constraintlayout:constraintlayout:2.2.1 (bundled) | 21 | P1 | - | Children are placed by constraints (layout_constraint*): needs a constraint property editor and a canvas that draws them. Without them a preview would not match the app. |
| FrameLayout | VIEW_GROUP | `android.widget.FrameLayout` | platform | 1 | P1 | - | Needs its own view type: children stack and use layout_gravity, which no built-in container preview does. |
| CoordinatorLayout | VIEW_GROUP | `androidx.coordinatorlayout.widget.CoordinatorLayout` | androidx.coordinatorlayout:coordinatorlayout:1.3.0 (bundled) | 21 | P1 | - | Needs its own view type and layout_behavior / layout_anchor properties. |
| GridLayout | VIEW_GROUP | `android.widget.GridLayout` | platform | 14 | P1 | - | Needs rowCount / columnCount and per-child row / column / span properties plus a grid preview. |
| FlexboxLayout | VIEW_GROUP | `com.google.android.flexbox.FlexboxLayout` | com.google.android.flexbox:flexbox:3.0.0 (**not bundled**) | 14 | P1 | - | Library not bundled with NWS (flexbox 3.0.0 exists on Google Maven). Needs flexDirection / flexWrap / alignItems properties and a flex preview. |
| FragmentContainerView | VIEW_GROUP | `androidx.fragment.app.FragmentContainerView` | androidx.fragment:fragment:1.6.1 (bundled) | 14 | P1 | - | Needs android:name set to a Fragment class of the project, which NWS projects don't manage as classes. |
| ViewStub | VIEW | `android.view.ViewStub` | platform | 1 | P1 | - | Needs a reference to another layout file (android:layout) and inflatedId; no plain View type to base it on. |
| MaterialToolbar | VIEW_GROUP | `com.google.android.material.appbar.MaterialToolbar` | com.google.android.material:material:1.13.0 (bundled) | 21 | P1 | - | Needs a Toolbar preview with title, navigation icon and menu properties. |
| NavigationRailView | VIEW_GROUP | `com.google.android.material.navigationrail.NavigationRailView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P1 | - | Needs a menu resource (app:menu) and a menu editor. |
| NavigationView | VIEW_GROUP | `com.google.android.material.navigation.NavigationView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P1 | - | Needs a menu resource and a header layout; offered today only through the project Drawer. |
| ViewPager2 | VIEW_GROUP | `androidx.viewpager2.widget.ViewPager2` | androidx.viewpager2:viewpager2:1.1.0-beta02 (bundled) | 21 | P1 | - | Its events (registerOnPageChangeCallback) and adapter differ from ViewPager, so it can't borrow that type's blocks. NWS bundles 1.1.0-beta02; 1.1.0 stable exists. |
| Chip | VIEW | `com.google.android.material.chip.Chip` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Needs its own preview: drawing it as a CheckBox would not look like a chip. |
| ChipGroup | VIEW_GROUP | `com.google.android.material.chip.ChipGroup` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Wraps children onto new lines (not a LinearLayout) and has single-selection events: needs its own type. |
| Slider | VIEW | `com.google.android.material.slider.Slider` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Not a SeekBar: events (addOnChangeListener) and value / valueFrom / valueTo differ, so SeekBar blocks would not work. |
| RangeSlider | VIEW | `com.google.android.material.slider.RangeSlider` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Two thumbs and a list of values: needs its own events and blocks. |
| MaterialDivider | VIEW | `com.google.android.material.divider.MaterialDivider` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | A plain View: no built-in plain View type, and a container type would let children be dropped into it. |
| SearchBar | VIEW_GROUP | `com.google.android.material.search.SearchBar` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Works with SearchView in a CoordinatorLayout and needs its own events. |
| BottomSheetDragHandleView | VIEW | `com.google.android.material.bottomsheet.BottomSheetDragHandleView` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Only meaningful inside a bottom sheet, which NWS has no widget for yet. |
| NumberPicker | VIEW | `android.widget.NumberPicker` | platform | 11 | P2 | - | A LinearLayout subclass, but a leaf widget: the container type would let children be dropped into it. |
| SurfaceView | VIEW | `android.view.SurfaceView` | platform | 1 | P3 | - | Drawing happens through a SurfaceHolder callback in Java: needs events and blocks for it. |
| TextureView | VIEW | `android.view.TextureView` | platform | 14 | P3 | - | Needs a SurfaceTextureListener events and blocks. |
| PlayerView (Media3) | VIEW | `androidx.media3.ui.PlayerView` | androidx.media3:media3-ui:1.11.1 (**not bundled**) | 21 | P3 | - | Library not bundled (media3-ui 1.11.1 exists on Google Maven) and needs an ExoPlayer set up in Java. |
| PhotoView | VIEW | `com.github.chrisbanes.photoview.PhotoView` | com.github.chrisbanes:PhotoView:2.3.0 (**not bundled**) | 14 | P3 | - | Library not bundled (PhotoView 2.3.0 exists on JitPack). An ImageView subclass: integrable through the ImageView type once the library is added. |
| ShimmerFrameLayout | VIEW_GROUP | `com.facebook.shimmer.ShimmerFrameLayout` | com.facebook.shimmer:shimmer:0.5.0 (**not bundled**) | 14 | P3 | - | Library not bundled (shimmer 0.5.0 exists on Maven Central). A FrameLayout subclass, so it waits for FrameLayout support. |
| GraphView | VIEW | `com.jjoe64.graphview.GraphView` | com.jjoe64:graphview:4.2.2 (**not bundled**) | 14 | P3 | - | Library not bundled (graphview 4.2.2 exists on Maven Central). Data is set from Java: needs blocks. |
| MPAndroidChart | VIEW | `com.github.mikephil.charting.charts.LineChart` | com.github.PhilJay:MPAndroidChart:v3.1.0 (**not bundled**) | 14 | P3 | - | Library not bundled (MPAndroidChart v3.1.0 exists on JitPack). Several chart classes, each configured from Java: needs blocks. |
| CircularProgressIndicator | VIEW | `com.google.android.material.progressindicator.CircularProgressIndicator` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | The ProgressBar type writes a ProgressBar style attribute that would override the indicator's own Material style: needs a type without it. |
| LinearProgressIndicator | VIEW | `com.google.android.material.progressindicator.LinearProgressIndicator` | com.google.android.material:material:1.13.0 (bundled) | 21 | P2 | - | Same ProgressBar style attribute problem as CircularProgressIndicator. |
| SlidingPaneLayout | VIEW_GROUP | `androidx.slidingpanelayout.widget.SlidingPaneLayout` | androidx.slidingpanelayout:slidingpanelayout:1.0.0 (bundled) | 21 | P1 | - | Two panes with layout_weight and a slide gesture: needs its own container type and preview. |
| ViewFlipper | VIEW_GROUP | `android.widget.ViewFlipper` | platform | 1 | P3 | - | A FrameLayout subclass showing one child at a time: waits for FrameLayout support. |
| ViewAnimator | VIEW_GROUP | `android.widget.ViewAnimator` | platform | 1 | P3 | - | A FrameLayout subclass showing one child at a time: waits for FrameLayout support. |
| BadgeDrawable | EFFECT | `com.google.android.material.badge.BadgeDrawable` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | Not a widget: attaches to an anchor view from Java (BadgeUtils). Needs logic blocks, not a palette tile. |
| CircularReveal | EFFECT | `android.view.ViewAnimationUtils` | platform | 21 | P4 | - | An animation effect started from Java: needs logic blocks, not a palette tile. |
| MaterialDatePicker | DIALOG | `com.google.android.material.datepicker.MaterialDatePicker` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | A dialog needing a FragmentActivity and a result listener: needs a component and blocks. |
| MaterialTimePicker | DIALOG | `com.google.android.material.timepicker.MaterialTimePicker` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | A dialog needing a FragmentActivity and a result listener: needs a component and blocks. |
| BottomSheetDialog | DIALOG | `com.google.android.material.bottomsheet.BottomSheetDialog` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | A dialog that shows another layout: needs a component taking a layout and blocks. |
| PopupMenu | API | `android.widget.PopupMenu` | platform | 11 | P4 | - | Needs a menu built from blocks (or a menu resource) and an item-click event. |
| MaterialAlertDialogBuilder | DIALOG | `com.google.android.material.dialog.MaterialAlertDialogBuilder` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | The Dialog component builds a plain AlertDialog; a Material variant needs a component option. |
| MaterialSideSheet | DIALOG | `com.google.android.material.sidesheet.SideSheetDialog` | com.google.android.material:material:1.13.0 (bundled) | 21 | P4 | - | A dialog that shows another layout: needs a component taking a layout and blocks. |
| RecyclerView ItemTouchHelper | API | `androidx.recyclerview.widget.ItemTouchHelper` | platform | 21 | P4 | - | Attaches to a RecyclerView from Java with a callback: needs logic blocks. |

### BLOCKED (4)

| Component | Kind | Class | Needs | API | Phase | Preview | Note |
|---|---|---|---|---|---|---|---|
| MotionLayout | VIEW_GROUP | `androidx.constraintlayout.motion.widget.MotionLayout` | androidx.constraintlayout:constraintlayout:2.2.1 (bundled) | 21 | P1 | - | Needs a MotionScene XML resource (res/xml) that NWS has no editor for, on top of ConstraintLayout support. |
| MultiSelectSpinner | CUSTOM_COMPONENT | `-` | platform | 1 | P2 | - | Not an Android or Material class: it names several different third-party libraries. A specific library has to be chosen before it can be integrated. |
| ComposeView | VIEW | `androidx.compose.ui.platform.ComposeView` | androidx.compose.ui:ui (**not bundled**) | 21 | P4 | - | NWS compiles plain Kotlin (KotlinCompiler) but has no Compose compiler plugin or Compose runtime libraries in its on-device toolchain. Nothing is simulated: this needs that toolchain first. |
| Navigation Component | API | `androidx.navigation.fragment.NavHostFragment` | platform | 21 | P4 | - | Needs the androidx.navigation libraries (not bundled), a navigation graph resource and Fragments, none of which NWS projects manage. |

## Order of work

- **P1** layouts, navigation, containers: TableLayout, TableRow, NestedScrollView, AppBarLayout done; ConstraintLayout,
  FrameLayout, CoordinatorLayout, GridLayout, FlexboxLayout, FragmentContainerView, ViewStub, MaterialToolbar,
  NavigationRailView, NavigationView, ViewPager2, SlidingPaneLayout need new view types or panels.
- **P2** Material, forms, interactions: the integrated ones above; Chip, ChipGroup, Slider, RangeSlider, MaterialDivider,
  SearchBar, NumberPicker, progress indicators need their own preview or events.
- **P3** media, animation, charts: all need either a library the user adds first or Java-side blocks.
- **P4** dialogs, effects, APIs, Compose: none of these are widgets; they need logic blocks or components. Compose
  needs a toolchain NWS does not have.

## What is not covered yet

Interface work asked for the left panel (2-column grid, favourites, drag handles on every tile, independent scrolling
rules) is not done: the palette is still a one-column list. Done: collapsible sections with live counters, search.
Properties are the base type's panel plus the existing custom-attribute editor; per-class property panels (for
example `app:shapeAppearance`) are not written.
