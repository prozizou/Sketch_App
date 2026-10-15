package pro.sketchware.resolver;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.resolver.ResourceInventory.Definition;
import pro.sketchware.resolver.ResourceInventory.Kind;

/**
 * Looks for what could stand in for a missing resource. It looks at the project first (the same name in another
 * qualifier, the same name in drawable/mipmap, similar names) and only then at the offline icon library. Nothing is
 * invented: every suggestion points at a real resource or a real library icon, and a weak match is offered but never
 * applied without a person choosing it.
 */
public final class ResourceMatcher {
    private static final int MAX_EXISTING = 5;
    private static final int MAX_ICONS = 2;

    private ResourceMatcher() {
    }

    /** @return suggestions, project resources first, each group best first; empty when nothing fits or it isn't fixable */
    public static List<Suggestion> suggest(MissingResource missing, ResourceInventory inventory, @Nullable IconLibrary library) {
        List<Suggestion> result = new ArrayList<>();
        if (!missing.fixable()) return result;
        result.addAll(fromProject(missing, inventory));
        if (ResourceTypes.isFile(missing.type()) && library != null) result.addAll(fromLibrary(missing, library));
        return result;
    }

    /** The first suggestion if it is sure enough to apply without asking, else null. */
    @Nullable
    public static Suggestion confident(List<Suggestion> suggestions) {
        return suggestions.isEmpty() || !suggestions.get(0).confident() ? null : suggestions.get(0);
    }

    private static List<Suggestion> fromProject(MissingResource missing, ResourceInventory inventory) {
        String type = missing.type();
        String name = missing.name();
        List<Suggestion> found = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // 1. The same resource exists in another qualifier.
        for (Definition d : inventory.find(type, name)) {
            if (!used.add(d.path() + "|" + d.name())) continue;
            found.add(new Suggestion(d.kind() == Kind.FILE ? Suggestion.Kind.COPY_FILE : Suggestion.Kind.COPY_VALUE, 0.95, true, d, null, null));
        }
        // 2. A file of the same name in the sibling type (drawable <-> mipmap).
        String sibling = siblingType(type);
        if (sibling != null) {
            for (Definition d : inventory.find(sibling, name)) {
                if (d.kind() != Kind.FILE || !used.add(d.path() + "|" + d.name())) continue;
                found.add(new Suggestion(Suggestion.Kind.COPY_FILE, 0.92, true, d, null, null));
            }
        }
        // 3. Similar names.
        String key = NameTokens.key(name);
        if (!key.isEmpty()) {
            List<Definition> candidates = new ArrayList<>(inventory.ofType(type));
            if (sibling != null) candidates.addAll(inventory.ofType(sibling));
            Set<String> namesDone = new HashSet<>();
            namesDone.add(ResourceNames.foldedKey(name));
            for (Definition d : candidates) {
                if (!namesDone.add(type.equals(d.type()) ? ResourceNames.foldedKey(d.name()) : d.type() + "/" + ResourceNames.foldedKey(d.name()))) continue;
                double score = similarity(key, NameTokens.key(d.name()));
                if (score <= 0) continue;
                Suggestion.Kind kind;
                if (d.kind() == Kind.FILE && !(d.type().equals("drawable") && type.equals("drawable"))) {
                    kind = Suggestion.Kind.COPY_FILE; // a file of another type, or a mipmap: copy it
                } else if (ResourceTypes.ALIAS_TYPES.contains(type) && d.type().equals(type)) {
                    kind = Suggestion.Kind.ALIAS;
                } else {
                    continue;
                }
                found.add(new Suggestion(kind, score, true, d, null, null));
            }
        }
        found.sort(Comparator.comparingDouble(Suggestion::score).reversed());
        return found.size() > MAX_EXISTING ? new ArrayList<>(found.subList(0, MAX_EXISTING)) : found;
    }

    private static List<Suggestion> fromLibrary(MissingResource missing, IconLibrary library) {
        List<Suggestion> found = new ArrayList<>();
        String key = NameTokens.key(missing.name());
        if (key.isEmpty()) return found;
        String style = NameTokens.styleOf(missing.name());
        if (style == null) style = IconLibrary.DEFAULT_STYLE;
        IconLibrary.IconInfo exact = library.find(key);
        if (exact != null) {
            found.add(new Suggestion(Suggestion.Kind.MATERIAL_ICON, 0.88, false, null, exact.name(), styleFor(exact, style)));
        } else if (key.length() >= 4) {
            for (IconLibrary.IconInfo icon : library.search(key, 60)) {
                if (NameTokens.distance(key, icon.name()) <= 1 && found.size() < MAX_ICONS) {
                    found.add(new Suggestion(Suggestion.Kind.MATERIAL_ICON, 0.7, false, null, icon.name(), styleFor(icon, style)));
                }
            }
        }
        return found;
    }

    private static String styleFor(IconLibrary.IconInfo icon, String wanted) {
        if (icon.styles().contains(wanted)) return wanted;
        return icon.styles().contains(IconLibrary.DEFAULT_STYLE) ? IconLibrary.DEFAULT_STYLE : icon.styles().get(0);
    }

    /** 0 when the two name keys are unrelated, else how alike they are. */
    static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 0.85;
        int shortest = Math.min(a.length(), b.length());
        int distance = NameTokens.distance(a, b);
        if (distance <= 1 && shortest >= 4) return 0.75;
        if (distance <= 2 && shortest >= 7) return 0.65;
        List<String> ta = List.of(a.split("_"));
        List<String> tb = List.of(b.split("_"));
        List<String> smaller = ta.size() <= tb.size() ? ta : tb;
        List<String> larger = ta.size() <= tb.size() ? tb : ta;
        if (smaller.size() < larger.size() && larger.containsAll(smaller) && String.join("_", smaller).length() >= 3) return 0.6;
        return 0;
    }

    @Nullable
    private static String siblingType(String type) {
        return switch (type) {
            case "drawable" -> "mipmap";
            case "mipmap" -> "drawable";
            default -> null;
        };
    }
}
