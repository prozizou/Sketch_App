package pro.sketchware.resolver;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import pro.sketchware.resolver.ResourceInventory.Definition;

/**
 * Turns the chosen resolutions into the exact changes to make, and keeps out the ones that can't be made: a name that
 * is already taken, two resolutions for one resource, a value AAPT2 wouldn't read, a source that is gone. Nothing is
 * renamed to get around a clash and nothing existing is replaced; a clash is a {@link Conflict} for the person to see.
 */
public final class PlanBuilder {
    /** The changes to make, and what was left out and why. */
    public record Plan(List<PlannedChange> changes, List<Conflict> conflicts) {
    }

    private static final Pattern COLOR = Pattern.compile("#([0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");
    private static final Pattern DIMEN = Pattern.compile("-?(\\d+\\.?\\d*|\\.\\d+)(dp|dip|sp|px|pt|in|mm)");
    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,10}|0[xX][0-9A-Fa-f]{1,8}");
    private static final Pattern REFERENCE = Pattern.compile("@(?<type>[a-z]+)/(?<name>[A-Za-z_][\\w.]*)");
    private static final java.util.Set<String> DENSITIES = java.util.Set.of("-mdpi", "-hdpi", "-xhdpi", "-xxhdpi", "-xxxhdpi", "-nodpi");

    private final ResourceInventory inventory;
    @Nullable
    private final IconLibrary library;
    private final String iconColour;

    /**
     * @param iconColour the colour of library icons, {@code #RRGGBB} (null for black)
     */
    public PlanBuilder(ResourceInventory inventory, @Nullable IconLibrary library, @Nullable String iconColour) {
        this.inventory = inventory;
        this.library = library;
        this.iconColour = iconColour;
    }

    /** @param choices the chosen resolution for each missing resource, in the order to apply them */
    public Plan build(Map<MissingResource, Resolution> choices) {
        List<PlannedChange> changes = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        // (type/foldedName + qualifier) -> key of the resolution that claimed it
        Map<String, String> claimed = new HashMap<>();
        // values added by this plan, to check references and aliases against
        Map<String, String> addedValues = new LinkedHashMap<>();

        for (Map.Entry<MissingResource, Resolution> choice : choices.entrySet()) {
            MissingResource missing = choice.getKey();
            Resolution resolution = choice.getValue();
            if (!missing.fixable()) {
                conflicts.add(new Conflict(missing.key(), Conflict.Reason.INVALID, "This resource can't be fixed here"));
                continue;
            }
            PlannedChange change;
            try {
                change = plan(missing, resolution, addedValues);
            } catch (Refusal refusal) {
                conflicts.add(new Conflict(missing.key(), refusal.reason, refusal.getMessage()));
                continue;
            }
            String qualifier = qualifierOf(change);
            String claim = missing.type() + "/" + ResourceNames.foldedKey(missing.name()) + qualifier;
            // An image of the image manager is built into the app's drawables in a density folder of its own, so a
            // file of the same name would clash whatever qualifier it gets.
            boolean imageManagerHasIt = inventory.find(missing.type(), missing.name()).stream()
                    .anyMatch(d -> d.origin() == ResourceInventory.Origin.PROJECT_IMAGES);
            if (imageManagerHasIt || inventory.has(missing.type(), missing.name(), qualifier)) {
                conflicts.add(new Conflict(missing.key(), Conflict.Reason.EXISTS,
                        missing.type() + "/" + missing.name() + " already exists" + (qualifier.isEmpty() ? "" : " (" + qualifier.substring(1) + ")")));
                continue;
            }
            String other = claimed.get(claim);
            if (other != null) {
                conflicts.add(new Conflict(missing.key(), Conflict.Reason.DUPLICATE_IN_PLAN, "Another choice already adds " + other));
                continue;
            }
            if (change.kind() == PlannedChange.Kind.VALUE && inventory.unreadableValuesFiles().stream().anyMatch(p -> p.endsWith("/" + change.path()))) {
                conflicts.add(new Conflict(missing.key(), Conflict.Reason.UNREADABLE_VALUES, change.path() + " isn't valid XML, so it is not edited"));
                continue;
            }
            claimed.put(claim, missing.key());
            if (change.kind() == PlannedChange.Kind.VALUE) addedValues.put(missing.type() + "/" + missing.name(), change.entry().value());
            changes.add(change);
        }
        return new Plan(changes, conflicts);
    }

    private static String qualifierOf(PlannedChange change) {
        String folder = change.path().substring(0, change.path().indexOf('/'));
        int dash = folder.indexOf('-');
        return dash < 0 ? "" : folder.substring(dash);
    }

    private PlannedChange plan(MissingResource missing, Resolution resolution, Map<String, String> addedValues) throws Refusal {
        if (resolution instanceof Resolution.Reuse reuse) return reuse(missing, reuse.suggestion(), addedValues);
        if (resolution instanceof Resolution.Imported imported) return imported(missing, imported);
        if (resolution instanceof Resolution.Typed typed) return typed(missing, typed.text(), addedValues);
        throw new Refusal(Conflict.Reason.INVALID, "Unknown resolution");
    }

    // ---- reusing the project's own resources and library icons ----

    private PlannedChange reuse(MissingResource missing, Suggestion suggestion, Map<String, String> addedValues) throws Refusal {
        String type = missing.type();
        String name = missing.name();
        Definition source = suggestion.source();
        switch (suggestion.kind()) {
            case COPY_FILE -> {
                if (source == null || source.kind() != ResourceInventory.Kind.FILE) throw new Refusal(Conflict.Reason.SOURCE_MISSING, "Nothing to copy");
                byte[] bytes = read(new File(source.path()));
                String qualifier = copyQualifier(type, source.qualifier());
                String path = DestinationRules.path(type, qualifier, name, source.extension());
                return PlannedChange.file(missing.key(), path, bytes, "Copy of " + source.type() + source.qualifier() + "/" + new File(source.path()).getName(), List.of());
            }
            case COPY_VALUE -> {
                if (source == null || source.kind() != ResourceInventory.Kind.VALUE || source.value() == null) throw new Refusal(Conflict.Reason.SOURCE_MISSING, "Nothing to copy");
                return PlannedChange.value(missing.key(), "values/" + ResourceTypes.valuesFile(type), new ValuesFile.Entry(type, name, source.value()),
                        "Copy of the " + source.type() + source.qualifier() + " value");
            }
            case ALIAS -> {
                if (source == null) throw new Refusal(Conflict.Reason.SOURCE_MISSING, "Nothing to point to");
                if (!ResourceTypes.ALIAS_TYPES.contains(type)) throw new Refusal(Conflict.Reason.INVALID, "A " + type + " can't be an alias");
                return alias(missing, source.type(), source.name(), addedValues, source.value());
            }
            case MATERIAL_ICON -> {
                if (library == null || suggestion.iconName() == null || suggestion.iconStyle() == null) throw new Refusal(Conflict.Reason.SOURCE_MISSING, "The icon library isn't available");
                VectorModel model;
                try {
                    model = SvgToVector.convert(library.svg(suggestion.iconName(), suggestion.iconStyle()), iconColour == null ? "#FF000000" : iconColour);
                } catch (IOException | SvgToVector.Unsupported e) {
                    throw new Refusal(Conflict.Reason.SOURCE_MISSING, "The icon " + suggestion.iconName() + " can't be read: " + e.getMessage());
                }
                String qualifier = DestinationRules.defaultQualifier(type, FileInspector.Format.VECTOR_XML, 0, 0);
                return PlannedChange.file(missing.key(), DestinationRules.path(type, qualifier, name, ".xml"),
                        model.toXml().getBytes(StandardCharsets.UTF_8), "Material icon " + suggestion.iconName() + " (" + suggestion.iconStyle() + ")", List.of());
            }
        }
        throw new Refusal(Conflict.Reason.INVALID, "Unknown suggestion");
    }

    /** Density folders are kept, any-density folders only for mipmap, everything else (night, versions, ...) goes to the default. */
    private static String copyQualifier(String destinationType, String sourceQualifier) {
        if (DENSITIES.contains(sourceQualifier)) return sourceQualifier;
        if (destinationType.equals("mipmap") && (sourceQualifier.equals("-anydpi") || sourceQualifier.equals("-anydpi-v26"))) return sourceQualifier;
        return "";
    }

    private PlannedChange alias(MissingResource missing, String targetType, String targetName, Map<String, String> addedValues, String targetValue) throws Refusal {
        String type = missing.type();
        if (targetType.equals(type) && ResourceNames.foldedKey(targetName).equals(ResourceNames.foldedKey(missing.name()))) {
            throw new Refusal(Conflict.Reason.ALIAS_CYCLE, "A resource can't be an alias of itself");
        }
        if (targetValue != null) {
            java.util.regex.Matcher cycle = REFERENCE.matcher(targetValue.trim());
            if (cycle.matches() && cycle.group("type").equals(type)
                    && ResourceNames.foldedKey(cycle.group("name")).equals(ResourceNames.foldedKey(missing.name()))) {
                throw new Refusal(Conflict.Reason.ALIAS_CYCLE, targetName + " already points at " + missing.name());
            }
        }
        String reference = ValuesFile.reference(targetType, targetName);
        return PlannedChange.value(missing.key(), "values/" + ResourceTypes.valuesFile(type), new ValuesFile.Entry(type, missing.name(), reference), "Alias of " + reference);
    }

    // ---- files the person picked, icons they downloaded ----

    private PlannedChange imported(MissingResource missing, Resolution.Imported imported) throws Refusal {
        FileInspector.Inspection inspection = imported.inspection();
        if (!inspection.ok()) throw new Refusal(Conflict.Reason.REFUSED, inspection.problem());
        if (!ResourceTypes.isFile(missing.type())) throw new Refusal(Conflict.Reason.INVALID, "A file can't stand for a " + missing.type());
        String qualifier = imported.qualifier() != null ? imported.qualifier()
                : DestinationRules.defaultQualifier(missing.type(), inspection.format(), inspection.width(), inspection.height());
        String path = DestinationRules.path(missing.type(), qualifier, missing.name(), inspection.extension());
        return PlannedChange.file(missing.key(), path, inspection.content(), "File " + imported.label(), inspection.notes());
    }

    // ---- values typed in ----

    private PlannedChange typed(MissingResource missing, String text, Map<String, String> addedValues) throws Refusal {
        String type = missing.type();
        if (!ResourceTypes.isValue(type)) throw new Refusal(Conflict.Reason.INVALID, "A " + type + " is a file, not a value");
        String trimmed = text == null ? "" : text.trim();
        if (type.equals("string")) {
            if (trimmed.isEmpty()) throw new Refusal(Conflict.Reason.INVALID, "The text is empty");
            // a typed string is always literal text: @ and ? are escaped, so it never becomes a reference
            return PlannedChange.value(missing.key(), "values/" + ResourceTypes.valuesFile(type),
                    new ValuesFile.Entry(type, missing.name(), ValuesFile.textValue(text)), "Text you typed");
        }
        java.util.regex.Matcher reference = REFERENCE.matcher(trimmed);
        if (reference.matches()) {
            String targetType = reference.group("type");
            String targetName = reference.group("name");
            if (!targetType.equals(type)) throw new Refusal(Conflict.Reason.INVALID, "A " + type + " can only point at another " + type);
            boolean known = !inventory.find(targetType, targetName).isEmpty() || addedValues.containsKey(targetType + "/" + targetName);
            if (!known) throw new Refusal(Conflict.Reason.SOURCE_MISSING, "@" + targetType + "/" + targetName + " doesn't exist");
            return alias(missing, targetType, targetName, addedValues, null);
        }
        boolean valid = switch (type) {
            case "color" -> COLOR.matcher(trimmed).matches();
            case "dimen" -> DIMEN.matcher(trimmed).matches();
            case "integer" -> INTEGER.matcher(trimmed).matches();
            case "bool" -> trimmed.equals("true") || trimmed.equals("false");
            default -> false;
        };
        if (!valid) throw new Refusal(Conflict.Reason.INVALID, "\"" + trimmed + "\" isn't a valid " + type + (type.equals("color") ? " (use #RRGGBB or #AARRGGBB)" : type.equals("dimen") ? " (like 16dp or 14sp)" : ""));
        return PlannedChange.value(missing.key(), "values/" + ResourceTypes.valuesFile(type), new ValuesFile.Entry(type, missing.name(), trimmed), "Value you chose");
    }

    private static byte[] read(File file) throws Refusal {
        try {
            if (!file.isFile()) throw new Refusal(Conflict.Reason.SOURCE_MISSING, file.getName() + " isn't there any more");
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new Refusal(Conflict.Reason.SOURCE_MISSING, "Can't read " + file.getName() + ": " + e.getMessage());
        }
    }

    private static final class Refusal extends Exception {
        final Conflict.Reason reason;

        Refusal(Conflict.Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }
}
