package pro.sketchware.analysis.fix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import pro.sketchware.analysis.CompatibilityAnalyzer;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.ViewFacts;
import pro.sketchware.editor.layout.SpacingScale;
import pro.sketchware.logic.BlockGraph;
import pro.sketchware.logic.LogicEvent;
import pro.sketchware.logic.LogicScreen;

/**
 * Works out the changes that fix the problems which have one safe answer, without asking: widget sizes, text sizes,
 * text colour for contrast, spacing on the 4 dp rhythm, image descriptions, and blocks that never run. It only plans;
 * the editor applies the plan. Everything else needs a decision from the user and is left alone.
 */
public final class AutoFix {
    /** A kind of fix, the finding it answers, and whether it is ticked by default. */
    public enum Rule {
        CONTENT_DESCRIPTION("a11y.content-description", true),
        TOUCH_TARGET("a11y.touch-target", true),
        SMALL_TEXT("a11y.small-text", true),
        CONTRAST("a11y.contrast", true),
        SPACING("design.spacing-off-scale", true),
        TEXT_SCALE("design.text-size-off-scale", true),
        /** Moves typed-in text to strings.xml and points the widget at it. */
        HARDCODED_TEXT("quality.hardcoded-text", true),
        /** Deletes blocks: off by default, since loose blocks are sometimes kept on purpose as drafts. */
        UNCONNECTED_BLOCKS("logic.unconnected-blocks", false);

        public final String findingId;
        public final boolean onByDefault;

        Rule(String findingId, boolean onByDefault) {
            this.findingId = findingId;
            this.onByDefault = onByDefault;
        }

        public static Rule forFinding(String findingId) {
            for (Rule rule : values()) if (rule.findingId.equals(findingId)) return rule;
            return null;
        }
    }

    /** {@link #TEXT_TO_RESOURCE}: the widget's own text goes to strings.xml; the key is chosen when it is applied. */
    public enum Field {WIDTH, HEIGHT, TEXT_SIZE, TEXT_COLOR, MARGIN_LEFT, MARGIN_RIGHT, PADDING_LEFT, PADDING_RIGHT, CONTENT_DESCRIPTION, TEXT_TO_RESOURCE}

    /**
     * Set {@code field} of widget {@code id} on layout {@code screen} (without {@code .xml}) to {@code value}, or to
     * {@code text} for {@link Field#CONTENT_DESCRIPTION}.
     */
    public record WidgetChange(Rule rule, String screen, String id, Field field, int value, String text) {
        public String describe() {
            return screen + " / " + id + ": " + switch (field) {
                case WIDTH -> "width " + value + " dp";
                case HEIGHT -> "height " + value + " dp";
                case TEXT_SIZE -> "text size " + value + " sp";
                case TEXT_COLOR -> String.format(Locale.ROOT, "text colour #%06X", value & 0xffffff);
                case MARGIN_LEFT -> "left margin " + value + " dp";
                case MARGIN_RIGHT -> "right margin " + value + " dp";
                case PADDING_LEFT -> "left padding " + value + " dp";
                case PADDING_RIGHT -> "right padding " + value + " dp";
                case CONTENT_DESCRIPTION -> "description \"" + text + "\"";
                case TEXT_TO_RESOURCE -> "text moved to strings.xml";
            };
        }
    }

    /** Delete the blocks {@code blockIds} of event {@code eventKey} of screen {@code javaName}. */
    public record BlockRemoval(String javaName, String eventKey, Set<String> blockIds) {
    }

    public record Plan(List<WidgetChange> widgetChanges, List<BlockRemoval> blockRemovals) {
        public int count(Rule rule) {
            if (rule == Rule.UNCONNECTED_BLOCKS) {
                return blockRemovals.stream().mapToInt(r -> r.blockIds().size()).sum();
            }
            return (int) widgetChanges.stream().filter(c -> c.rule() == rule).count();
        }

        public boolean isEmpty() {
            return widgetChanges.isEmpty() && blockRemovals.isEmpty();
        }

        /** Only the changes of the chosen rules. */
        public Plan only(Set<Rule> rules) {
            return new Plan(widgetChanges.stream().filter(c -> rules.contains(c.rule())).toList(),
                    rules.contains(Rule.UNCONNECTED_BLOCKS) ? blockRemovals : List.of());
        }
    }

    /** The Material 3 type scale from the smallest readable size up (11 sp is below the 12 sp minimum). */
    static final int[] READABLE_TYPE_SCALE = {12, 14, 16, 22, 24, 28, 32, 36, 45, 57};
    static final int TOUCH_TARGET_DP = 48;
    static final int MIN_TEXT_SP = 12;
    static final int LARGE_TEXT_SP = 18;
    static final int BLACK = 0xff000000;
    static final int WHITE = 0xffffffff;

    private AutoFix() {
    }

    public static Plan plan(List<ViewFacts> views, List<LogicScreen> screens) {
        // One change per widget field: a later rule refines an earlier one (small text, then the type scale)
        Map<String, WidgetChange> changes = new LinkedHashMap<>();
        for (ViewFacts view : views) {
            if (view.kind() == ViewFacts.Kind.IMAGE && !view.hasContentDescription()) {
                put(changes, new WidgetChange(Rule.CONTENT_DESCRIPTION, view.screen(), view.id(), Field.CONTENT_DESCRIPTION, 0, describe(null, view.id())));
            }
            if (view.clickable()) {
                if (view.widthDp() >= 0 && view.widthDp() < TOUCH_TARGET_DP) {
                    put(changes, new WidgetChange(Rule.TOUCH_TARGET, view.screen(), view.id(), Field.WIDTH, TOUCH_TARGET_DP, null));
                }
                if (view.heightDp() >= 0 && view.heightDp() < TOUCH_TARGET_DP) {
                    put(changes, new WidgetChange(Rule.TOUCH_TARGET, view.screen(), view.id(), Field.HEIGHT, TOUCH_TARGET_DP, null));
                }
            }
            int size = view.textSizeSp();
            if (size > 0) {
                // Below 12 sp is too small to read; off the type scale is rounded to its nearest readable step
                Rule rule = size < MIN_TEXT_SP ? Rule.SMALL_TEXT : isOnFullScale(size) ? null : Rule.TEXT_SCALE;
                if (rule != null) {
                    size = nearestReadable(Math.max(size, MIN_TEXT_SP));
                    put(changes, new WidgetChange(rule, view.screen(), view.id(), Field.TEXT_SIZE, size, null));
                }
            }
            if (view.textColor() != null && view.backgroundColor() != null) {
                double needed = size >= LARGE_TEXT_SP ? 3.0 : 4.5;
                if (CompatibilityAnalyzer.contrastRatio(view.textColor(), view.backgroundColor()) < needed) {
                    put(changes, new WidgetChange(Rule.CONTRAST, view.screen(), view.id(), Field.TEXT_COLOR, readableOn(view.backgroundColor()), null));
                }
            }
            if (view.hardcodedText()) {
                put(changes, new WidgetChange(Rule.HARDCODED_TEXT, view.screen(), view.id(), Field.TEXT_TO_RESOURCE, 0, null));
            }
            spacing(changes, view, Field.MARGIN_LEFT, view.marginLeft());
            spacing(changes, view, Field.MARGIN_RIGHT, view.marginRight());
            spacing(changes, view, Field.PADDING_LEFT, view.paddingLeft());
            spacing(changes, view, Field.PADDING_RIGHT, view.paddingRight());
        }

        List<BlockRemoval> removals = new ArrayList<>();
        for (LogicScreen screen : screens) {
            for (LogicEvent event : screen.events()) {
                Set<String> reachable = new BlockGraph(event).reachable();
                Set<String> loose = new LinkedHashSet<>();
                event.blocks().forEach(block -> {
                    if (!reachable.contains(block.id())) loose.add(block.id());
                });
                if (!loose.isEmpty()) removals.add(new BlockRemoval(screen.javaName(), event.key(), loose));
            }
        }
        return new Plan(new ArrayList<>(changes.values()), removals);
    }

    private static void spacing(Map<String, WidgetChange> changes, ViewFacts view, Field field, int dp) {
        if (dp > 0 && !SpacingScale.isOnScale(dp)) {
            put(changes, new WidgetChange(Rule.SPACING, view.screen(), view.id(), field, SpacingScale.nearest(dp), null));
        }
    }

    private static void put(Map<String, WidgetChange> changes, WidgetChange change) {
        changes.put(change.screen() + "\u0000" + change.id() + "\u0000" + change.field(), change);
    }

    private static boolean isOnFullScale(int sp) {
        if (sp == 11) return true;
        for (int step : READABLE_TYPE_SCALE) if (step == sp) return true;
        return false;
    }

    /** The nearest readable type-scale size; the smaller one on a tie. */
    static int nearestReadable(int sp) {
        int best = READABLE_TYPE_SCALE[0];
        for (int step : READABLE_TYPE_SCALE) {
            if (Math.abs(step - sp) < Math.abs(best - sp)) best = step;
        }
        return best;
    }

    /** Black or white, whichever contrasts more with {@code background}. */
    static int readableOn(int background) {
        return CompatibilityAnalyzer.contrastRatio(BLACK, background) >= CompatibilityAnalyzer.contrastRatio(WHITE, background) ? BLACK : WHITE;
    }

    /**
     * A first description for an image: from its picture's name when it has one ({@code ic_user_avatar} gives
     * "User avatar"), else from the widget id ({@code imageview1} gives "Imageview 1"). The user should check it.
     */
    public static String describe(String pictureName, String widgetId) {
        String source = pictureName != null && !pictureName.isEmpty() && !pictureName.equals("default_image") ? pictureName : widgetId;
        String text = source.replaceFirst("^(ic|img|image|icon|bg)_", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replace('_', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
        return text.isEmpty() ? widgetId : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** What {@code view} looks like once {@code changes} are applied; lets the plan be checked against the analyses. */
    public static ViewFacts applyTo(ViewFacts view, List<WidgetChange> changes) {
        int width = view.widthDp(), height = view.heightDp(), textSize = view.textSizeSp();
        int marginLeft = view.marginLeft(), marginRight = view.marginRight(), paddingLeft = view.paddingLeft(), paddingRight = view.paddingRight();
        Integer textColor = view.textColor();
        boolean described = view.hasContentDescription();
        boolean hardcoded = view.hardcodedText();
        for (WidgetChange change : changes) {
            if (!change.screen().equals(view.screen()) || !change.id().equals(view.id())) continue;
            switch (change.field()) {
                case WIDTH -> width = change.value();
                case HEIGHT -> height = change.value();
                case TEXT_SIZE -> textSize = change.value();
                case TEXT_COLOR -> textColor = change.value();
                case MARGIN_LEFT -> marginLeft = change.value();
                case MARGIN_RIGHT -> marginRight = change.value();
                case PADDING_LEFT -> paddingLeft = change.value();
                case PADDING_RIGHT -> paddingRight = change.value();
                case CONTENT_DESCRIPTION -> described = true;
                case TEXT_TO_RESOURCE -> hardcoded = false;
            }
        }
        return new ViewFacts(view.screen(), view.id(), view.kind(), width, height, view.clickable(), described, textColor,
                view.backgroundColor(), textSize, marginLeft, marginRight, paddingLeft, paddingRight, hardcoded);
    }

    /** Findings this tool cannot fix, because they need a decision. */
    public static List<Finding> notFixable(List<Finding> findings) {
        return findings.stream().filter(f -> Rule.forFinding(f.id()) == null).toList();
    }
}
