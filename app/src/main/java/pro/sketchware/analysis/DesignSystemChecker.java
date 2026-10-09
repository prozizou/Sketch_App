package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import pro.sketchware.editor.layout.SpacingScale;

/**
 * Checks that a project's screens follow a consistent design system: the 4 dp spacing rhythm, the Material 3 type
 * scale, and a small set of colours. It only reports; nothing is changed. Rules are {@code design.*}.
 */
public final class DesignSystemChecker {
    /** Text sizes (sp) of the Material 3 type scale, rounded. */
    static final int[] TYPE_SCALE = {11, 12, 14, 16, 22, 24, 28, 32, 36, 45, 57};
    static final int MAX_TEXT_SIZES = 5;
    static final int MAX_COLORS = 8;
    /** Two colours closer than this (sum of the channel differences) are probably meant to be one. */
    static final int NEAR_DUPLICATE_DISTANCE = 24;

    private DesignSystemChecker() {
    }

    public static List<Finding> analyze(List<ViewFacts> views) {
        List<Finding> out = new ArrayList<>();
        List<String> offScaleSpacing = new ArrayList<>();
        List<String> offScaleText = new ArrayList<>();
        Set<Integer> textSizes = new TreeSet<>();
        Set<Integer> colors = new LinkedHashSet<>();
        for (ViewFacts view : views) {
            if (hasOffScaleSpacing(view)) offScaleSpacing.add(view.label());
            if (view.textSizeSp() > 0) {
                textSizes.add(view.textSizeSp());
                if (!onTypeScale(view.textSizeSp())) offScaleText.add(view.label() + " (" + view.textSizeSp() + " sp)");
            }
            if (view.textColor() != null && opaque(view.textColor())) colors.add(rgb(view.textColor()));
            if (view.backgroundColor() != null && opaque(view.backgroundColor())) colors.add(rgb(view.backgroundColor()));
        }
        if (!offScaleSpacing.isEmpty()) {
            out.add(new Finding("design.spacing-off-scale", Category.QUALITY, Severity.INFO,
                    "Margins or padding off the 4 dp rhythm (" + offScaleSpacing.size() + " widgets)",
                    "Material Design spaces things in steps of 4 dp (4, 8, 12, 16, 24...). Odd values make the screens look uneven and are hard to keep consistent.",
                    "Use values from 0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 56, 64 dp.", sample(offScaleSpacing)));
        }
        if (!offScaleText.isEmpty()) {
            out.add(new Finding("design.text-size-off-scale", Category.QUALITY, Severity.INFO,
                    "Text sizes outside the Material type scale (" + offScaleText.size() + " widgets)",
                    "The Material 3 type scale uses a fixed set of sizes so text hierarchy stays consistent between screens.",
                    "Use 11, 12, 14, 16, 22, 24, 28, 32, 36, 45 or 57 sp.", sample(offScaleText)));
        }
        if (textSizes.size() > MAX_TEXT_SIZES) {
            out.add(new Finding("design.too-many-text-sizes", Category.QUALITY, Severity.INFO,
                    textSizes.size() + " different text sizes",
                    "More than " + MAX_TEXT_SIZES + " sizes usually means the text hierarchy is not planned, and the screens feel inconsistent.",
                    "Pick a few roles (title, heading, body, caption) and use one size for each.", textSizes.toString()));
        }
        if (colors.size() > MAX_COLORS) {
            out.add(new Finding("design.too-many-colors", Category.QUALITY, Severity.INFO,
                    colors.size() + " different typed-in colours",
                    "Colours typed into widgets cannot follow a theme or dark mode and are hard to change together.",
                    "Define the colours once in the project's colour resources (primary, surface, on-surface...) and refer to them.", null));
        }
        List<String> nearDuplicates = nearDuplicates(new ArrayList<>(colors));
        if (!nearDuplicates.isEmpty()) {
            out.add(new Finding("design.near-duplicate-colors", Category.QUALITY, Severity.INFO,
                    "Colours that are almost the same (" + nearDuplicates.size() + " pairs)",
                    "Two colours that differ by a hair are nearly always one colour typed twice, and they will drift apart over time.",
                    "Use one colour for both, ideally from the colour resources.", sample(nearDuplicates)));
        }
        return out;
    }

    static boolean hasOffScaleSpacing(ViewFacts view) {
        return off(view.marginLeft()) || off(view.marginRight()) || off(view.paddingLeft()) || off(view.paddingRight());
    }

    private static boolean off(int dp) {
        return dp > 0 && !SpacingScale.isOnScale(dp);
    }

    static boolean onTypeScale(int sp) {
        for (int step : TYPE_SCALE) {
            if (step == sp) return true;
        }
        return false;
    }

    private static boolean opaque(int argb) {
        return (argb >>> 24) == 0xff;
    }

    private static int rgb(int argb) {
        return argb & 0xffffff;
    }

    static List<String> nearDuplicates(List<Integer> colors) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                int a = colors.get(i), b = colors.get(j);
                int distance = Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff))
                        + Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff))
                        + Math.abs((a & 0xff) - (b & 0xff));
                if (distance > 0 && distance <= NEAR_DUPLICATE_DISTANCE) {
                    pairs.add(String.format("#%06X ~ #%06X", a, b));
                }
            }
        }
        return pairs;
    }

    private static String sample(List<String> items) {
        int shown = Math.min(5, items.size());
        String text = String.join(", ", items.subList(0, shown));
        return items.size() > shown ? text + ", ..." : text;
    }
}
