package pro.sketchware.editor.manage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.ProjectResourceBean;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ResourceListSnapshotTest {

    private static ProjectResourceBean bean(String name, String file) {
        ProjectResourceBean bean = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, name, file);
        bean.flipHorizontal = 1;
        bean.flipVertical = 1;
        return bean;
    }

    private static List<ProjectResourceBean> list(ProjectResourceBean... beans) {
        List<ProjectResourceBean> list = new ArrayList<>();
        for (ProjectResourceBean bean : beans) list.add(bean);
        return list;
    }

    @Test
    public void unchangedListHasNoChanges() {
        ProjectResourceBean a = bean("logo", "logo.png");
        ProjectResourceBean b = bean("bg", "bg.png");
        ResourceListSnapshot snapshot = ResourceListSnapshot.of(list(a, b));
        assertFalse(snapshot.hasChanges(list(a, b)));
        assertFalse(ResourceListSnapshot.of(new ArrayList<>()).hasChanges(new ArrayList<>()));
    }

    @Test
    public void addedRemovedAndMovedResourcesAreChanges() {
        ProjectResourceBean a = bean("logo", "logo.png");
        ProjectResourceBean b = bean("bg", "bg.png");
        ResourceListSnapshot snapshot = ResourceListSnapshot.of(list(a, b));
        assertTrue(snapshot.hasChanges(list(a)));
        assertTrue(snapshot.hasChanges(list(b, a)));
        assertTrue(snapshot.hasChanges(list(a, b, bean("icon", "icon.png"))));
    }

    /** The managers edit the project's own objects in place: the snapshot must not follow those edits. */
    @Test
    public void inPlaceEditsAreChanges() {
        ProjectResourceBean a = bean("logo", "logo.png");
        List<ProjectResourceBean> current = list(a);
        ResourceListSnapshot snapshot = ResourceListSnapshot.of(current);
        a.rotate = 90;
        assertTrue(snapshot.hasChanges(current));
        a.rotate = 0;
        a.resName = "brand";
        assertTrue(snapshot.hasChanges(current));
    }

    @Test
    public void newOrEditedFlagsAreChanges() {
        ProjectResourceBean a = bean("logo", "logo.png");
        ResourceListSnapshot snapshot = ResourceListSnapshot.of(list(a));
        a.isEdited = true;
        assertTrue(snapshot.hasChanges(list(a)));
        a.isEdited = false;
        a.isNew = true;
        assertTrue(snapshot.hasChanges(list(a)));
    }

    @Test
    public void survivesSavingAndRestoring() {
        ProjectResourceBean a = bean("logo", "logo.png");
        ResourceListSnapshot restored = ResourceListSnapshot.fromList(ResourceListSnapshot.of(list(a)).toList());
        assertFalse(restored.hasChanges(list(a)));
        assertNull(ResourceListSnapshot.fromList(null));
    }
}
