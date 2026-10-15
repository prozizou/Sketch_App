package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

/** A set of icons that is on the device: usable with no connection. */
public interface IconLibrary {
    /** The style used when none is asked for: the classic Material "baseline" look. */
    String DEFAULT_STYLE = "baseline";

    /**
     * @param name   the icon's name ({@code arrow_back})
     * @param styles the styles it comes in ({@code baseline}, {@code outline}, ...)
     */
    record IconInfo(@NonNull String name, @NonNull List<String> styles) {
    }

    /** @return the icons whose name matches {@code query}, best first, at most {@code limit} */
    List<IconInfo> search(String query, int limit);

    /** @return the icon with exactly this name, or null */
    @Nullable
    IconInfo find(String name);

    /** @return the SVG text of an icon in a style */
    String svg(String name, String style) throws IOException;
}
