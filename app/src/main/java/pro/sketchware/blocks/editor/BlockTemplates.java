package pro.sketchware.blocks.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Starting points for a block: a type, a spec and the Java it generates. Names in the Java are fully qualified, so a
 * template doesn't depend on the imports of the generated file.
 */
public final class BlockTemplates {
    public enum Group {JAVA, VIEW, ADMOB, FIREBASE, INTENT, CUSTOM}

    /** @param needs the library the generated app needs for it, or null */
    public record Template(String id, Group group, String title, String type, String spec, String code, String needs) {
    }

    public static final List<Template> ALL = List.of(
            new Template("java.toast", Group.JAVA, "Show a toast", "regular", "show toast %s",
                    "android.widget.Toast.makeText(getApplicationContext(), %s, android.widget.Toast.LENGTH_SHORT).show();", null),
            new Template("java.length", Group.JAVA, "Length of a text", "d", "length of %s",
                    "String.valueOf(%s).length()", null),
            new Template("java.empty", Group.JAVA, "Text is empty", "b", "%s is empty",
                    "String.valueOf(%s).isEmpty()", null),
            new Template("view.visible", Group.VIEW, "Show or hide a view", "regular", "%m.view visible %b",
                    "%1$s.setVisibility(%2$s ? android.view.View.VISIBLE : android.view.View.GONE);", null),
            new Template("view.text", Group.VIEW, "Text of a TextView", "s", "text of %m.textview",
                    "%s.getText().toString()", null),
            new Template("intent.url", Group.INTENT, "Open a web page", "regular", "open web page %s",
                    "startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(%s)));", null),
            new Template("admob.init", Group.ADMOB, "Initialize AdMob", "regular", "initialize ads",
                    "com.google.android.gms.ads.MobileAds.initialize(getApplicationContext());", "AdMob"),
            new Template("firebase.signout", Group.FIREBASE, "Sign out of Firebase", "regular", "sign out",
                    "com.google.firebase.auth.FirebaseAuth.getInstance().signOut();", "Firebase Authentication"),
            new Template("custom.blank", Group.CUSTOM, "Empty block", "regular", "", "", null));

    private BlockTemplates() {
    }

    public static String groupLabel(Group group) {
        return switch (group) {
            case JAVA -> "Java";
            case VIEW -> "View";
            case ADMOB -> "AdMob";
            case FIREBASE -> "Firebase";
            case INTENT -> "Intent";
            case CUSTOM -> "Custom";
        };
    }

    /**
     * The templates that fit the block being edited: while the block is empty every template does; once it has
     * a spec or code, only the templates of its own type (a template never changes the type of a finished block).
     * The empty template is always offered.
     */
    public static List<Template> compatible(String currentType, boolean blockIsEmpty) {
        BlockShapes.Shape shape = BlockShapes.of(currentType);
        String current = shape == null ? "" : shape.type();
        List<Template> result = new ArrayList<>();
        for (Template template : ALL) {
            if (blockIsEmpty || template.group() == Group.CUSTOM || template.type().equals(current)) result.add(template);
        }
        return result;
    }

    public static Template byId(String id) {
        for (Template template : ALL) {
            if (template.id().equals(id)) return template;
        }
        return null;
    }
}
