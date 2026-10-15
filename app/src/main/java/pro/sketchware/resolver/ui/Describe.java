package pro.sketchware.resolver.ui;

import android.content.Context;

import java.io.File;

import pro.sketchware.R;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.ResourceInventory;
import pro.sketchware.resolver.Suggestion;

/** The words the resolver's screens use for suggestions, places and styles. */
final class Describe {
    private Describe() {
    }

    static String suggestionTitle(Context context, Suggestion suggestion) {
        ResourceInventory.Definition source = suggestion.source();
        return switch (suggestion.kind()) {
            case COPY_FILE -> context.getString(R.string.resolver_suggestion_copy_file,
                    source.type() + source.qualifier() + "/" + new File(source.path()).getName());
            case COPY_VALUE -> context.getString(R.string.resolver_suggestion_copy_value, source.type() + source.qualifier());
            case ALIAS -> context.getString(R.string.resolver_suggestion_alias, source.type(), source.name());
            case MATERIAL_ICON -> context.getString(R.string.resolver_suggestion_material, suggestion.iconName(), styleLabel(context, suggestion.iconStyle()));
        };
    }

    static String suggestionDetail(Context context, Suggestion suggestion) {
        return context.getString(suggestion.existing() ? R.string.resolver_suggestion_in_project : R.string.resolver_suggestion_offline);
    }

    static String styleLabel(Context context, String style) {
        return switch (style == null ? IconLibrary.DEFAULT_STYLE : style) {
            case "outline" -> context.getString(R.string.resolver_style_outline);
            case "round" -> context.getString(R.string.resolver_style_round);
            case "sharp" -> context.getString(R.string.resolver_style_sharp);
            case "twotone" -> context.getString(R.string.resolver_style_twotone);
            default -> context.getString(R.string.resolver_style_baseline);
        };
    }

    /** {@code layout/main.xml}: the file with the folder it is in. */
    static String shortPath(String path) {
        File file = new File(path);
        File parent = file.getParentFile();
        return parent == null || parent.getName().isEmpty() ? file.getName() : parent.getName() + "/" + file.getName();
    }

    static String reason(Context context, MissingResource problem) {
        MissingResource.Reason reason = problem.unfixableReason();
        if (reason == null) return "";
        return switch (reason) {
            case FRAMEWORK -> context.getString(R.string.resolver_reason_framework);
            case UNSUPPORTED_TYPE -> context.getString(R.string.resolver_reason_unsupported, problem.type());
            case INVALID_NAME -> context.getString(R.string.resolver_reason_invalid_name);
        };
    }

    static int valueHint(String type) {
        return switch (type) {
            case "color" -> R.string.resolver_value_hint_color;
            case "dimen" -> R.string.resolver_value_hint_dimen;
            case "integer" -> R.string.resolver_value_hint_integer;
            case "bool" -> R.string.resolver_value_hint_bool;
            default -> R.string.resolver_value_hint_string;
        };
    }
}
