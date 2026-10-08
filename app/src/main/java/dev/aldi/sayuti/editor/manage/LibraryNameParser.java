package dev.aldi.sayuti.editor.manage;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the artifact and version out of a local library's folder name, like "zoomage_V_1.3.1" or "appcompat-v1.6.1". */
public class LibraryNameParser {
    private static final Pattern NAME_AND_VERSION = Pattern.compile("^(.+?)[-_ ]+[vV]?[-_ ]*(\\d+(?:[.\\-_]\\w+)*)$");

    public final String artifactId;
    @Nullable
    public final String version;

    private LibraryNameParser(String artifactId, @Nullable String version) {
        this.artifactId = artifactId;
        this.version = version;
    }

    public static LibraryNameParser parse(String folderName) {
        Matcher matcher = NAME_AND_VERSION.matcher(folderName.trim());
        if (matcher.matches()) {
            return new LibraryNameParser(matcher.group(1).replaceAll("[-_ ]+$", ""), matcher.group(2));
        }
        return new LibraryNameParser(folderName.trim(), null);
    }
}
