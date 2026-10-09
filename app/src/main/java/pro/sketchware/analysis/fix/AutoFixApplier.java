package pro.sketchware.analysis.fix;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ViewBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.jC;
import pro.sketchware.activities.snapshots.AutoSnapshots;
import pro.sketchware.activities.snapshots.ProjectSnapshots;

/**
 * Applies an {@link AutoFix.Plan} to the open project's data. A snapshot of the saved project is taken first, so the
 * whole fix can be undone from the Snapshots screen; nothing is changed if the snapshot cannot be taken. The editor
 * must save the project afterwards for the changes to reach the disk.
 */
public final class AutoFixApplier {
    /** @param missing changes whose widget or event is no longer in the project */
    public record Result(int applied, int missing, ProjectSnapshots.Snapshot snapshot) {
    }

    private AutoFixApplier() {
    }

    public static Result apply(String scId, AutoFix.Plan plan) throws IOException {
        ProjectSnapshots.Snapshot snapshot = AutoSnapshots.forProject(scId).create(ProjectSnapshots.Kind.MANUAL);
        int applied = 0;
        int missing = 0;
        for (AutoFix.WidgetChange change : plan.widgetChanges()) {
            ViewBean bean = jC.a(scId).c(change.screen() + ".xml", change.id());
            if (bean == null || !apply(bean, change)) {
                missing++;
            } else {
                applied++;
            }
        }
        for (AutoFix.BlockRemoval removal : plan.blockRemovals()) {
            HashMap<String, ArrayList<BlockBean>> events = jC.a(scId).b(removal.javaName());
            ArrayList<BlockBean> blocks = events == null ? null : events.get(removal.eventKey());
            if (blocks == null) {
                missing += removal.blockIds().size();
                continue;
            }
            int before = blocks.size();
            blocks.removeIf(block -> removal.blockIds().contains(block.id));
            applied += before - blocks.size();
            missing += removal.blockIds().size() - (before - blocks.size());
        }
        return new Result(applied, missing, snapshot);
    }

    private static boolean apply(ViewBean bean, AutoFix.WidgetChange change) {
        int value = change.value();
        switch (change.field()) {
            case WIDTH -> bean.layout.width = value;
            case HEIGHT -> bean.layout.height = value;
            case MARGIN_LEFT -> bean.layout.marginLeft = value;
            case MARGIN_RIGHT -> bean.layout.marginRight = value;
            case PADDING_LEFT -> bean.layout.paddingLeft = value;
            case PADDING_RIGHT -> bean.layout.paddingRight = value;
            case TEXT_SIZE -> {
                if (bean.text == null) return false;
                bean.text.textSize = value;
            }
            case TEXT_COLOR -> {
                if (bean.text == null) return false;
                bean.text.textColor = value;
            }
            case CONTENT_DESCRIPTION -> {
                String inject = bean.inject == null ? "" : bean.inject;
                if (inject.contains("contentDescription")) return true;
                String picture = bean.image == null ? null : bean.image.resName;
                String description = AutoFix.describe(picture, bean.id).replace("\"", "'");
                bean.inject = (inject.isBlank() ? "" : inject.stripTrailing() + "\n") + "android:contentDescription=\"" + description + "\"";
            }
        }
        return true;
    }
}
