package pro.sketchware.properties;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import a.a.a.Gx;
import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * The Android attributes a view can carry beyond the ones the editor has dedicated rows for, grouped in
 * sections and filtered by what the selected view is (a TextView gets text attributes, a GridView gets
 * columns and spacing, and so on). Values are written to the view's custom attributes, so they end up in
 * the generated layout XML.
 */
public final class AttributeCatalog {
    public enum Type {BOOLEAN, ENUM, FLAGS, DIMENSION, INTEGER, FLOAT, COLOR, REFERENCE, STRING}

    /** Which views an attribute applies to. */
    public enum Target {
        VIEW, GROUP, LINEAR, SCROLL, TEXT, EDIT_TEXT, IMAGE, COMPOUND, GRID, LIST, ABS_LIST, RECYCLER,
        PROGRESS, MATERIAL_BUTTON, CARD;

        boolean matches(Gx info, int viewType) {
            return switch (this) {
                case VIEW -> true;
                case GROUP -> info.a("ViewGroup");
                case LINEAR -> info.a("LinearLayout");
                case SCROLL -> info.a("ScrollView") || info.a("HorizontalScrollView");
                case TEXT -> info.a("TextView");
                case EDIT_TEXT -> info.a("EditText");
                case IMAGE -> info.a("ImageView");
                case COMPOUND -> info.a("CompoundButton");
                case GRID -> info.a("GridView");
                case LIST -> info.a("ListView");
                case ABS_LIST -> info.a("AbsListView");
                case RECYCLER -> viewType == ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW || info.a("RecyclerView");
                case PROGRESS -> info.a("ProgressBar");
                case MATERIAL_BUTTON -> viewType == ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON || info.a("MaterialButton");
                case CARD -> viewType == ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW || info.a("CardView");
            };
        }
    }

    public record Attr(String name, Type type, List<String> options, Target target) {
        /** "android:minWidth" becomes "Min width". */
        public String label() {
            String bare = name.substring(name.indexOf(':') + 1);
            if (bare.startsWith("layout_")) {
                bare = bare.substring("layout_".length());
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < bare.length(); i++) {
                char c = bare.charAt(i);
                if (c == '_') {
                    out.append(' ');
                } else if (Character.isUpperCase(c) && i > 0) {
                    out.append(' ').append(Character.toLowerCase(c));
                } else {
                    out.append(i == 0 ? Character.toUpperCase(c) : c);
                }
            }
            return out.toString();
        }

        /** A short example shown as a hint in the editor. */
        public String example() {
            return switch (type) {
                case DIMENSION -> "16dp";
                case INTEGER -> "1";
                case FLOAT -> "0.5";
                case COLOR -> "#FF5722";
                case REFERENCE -> "@drawable/name";
                case STRING -> "";
                default -> "";
            };
        }

        /** Why {@code value} can't be used, or {@code null} when it is fine. */
        @Nullable
        public String problemWith(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            if (value.contains("\"") || value.contains("\n") || value.contains(" ")) {
                // Spaces are stripped when the layout is generated, so they would silently change the value.
                return "No spaces or quotes";
            }
            boolean reference = value.startsWith("@") || value.startsWith("?");
            return switch (type) {
                case BOOLEAN -> value.equals("true") || value.equals("false") || reference ? null : "true or false";
                case ENUM -> options.contains(value) || reference ? null : "One of: " + String.join(", ", options);
                case FLAGS -> {
                    for (String part : value.split("\\|")) {
                        if (!options.contains(part)) {
                            yield "Any of: " + String.join(", ", options);
                        }
                    }
                    yield null;
                }
                case DIMENSION -> DIMENSION_PATTERN.matcher(value).matches() || reference ? null : "Like 16dp, 12sp, @dimen/name";
                case INTEGER -> INTEGER_PATTERN.matcher(value).matches() || reference ? null : "A whole number";
                case FLOAT -> FLOAT_PATTERN.matcher(value).matches() || reference ? null : "A number";
                case COLOR -> COLOR_PATTERN.matcher(value).matches() || reference ? null : "Like #FF5722 or @color/name";
                case REFERENCE -> reference || COLOR_PATTERN.matcher(value).matches() ? null : "Like @drawable/name";
                case STRING -> null;
            };
        }
    }

    public record Section(String key, @StringRes int titleRes, List<Attr> attrs) {
        public List<String> names() {
            List<String> names = new ArrayList<>();
            for (Attr attr : attrs) {
                names.add(attr.name());
            }
            return names;
        }
    }

    private static final Pattern DIMENSION_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?(dp|dip|sp|px|pt|in|mm)$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern FLOAT_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private static final List<Section> ALL = new ArrayList<>();

    private static List<String> o(String... values) {
        return List.of(values);
    }

    private static Attr a(String name, Type type, Target target, String... options) {
        return new Attr(name, type, List.of(options), target);
    }

    private static void section(String key, @StringRes int title, Attr... attrs) {
        ALL.add(new Section(key, title, List.of(attrs)));
    }

    static {
        Target V = Target.VIEW;
        section("identity", R.string.property_section_identity,
                a("android:tag", Type.STRING, V),
                a("android:contentDescription", Type.STRING, V),
                a("android:transitionName", Type.STRING, V),
                a("android:tooltipText", Type.STRING, V),
                a("android:accessibilityHeading", Type.BOOLEAN, V),
                a("android:screenReaderFocusable", Type.BOOLEAN, V),
                a("android:importantForAccessibility", Type.ENUM, V, "auto", "yes", "no", "noHideDescendants"),
                a("android:accessibilityLiveRegion", Type.ENUM, V, "none", "polite", "assertive"),
                a("android:labelFor", Type.REFERENCE, V));

        section("layout", R.string.property_section_layout,
                a("android:minWidth", Type.DIMENSION, V),
                a("android:minHeight", Type.DIMENSION, V),
                a("android:maxWidth", Type.DIMENSION, Target.IMAGE),
                a("android:maxHeight", Type.DIMENSION, Target.IMAGE),
                a("android:maxWidth", Type.DIMENSION, Target.TEXT),
                a("android:maxHeight", Type.DIMENSION, Target.TEXT),
                a("android:layout_marginStart", Type.DIMENSION, V),
                a("android:layout_marginEnd", Type.DIMENSION, V),
                a("android:layout_marginHorizontal", Type.DIMENSION, V),
                a("android:layout_marginVertical", Type.DIMENSION, V),
                a("android:paddingStart", Type.DIMENSION, V),
                a("android:paddingEnd", Type.DIMENSION, V),
                a("android:paddingHorizontal", Type.DIMENSION, V),
                a("android:paddingVertical", Type.DIMENSION, V),
                a("android:layoutDirection", Type.ENUM, V, "ltr", "rtl", "inherit", "locale"),
                a("android:clipChildren", Type.BOOLEAN, Target.GROUP),
                a("android:clipToPadding", Type.BOOLEAN, Target.GROUP),
                a("android:baselineAligned", Type.BOOLEAN, Target.LINEAR),
                a("android:baselineAlignedChildIndex", Type.INTEGER, Target.LINEAR),
                a("android:measureWithLargestChild", Type.BOOLEAN, Target.LINEAR),
                a("android:showDividers", Type.FLAGS, Target.LINEAR, "none", "beginning", "middle", "end"),
                a("android:divider", Type.REFERENCE, Target.LINEAR),
                a("android:dividerPadding", Type.DIMENSION, Target.LINEAR));

        section("appearance", R.string.property_section_appearance,
                a("android:backgroundTint", Type.COLOR, V),
                a("android:backgroundTintMode", Type.ENUM, V, "src_over", "src_in", "src_atop", "multiply", "screen", "add"),
                a("android:foreground", Type.REFERENCE, V),
                a("android:foregroundGravity", Type.FLAGS, V, "top", "bottom", "left", "right", "center_vertical", "center_horizontal", "center", "fill", "clip_vertical", "clip_horizontal"),
                a("android:foregroundTint", Type.COLOR, V),
                a("android:elevation", Type.DIMENSION, V),
                a("android:translationZ", Type.DIMENSION, V),
                a("android:stateListAnimator", Type.REFERENCE, V),
                a("android:outlineProvider", Type.ENUM, V, "background", "none", "bounds", "paddedBounds"),
                a("android:clipToOutline", Type.BOOLEAN, V),
                a("android:outlineAmbientShadowColor", Type.COLOR, V),
                a("android:outlineSpotShadowColor", Type.COLOR, V),
                a("android:forceDarkAllowed", Type.BOOLEAN, V));

        section("state", R.string.property_section_state,
                a("android:visibility", Type.ENUM, V, "visible", "invisible", "gone"),
                a("android:clickable", Type.BOOLEAN, V),
                a("android:longClickable", Type.BOOLEAN, V),
                a("android:contextClickable", Type.BOOLEAN, V),
                a("android:focusable", Type.BOOLEAN, V),
                a("android:focusableInTouchMode", Type.BOOLEAN, V),
                a("android:focusedByDefault", Type.BOOLEAN, V),
                a("android:selected", Type.BOOLEAN, V),
                a("android:activated", Type.BOOLEAN, V),
                a("android:duplicateParentState", Type.BOOLEAN, V),
                a("android:nextFocusUp", Type.REFERENCE, V),
                a("android:nextFocusDown", Type.REFERENCE, V),
                a("android:nextFocusLeft", Type.REFERENCE, V),
                a("android:nextFocusRight", Type.REFERENCE, V));

        section("transform", R.string.property_section_transform,
                a("android:rotationX", Type.FLOAT, V),
                a("android:rotationY", Type.FLOAT, V),
                a("android:transformPivotX", Type.DIMENSION, V),
                a("android:transformPivotY", Type.DIMENSION, V));

        section("interaction", R.string.property_section_interaction,
                a("android:onClick", Type.STRING, V),
                a("android:soundEffectsEnabled", Type.BOOLEAN, V),
                a("android:hapticFeedbackEnabled", Type.BOOLEAN, V),
                a("android:pointerIcon", Type.ENUM, V, "none", "arrow", "context_menu", "help", "hand", "wait", "cell", "crosshair", "text", "vertical_text", "alias", "copy", "no_drop", "all_scroll", "horizontal_double_arrow", "vertical_double_arrow", "grab", "grabbing", "zoom_in", "zoom_out"),
                a("android:keepScreenOn", Type.BOOLEAN, V));

        section("scrolling", R.string.property_section_scrolling,
                a("android:scrollbars", Type.FLAGS, V, "none", "horizontal", "vertical"),
                a("android:scrollbarStyle", Type.ENUM, V, "insideOverlay", "insideInset", "outsideOverlay", "outsideInset"),
                a("android:scrollbarSize", Type.DIMENSION, V),
                a("android:scrollbarFadeDuration", Type.INTEGER, V),
                a("android:overScrollMode", Type.ENUM, V, "always", "ifContentScrolls", "never"),
                a("android:nestedScrollingEnabled", Type.BOOLEAN, V),
                a("android:requiresFadingEdge", Type.FLAGS, V, "none", "horizontal", "vertical"),
                a("android:fadingEdgeLength", Type.DIMENSION, V),
                a("android:isScrollContainer", Type.BOOLEAN, V),
                a("android:fillViewport", Type.BOOLEAN, Target.SCROLL));

        section("text", R.string.property_section_text,
                a("android:fontFamily", Type.STRING, Target.TEXT),
                a("android:textAlignment", Type.ENUM, Target.TEXT, "inherit", "gravity", "textStart", "textEnd", "center", "viewStart", "viewEnd"),
                a("android:textDirection", Type.ENUM, Target.TEXT, "inherit", "firstStrong", "anyRtl", "ltr", "rtl", "locale", "firstStrongLtr", "firstStrongRtl"),
                a("android:letterSpacing", Type.FLOAT, Target.TEXT),
                a("android:lineSpacingExtra", Type.DIMENSION, Target.TEXT),
                a("android:lineSpacingMultiplier", Type.FLOAT, Target.TEXT),
                a("android:maxLines", Type.INTEGER, Target.TEXT),
                a("android:minLines", Type.INTEGER, Target.TEXT),
                a("android:ems", Type.INTEGER, Target.TEXT),
                a("android:minEms", Type.INTEGER, Target.TEXT),
                a("android:maxEms", Type.INTEGER, Target.TEXT),
                a("android:ellipsize", Type.ENUM, Target.TEXT, "none", "start", "middle", "end", "marquee"),
                a("android:marqueeRepeatLimit", Type.INTEGER, Target.TEXT),
                a("android:includeFontPadding", Type.BOOLEAN, Target.TEXT),
                a("android:textAllCaps", Type.BOOLEAN, Target.TEXT),
                a("android:textFontWeight", Type.INTEGER, Target.TEXT),
                a("android:textIsSelectable", Type.BOOLEAN, Target.TEXT),
                a("android:freezesText", Type.BOOLEAN, Target.TEXT),
                a("android:breakStrategy", Type.ENUM, Target.TEXT, "simple", "high_quality", "balanced"),
                a("android:hyphenationFrequency", Type.ENUM, Target.TEXT, "none", "normal", "full"),
                a("android:autoLink", Type.FLAGS, Target.TEXT, "none", "web", "email", "phone", "map", "all"),
                a("android:linksClickable", Type.BOOLEAN, Target.TEXT),
                a("android:textColorLink", Type.COLOR, Target.TEXT),
                a("android:textColorHighlight", Type.COLOR, Target.TEXT),
                a("android:shadowColor", Type.COLOR, Target.TEXT),
                a("android:shadowDx", Type.FLOAT, Target.TEXT),
                a("android:shadowDy", Type.FLOAT, Target.TEXT),
                a("android:shadowRadius", Type.FLOAT, Target.TEXT),
                a("android:drawableStart", Type.REFERENCE, Target.TEXT),
                a("android:drawableEnd", Type.REFERENCE, Target.TEXT),
                a("android:drawableTop", Type.REFERENCE, Target.TEXT),
                a("android:drawableBottom", Type.REFERENCE, Target.TEXT),
                a("android:drawablePadding", Type.DIMENSION, Target.TEXT),
                a("android:drawableTint", Type.COLOR, Target.TEXT));

        section("input", R.string.property_section_input,
                a("android:selectAllOnFocus", Type.BOOLEAN, Target.EDIT_TEXT),
                a("android:cursorVisible", Type.BOOLEAN, Target.EDIT_TEXT),
                a("android:textCursorDrawable", Type.REFERENCE, Target.EDIT_TEXT),
                a("android:textSelectHandle", Type.REFERENCE, Target.EDIT_TEXT),
                a("android:autofillHints", Type.STRING, Target.EDIT_TEXT),
                a("android:importantForAutofill", Type.ENUM, Target.EDIT_TEXT, "auto", "no", "noExcludeDescendants", "yes", "yesExcludeDescendants"),
                a("android:digits", Type.STRING, Target.EDIT_TEXT),
                a("android:maxLength", Type.INTEGER, Target.EDIT_TEXT),
                a("android:imeActionLabel", Type.STRING, Target.EDIT_TEXT),
                a("android:imeActionId", Type.INTEGER, Target.EDIT_TEXT),
                a("android:privateImeOptions", Type.STRING, Target.EDIT_TEXT),
                a("android:button", Type.REFERENCE, Target.COMPOUND),
                a("android:buttonTint", Type.COLOR, Target.COMPOUND));

        section("image", R.string.property_section_image,
                a("android:adjustViewBounds", Type.BOOLEAN, Target.IMAGE),
                a("android:cropToPadding", Type.BOOLEAN, Target.IMAGE),
                a("android:baselineAlignBottom", Type.BOOLEAN, Target.IMAGE),
                a("android:tint", Type.COLOR, Target.IMAGE),
                a("android:tintMode", Type.ENUM, Target.IMAGE, "src_over", "src_in", "src_atop", "multiply", "screen", "add"),
                a("android:indeterminateTint", Type.COLOR, Target.PROGRESS),
                a("android:progressTint", Type.COLOR, Target.PROGRESS),
                a("android:progressBackgroundTint", Type.COLOR, Target.PROGRESS));

        section("list", R.string.property_section_list,
                a("android:numColumns", Type.STRING, Target.GRID),
                a("android:columnWidth", Type.DIMENSION, Target.GRID),
                a("android:horizontalSpacing", Type.DIMENSION, Target.GRID),
                a("android:verticalSpacing", Type.DIMENSION, Target.GRID),
                a("android:stretchMode", Type.ENUM, Target.GRID, "none", "spacingWidth", "columnWidth", "spacingWidthUniform"),
                a("android:gravity", Type.FLAGS, Target.GRID, "top", "bottom", "left", "right", "center_vertical", "center_horizontal", "center", "fill"),
                a("android:divider", Type.REFERENCE, Target.LIST),
                a("android:headerDividersEnabled", Type.BOOLEAN, Target.LIST),
                a("android:footerDividersEnabled", Type.BOOLEAN, Target.LIST),
                a("android:choiceMode", Type.ENUM, Target.ABS_LIST, "none", "singleChoice", "multipleChoice", "multipleChoiceModal"),
                a("android:listSelector", Type.REFERENCE, Target.ABS_LIST),
                a("android:cacheColorHint", Type.COLOR, Target.ABS_LIST),
                a("android:fastScrollEnabled", Type.BOOLEAN, Target.ABS_LIST),
                a("android:stackFromBottom", Type.BOOLEAN, Target.ABS_LIST),
                a("android:transcriptMode", Type.ENUM, Target.ABS_LIST, "disabled", "normal", "alwaysScroll"),
                a("android:smoothScrollbar", Type.BOOLEAN, Target.ABS_LIST),
                a("android:scrollingCache", Type.BOOLEAN, Target.ABS_LIST),
                a("android:drawSelectorOnTop", Type.BOOLEAN, Target.ABS_LIST),
                a("android:layoutAnimation", Type.REFERENCE, Target.ABS_LIST),
                a("app:layoutManager", Type.STRING, Target.RECYCLER),
                a("app:reverseLayout", Type.BOOLEAN, Target.RECYCLER),
                a("app:stackFromEnd", Type.BOOLEAN, Target.RECYCLER),
                a("app:spanCount", Type.INTEGER, Target.RECYCLER),
                a("android:orientation", Type.ENUM, Target.RECYCLER, "horizontal", "vertical"));

        section("material", R.string.property_section_material,
                a("app:cornerRadius", Type.DIMENSION, Target.MATERIAL_BUTTON),
                a("app:strokeWidth", Type.DIMENSION, Target.MATERIAL_BUTTON),
                a("app:strokeColor", Type.COLOR, Target.MATERIAL_BUTTON),
                a("app:rippleColor", Type.COLOR, Target.MATERIAL_BUTTON),
                a("app:icon", Type.REFERENCE, Target.MATERIAL_BUTTON),
                a("app:iconTint", Type.COLOR, Target.MATERIAL_BUTTON),
                a("app:iconSize", Type.DIMENSION, Target.MATERIAL_BUTTON),
                a("app:iconPadding", Type.DIMENSION, Target.MATERIAL_BUTTON),
                a("app:iconGravity", Type.ENUM, Target.MATERIAL_BUTTON, "start", "textStart", "end", "textEnd", "top", "textTop"),
                a("app:shapeAppearance", Type.REFERENCE, Target.MATERIAL_BUTTON),
                a("app:cardCornerRadius", Type.DIMENSION, Target.CARD),
                a("app:cardElevation", Type.DIMENSION, Target.CARD),
                a("app:cardBackgroundColor", Type.COLOR, Target.CARD),
                a("app:cardUseCompatPadding", Type.BOOLEAN, Target.CARD),
                a("app:cardPreventCornerOverlap", Type.BOOLEAN, Target.CARD),
                a("app:contentPadding", Type.DIMENSION, Target.CARD),
                a("app:strokeColor", Type.COLOR, Target.CARD),
                a("app:strokeWidth", Type.DIMENSION, Target.CARD),
                a("app:rippleColor", Type.COLOR, Target.CARD));

        section("system", R.string.property_section_system,
                a("android:fitsSystemWindows", Type.BOOLEAN, V),
                a("android:filterTouchesWhenObscured", Type.BOOLEAN, V),
                a("android:saveEnabled", Type.BOOLEAN, V),
                a("android:layerType", Type.ENUM, V, "none", "software", "hardware"),
                a("android:drawingCacheQuality", Type.ENUM, V, "auto", "low", "high"),
                a("android:importantForAutofill", Type.ENUM, V, "auto", "no", "noExcludeDescendants", "yes", "yesExcludeDescendants"));
    }

    private AttributeCatalog() {
    }

    /** The sections for a view the editor knows only by its class. */
    public static List<Section> sectionsFor(Gx info) {
        return sectionsFor(info, -1);
    }

    /** The sections that have attributes for this kind of view, in display order. */
    public static List<Section> sectionsFor(Gx info, int viewType) {
        List<Section> sections = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Section section : ALL) {
            List<Attr> attrs = new ArrayList<>();
            for (Attr attr : section.attrs()) {
                if (attr.target().matches(info, viewType) && !seen.contains(attr.name())) {
                    attrs.add(attr);
                    seen.add(attr.name());
                }
            }
            if (!attrs.isEmpty()) {
                sections.add(new Section(section.key(), section.titleRes(), attrs));
            }
        }
        return sections;
    }

    /** The attribute the catalog knows under {@code name} for this view, or a plain text one for any other name. */
    public static Attr find(List<Section> sections, String name) {
        for (Section section : sections) {
            for (Attr attr : section.attrs()) {
                if (attr.name().equals(name)) {
                    return attr;
                }
            }
        }
        return new Attr(name, Type.STRING, List.of(), Target.VIEW);
    }

    /**
     * The sections narrowed to attributes matching {@code query} (name or label) and not in {@code except};
     * sections left empty are dropped.
     */
    public static List<Section> search(List<Section> sections, String query, java.util.Collection<String> except) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Section> found = new ArrayList<>();
        for (Section section : sections) {
            List<Attr> attrs = new ArrayList<>();
            for (Attr attr : section.attrs()) {
                if (!except.contains(attr.name()) && (needle.isEmpty() || searchText(attr).contains(needle))) {
                    attrs.add(attr);
                }
            }
            if (!attrs.isEmpty()) {
                found.add(new Section(section.key(), section.titleRes(), attrs));
            }
        }
        return found;
    }

    /** Lower-case text to search in for an attribute: its name and its label. */
    public static String searchText(Attr attr) {
        return (attr.name() + " " + attr.label()).toLowerCase(Locale.ROOT);
    }
}
