package pro.sketchware.evolve;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits the blocks of an open event into the stacks the user sees on the canvas, so any of them (whatever its
 * palette: control, list, view, component, more block call...) can be shared. Pure Java.
 */
public final class BlockStacks {

    /** One stack: its first block and a copy of every block that hangs off it, in the order of the canvas. */
    public record Stack(BlockBean root, ArrayList<BlockBean> blocks) {
    }

    private BlockStacks() {
    }

    /**
     * @return the stacks of {@code canvas}. A block is a root when no other block continues into it, nests it or
     * takes it as a parameter. The beans are cloned: sharing never changes the canvas.
     */
    public static List<Stack> split(List<BlockBean> canvas) {
        Map<String, BlockBean> byId = new HashMap<>();
        for (BlockBean bean : canvas) {
            if (bean != null && bean.id != null) {
                byId.put(bean.id, bean);
            }
        }
        Set<String> referenced = new HashSet<>();
        for (BlockBean bean : byId.values()) {
            for (String child : children(bean)) {
                referenced.add(child);
            }
        }
        List<Stack> stacks = new ArrayList<>();
        for (BlockBean bean : canvas) {
            if (bean == null || bean.id == null || referenced.contains(bean.id)) {
                continue;
            }
            Set<String> members = new HashSet<>();
            collect(bean, byId, members);
            ArrayList<BlockBean> blocks = new ArrayList<>();
            for (BlockBean candidate : canvas) {
                if (candidate != null && candidate.id != null && members.contains(candidate.id)) {
                    blocks.add(candidate.clone());
                }
            }
            stacks.add(new Stack(bean, blocks));
        }
        return stacks;
    }

    private static void collect(BlockBean bean, Map<String, BlockBean> byId, Set<String> members) {
        if (!members.add(bean.id)) {
            return;
        }
        for (String child : children(bean)) {
            BlockBean next = byId.get(child);
            if (next != null) {
                collect(next, byId, members);
            }
        }
    }

    /** The ids of the blocks {@code bean} leads to: next block, both nests, and blocks plugged into parameters. */
    private static List<String> children(BlockBean bean) {
        List<String> ids = new ArrayList<>();
        if (bean.nextBlock >= 0) ids.add(String.valueOf(bean.nextBlock));
        if (bean.subStack1 >= 0) ids.add(String.valueOf(bean.subStack1));
        if (bean.subStack2 >= 0) ids.add(String.valueOf(bean.subStack2));
        if (bean.parameters != null) {
            for (String parameter : bean.parameters) {
                if (parameter != null && parameter.length() > 1 && parameter.charAt(0) == '@') {
                    ids.add(parameter.substring(1));
                }
            }
        }
        return ids;
    }
}
