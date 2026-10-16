package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** What a conversion changes in the event's blocks, counted by block id so that a moved or edited block is not "new". */
public record ChangeSummary(int kept, int changed, int added, int removed, @NonNull List<String> lines) {
    /** At most this many lines describe the changes; the counts always cover all of them. */
    private static final int MAX_LINES = 40;

    public boolean isEmpty() {
        return changed == 0 && added == 0 && removed == 0;
    }

    public static ChangeSummary compare(List<BlockBean> before, List<BlockBean> after) {
        Map<String, BlockBean> old = new HashMap<>();
        for (BlockBean bean : before) old.put(bean.id, bean);
        Map<String, BlockBean> fresh = new HashMap<>();
        for (BlockBean bean : after) fresh.put(bean.id, bean);
        int kept = 0;
        int changed = 0;
        int added = 0;
        int removed = 0;
        List<String> lines = new ArrayList<>();
        for (BlockBean bean : after) {
            BlockBean was = old.get(bean.id);
            if (was == null) {
                added++;
                line(lines, "+ " + describe(bean));
            } else if (same(was, bean)) {
                kept++;
            } else {
                changed++;
                line(lines, "~ " + describe(bean));
            }
        }
        for (BlockBean bean : before) {
            if (!fresh.containsKey(bean.id)) {
                removed++;
                line(lines, "- " + describe(bean));
            }
        }
        return new ChangeSummary(kept, changed, added, removed, lines);
    }

    private static void line(List<String> lines, String text) {
        if (lines.size() < MAX_LINES) lines.add(text);
    }

    private static boolean same(BlockBean a, BlockBean b) {
        return Objects.equals(a.opCode, b.opCode) && Objects.equals(a.spec, b.spec) && Objects.equals(a.parameters, b.parameters)
                && a.nextBlock == b.nextBlock && a.subStack1 == b.subStack1 && a.subStack2 == b.subStack2;
    }

    private static String describe(BlockBean bean) {
        String name = bean.opCode.equals("getVar") || bean.opCode.equals("getArg") ? bean.opCode + " " + bean.spec : bean.opCode;
        if (bean.opCode.equals("addSourceDirectly") && !bean.parameters.isEmpty()) {
            String code = bean.parameters.get(0).replace('\n', ' ').trim();
            return "Java: " + (code.length() > 50 ? code.substring(0, 50) + "…" : code);
        }
        return name;
    }
}
