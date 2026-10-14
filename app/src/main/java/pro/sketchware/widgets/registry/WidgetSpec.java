package pro.sketchware.widgets.registry;

/**
 * Describes one Android component the Visual Editor offers, or could offer: what it really is, which class it
 * generates, what it needs to build, and how far its integration goes.
 *
 * <p>Pure data, no Android dependency, so it can be checked by plain unit tests.
 *
 * @param id             stable identifier, never reused or renamed once shipped
 * @param title          label in the palette
 * @param category       palette section
 * @param kind           what the component really is
 * @param className      fully qualified class (or builder/API class for non-visual elements)
 * @param dependency     what the built app needs for {@code className}
 * @param minApi         lowest Android API level the class works on
 * @param phase          development phase, 1 to 4
 * @param status         how far the integration goes today
 * @param base           built-in view type the editor uses for preview, properties and events; {@link Base#NONE}
 *                       when the component has none
 * @param defaultText    text shown by a new instance of a text-carrying widget, "" otherwise
 * @param defaultInject  XML attributes added to a new instance, in the editor's inject format
 * @param preview        how close the canvas preview is to the real component
 * @param note           why it is not integrated yet, or what to know when it is
 */
public record WidgetSpec(
        String id,
        String title,
        Category category,
        Kind kind,
        String className,
        Dependency dependency,
        int minApi,
        int phase,
        Status status,
        Base base,
        String defaultText,
        String defaultInject,
        Preview preview,
        String note) {

    /** What the component really is. Non-visual elements are never offered as fake widgets. */
    public enum Kind {
        VIEW,
        VIEW_GROUP,
        DIALOG,
        EFFECT,
        API,
        CUSTOM_COMPONENT
    }

    public enum Category {
        LAYOUTS("Layouts"),
        MATERIAL("Material"),
        FORMS("Forms"),
        MEDIA("Media & animation"),
        INDICATORS("Indicators"),
        ACTIONS("Dialogs, effects & APIs");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** How far the integration goes. Only {@link #INTEGRATED} entries appear in the palette. */
    public enum Status {
        /** Already offered by the editor before the registry: never added a second time. */
        EXISTING,
        /** Offered in the palette, with preview, properties, events and generated XML/Java. */
        INTEGRATED,
        /** Needs new editor support (a view type, a preview or a panel) that is not written yet. */
        PLANNED,
        /** Can't be offered as asked: see the note. */
        BLOCKED
    }

    /** How close the canvas preview is to the real component. */
    public enum Preview {
        /** Same look and layout behaviour as the preview of {@link #base}. */
        EXACT,
        /** The preview of {@link #base} stands in: same bounds and attributes, not the same drawing. */
        APPROXIMATE,
        NONE
    }

    /**
     * Built-in view type used for preview, properties, events and placement. The component's class must be a
     * subclass of the class behind it (or behave as one), so events and blocks stay valid.
     */
    public enum Base {
        NONE,
        LINEAR_VERTICAL,
        LINEAR_HORIZONTAL,
        SCROLL_VERTICAL,
        BUTTON,
        MATERIAL_BUTTON,
        TEXT_VIEW,
        EDIT_TEXT,
        SWITCH,
        IMAGE_VIEW,
        CARD_VIEW,
        AUTO_COMPLETE_TEXT_VIEW
    }

    /**
     * @param coordinates Maven coordinates {@code group:artifact:version}, or "" for the platform
     * @param bundled     whether NWS already ships it for building apps. When false the project needs the library
     *                    added through the library manager first.
     */
    public record Dependency(String coordinates, boolean bundled) {
        public static final Dependency PLATFORM = new Dependency("", true);
        public static final Dependency ANDROIDX_CORE = new Dependency("androidx.core:core:1.17.0", true);
        public static final Dependency MATERIAL = new Dependency("com.google.android.material:material:1.13.0", true);
        public static final Dependency CONSTRAINT_LAYOUT = new Dependency("androidx.constraintlayout:constraintlayout:2.2.1", true);
        public static final Dependency COORDINATOR_LAYOUT = new Dependency("androidx.coordinatorlayout:coordinatorlayout:1.3.0", true);
        public static final Dependency DRAWER_LAYOUT = new Dependency("androidx.drawerlayout:drawerlayout:1.1.1", true);
        public static final Dependency SLIDING_PANE_LAYOUT = new Dependency("androidx.slidingpanelayout:slidingpanelayout:1.0.0", true);
        public static final Dependency FRAGMENT = new Dependency("androidx.fragment:fragment:1.6.1", true);
        public static final Dependency VIEWPAGER2 = new Dependency("androidx.viewpager2:viewpager2:1.1.0-beta02", true);
        public static final Dependency FLEXBOX = new Dependency("com.google.android.flexbox:flexbox:3.0.0", false);
        public static final Dependency MEDIA3_UI = new Dependency("androidx.media3:media3-ui:1.11.1", false);
        public static final Dependency PHOTO_VIEW = new Dependency("com.github.chrisbanes:PhotoView:2.3.0", false);
        public static final Dependency SHIMMER = new Dependency("com.facebook.shimmer:shimmer:0.5.0", false);
        public static final Dependency MP_ANDROID_CHART = new Dependency("com.github.PhilJay:MPAndroidChart:v3.1.0", false);
        public static final Dependency GRAPH_VIEW = new Dependency("com.jjoe64:graphview:4.2.2", false);
        public static final Dependency COMPOSE_UI = new Dependency("androidx.compose.ui:ui", false);

        public boolean isPlatform() {
            return coordinates.isEmpty();
        }
    }

    /** @return the simple class name, e.g. {@code MaterialSwitch}. */
    public String simpleName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** @return whether the component is an actual View the canvas can hold. */
    public boolean isVisual() {
        return kind == Kind.VIEW || kind == Kind.VIEW_GROUP || kind == Kind.CUSTOM_COMPONENT;
    }
}
