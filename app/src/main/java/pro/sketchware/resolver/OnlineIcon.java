package pro.sketchware.resolver;

import androidx.annotation.NonNull;

/**
 * An icon found online.
 *
 * @param prefix      the icon set ({@code ic}, {@code mdi}, {@code tabler}, ...)
 * @param name        the icon's name in the set ({@code baseline-add}, {@code home})
 * @param setName     the set's full name, for display
 * @param license     the set's licence ({@code Apache-2.0}, {@code MIT}, {@code ISC})
 * @param licenseUrl  where the licence text is
 */
public record OnlineIcon(@NonNull String prefix, @NonNull String name, @NonNull String setName, @NonNull String license, @NonNull String licenseUrl) {
    /** {@code mdi:home}, as the service writes it. */
    public String id() {
        return prefix + ":" + name;
    }

    /** A resource name made from the icon: {@code ic_mdi_home}, {@code ic_ic_baseline_add} is avoided as {@code ic_baseline_add}. */
    public String suggestedResourceName() {
        String base = name.replace('-', '_');
        return prefix.equals("ic") ? "ic_" + base : "ic_" + prefix.replace('-', '_') + "_" + base;
    }
}
