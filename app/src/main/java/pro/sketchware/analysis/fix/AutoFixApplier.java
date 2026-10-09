package pro.sketchware.analysis.fix;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.jC;
import pro.sketchware.activities.snapshots.AutoSnapshots;
import pro.sketchware.activities.snapshots.ProjectSnapshots;
import pro.sketchware.utility.FilePathUtil;

/**
 * Applies an {@link AutoFix.Plan} to the open project's data. A snapshot of the saved project is taken first, so the
 * whole fix can be undone from the Snapshots screen; nothing is changed if the snapshot cannot be taken. The editor
 * must save the project afterwards for the changes to reach the disk.
 */
public final class AutoFixApplier {
    /**
     * @param missing        changes whose widget or event is no longer in the project
     * @param stringsProblem why typed-in text was not moved (strings.xml unreadable), or null
     */
    public record Result(int applied, int missing, ProjectSnapshots.Snapshot snapshot, String stringsProblem) {
    }

    private AutoFixApplier() {
    }

    public static Result apply(String scId, AutoFix.Plan plan) throws IOException {
        ProjectSnapshots.Snapshot snapshot = AutoSnapshots.forProject(scId).create(ProjectSnapshots.Kind.MANUAL);
        int applied = 0;
        int missing = 0;
        String stringsProblem = null;
        List<AutoFix.WidgetChange> texts = plan.widgetChanges().stream()
                .filter(c -> c.field() == AutoFix.Field.TEXT_TO_RESOURCE).toList();
        if (!texts.isEmpty()) {
            try {
                int[] counts = moveTexts(scId, texts);
                applied += counts[0];
                missing += counts[1];
            } catch (StringResources.InvalidStringsFile e) {
                stringsProblem = e.getMessage();
                missing += texts.size();
            }
        }
        for (AutoFix.WidgetChange change : plan.widgetChanges()) {
            if (change.field() == AutoFix.Field.TEXT_TO_RESOURCE) continue;
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
        return new Result(applied, missing, snapshot, stringsProblem);
    }

    /**
     * Adds each widget's typed-in text to the project's strings.xml (reusing a key that already holds the same text)
     * and sets the widget's text to {@code @string/key}. The file is written once, before the widgets change.
     *
     * @return how many widgets changed, and how many were skipped
     */
    private static int[] moveTexts(String scId, List<AutoFix.WidgetChange> changes) throws IOException, StringResources.InvalidStringsFile {
        File file = new File(new FilePathUtil().getPathResource(scId), "values" + File.separator + "strings.xml");
        String xml = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        Map<String, String> existing = StringResources.parse(xml);
        Map<String, String> added = new LinkedHashMap<>();
        List<Map.Entry<ViewBean, String>> keys = new ArrayList<>();
        int missing = 0;
        for (AutoFix.WidgetChange change : changes) {
            ViewBean bean = jC.a(scId).c(change.screen() + ".xml", change.id());
            String text = bean == null || bean.text == null ? null : bean.text.text;
            if (text == null || text.isEmpty() || text.startsWith("@")) {
                missing++;
                continue;
            }
            String key = added.entrySet().stream().filter(e -> e.getValue().equals(text)).map(Map.Entry::getKey).findFirst()
                    .orElseGet(() -> StringResources.keyFor(text, existing, added.keySet()));
            if (!existing.containsKey(key)) added.put(key, text);
            keys.add(Map.entry(bean, key));
        }
        if (!added.isEmpty()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Couldn't create " + parent);
            Files.write(file.toPath(), StringResources.append(xml, added).getBytes(StandardCharsets.UTF_8));
        }
        for (Map.Entry<ViewBean, String> entry : keys) entry.getKey().text.text = "@string/" + entry.getValue();
        return new int[]{keys.size(), missing};
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
            case TEXT_TO_RESOURCE -> {
                return false;
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
