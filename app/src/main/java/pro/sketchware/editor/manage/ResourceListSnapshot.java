package pro.sketchware.editor.manage;

import com.besome.sketch.beans.ProjectResourceBean;

import java.util.ArrayList;
import java.util.List;

/**
 * What a project's image, sound or font list looked like when its manager opened, so leaving the manager
 * without changing anything skips the slow save (rewriting the resource list and the project's view and logic
 * data). Pure Java so it can be unit tested.
 * <p>
 * The managers edit the project's own resource objects in place, so the list is captured as text, not kept
 * as references.
 */
public final class ResourceListSnapshot {

    private final ArrayList<String> entries;

    private ResourceListSnapshot(ArrayList<String> entries) {
        this.entries = entries;
    }

    public static ResourceListSnapshot of(List<ProjectResourceBean> resources) {
        ArrayList<String> entries = new ArrayList<>();
        if (resources != null) {
            for (ProjectResourceBean resource : resources) {
                entries.add(entry(resource));
            }
        }
        return new ResourceListSnapshot(entries);
    }

    /** Rebuilds a snapshot saved with {@link #toList()} (screen rotation), or returns null. */
    public static ResourceListSnapshot fromList(ArrayList<String> saved) {
        return saved == null ? null : new ResourceListSnapshot(new ArrayList<>(saved));
    }

    public ArrayList<String> toList() {
        return new ArrayList<>(entries);
    }

    /**
     * @return true when {@code current} has a new or edited resource, or differs from the snapshot in content
     * or order (an added, removed, renamed, rotated or moved resource).
     */
    public boolean hasChanges(List<ProjectResourceBean> current) {
        if (current == null) {
            return !entries.isEmpty();
        }
        for (ProjectResourceBean resource : current) {
            if (resource.isNew || resource.isEdited) {
                return true;
            }
        }
        return !entries.equals(of(current).entries);
    }

    private static String entry(ProjectResourceBean resource) {
        return resource.resName + '\u0000' + resource.resFullName + '\u0000' + resource.rotate + '\u0000'
                + resource.flipHorizontal + '\u0000' + resource.flipVertical;
    }
}
