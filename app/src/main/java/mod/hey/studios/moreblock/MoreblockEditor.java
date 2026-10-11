package mod.hey.studios.moreblock;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.eC;

/**
 * Changes an existing MoreBlock (name, return type, parameters) and updates every place it is used.
 *
 * <p>A MoreBlock is stored as a {@code (nameWithType, spec)} pair, e.g. {@code ("uniqueID[String]",
 * "uniqueID[String] %s.prefix %d.length")}, its body under the event key {@code nameWithType + "_moreBlock"},
 * and every call as a {@code definedFunc} block whose spec is the MoreBlock spec without its type.
 *
 * <p>Calls keep the value of each parameter whose name and type are unchanged; values of removed parameters are
 * dropped, together with the blocks that were plugged into them.
 */
public final class MoreblockEditor {

    /** A parameter of a MoreBlock spec: {@code %s.key} has type {@code "s"} and name {@code "key"}. */
    public record Param(String type, String name) {
    }

    private MoreblockEditor() {
    }

    /** @return the parameters of a MoreBlock spec, in order (labels are skipped). */
    public static List<Param> params(String spec) {
        List<Param> result = new ArrayList<>();
        for (Pair<String, String> part : parts(spec)) {
            if (!part.first.equals("t")) {
                result.add(new Param(part.first, part.second));
            }
        }
        return result;
    }

    /**
     * @return the parts following the name of a MoreBlock spec, as used by the MoreBlock builder:
     * {@code ("t", label)} for labels, {@code (type, name)} for parameters.
     */
    public static ArrayList<Pair<String, String>> parts(String spec) {
        ArrayList<Pair<String, String>> result = new ArrayList<>();
        String rest = spec.substring(ReturnMoreblockManager.getMbNameWithTypeFromSpec(spec).length()).trim();
        if (rest.isEmpty()) {
            return result;
        }
        for (String token : rest.split(" +")) {
            int lastDot = token.lastIndexOf('.');
            if (token.startsWith("%") && lastDot > 1) {
                result.add(new Pair<>(token.substring(1, lastDot), token.substring(lastDot + 1)));
            } else {
                result.add(new Pair<>("t", token));
            }
        }
        return result;
    }

    /** @return the spec calls of this MoreBlock use: its spec without the return type. */
    public static String callSpec(String spec) {
        return ReturnMoreblockManager.getMbName(spec);
    }

    /** @return whether a block of this activity calls the MoreBlock with the given spec. */
    public static boolean isUsed(eC logic, String javaName, String spec) {
        String callSpec = callSpec(spec);
        for (ArrayList<BlockBean> blocks : logic.b(javaName).values()) {
            if (blocks == null) continue;
            for (BlockBean block : blocks) {
                if ("definedFunc".equals(block.opCode) && callSpec.equals(block.spec)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces the MoreBlock {@code oldName} of this activity with {@code (newName, newSpec)}: the definition keeps
     * its position and body, and every call is rewritten to the new spec.
     *
     * @return how many calls were updated
     */
    public static int apply(eC logic, String javaName, String oldName, String newName, String newSpec) {
        ArrayList<Pair<String, String>> moreBlocks = logic.g.get(javaName);
        if (moreBlocks == null) {
            return 0;
        }
        String oldSpec = null;
        for (int i = 0; i < moreBlocks.size(); i++) {
            if (moreBlocks.get(i).first.equals(oldName)) {
                oldSpec = moreBlocks.get(i).second;
                moreBlocks.set(i, new Pair<>(newName, newSpec));
                break;
            }
        }
        if (oldSpec == null) {
            return 0;
        }

        HashMap<String, ArrayList<BlockBean>> events = logic.d.get(javaName);
        if (events == null) {
            return 0;
        }
        ArrayList<BlockBean> body = events.remove(oldName + "_moreBlock");
        if (body != null) {
            events.put(newName + "_moreBlock", body);
        }

        String oldCallSpec = callSpec(oldSpec);
        String newCallSpec = callSpec(newSpec);
        List<Param> oldParams = params(oldSpec);
        List<Param> newParams = params(newSpec);
        int updated = 0;
        for (ArrayList<BlockBean> blocks : events.values()) {
            if (blocks == null) continue;
            Set<String> dropped = new HashSet<>();
            for (BlockBean block : blocks) {
                if ("definedFunc".equals(block.opCode) && oldCallSpec.equals(block.spec)) {
                    rewriteCall(block, newCallSpec, oldParams, newParams, dropped);
                    updated++;
                }
            }
            if (!dropped.isEmpty()) {
                removeBlocks(blocks, dropped);
            }
        }
        return updated;
    }

    /** Points a call at the new spec, keeping the values of parameters whose name and type didn't change. */
    static void rewriteCall(BlockBean call, String newCallSpec, List<Param> oldParams, List<Param> newParams,
                            Set<String> droppedBlockIds) {
        ArrayList<String> oldValues = call.parameters != null ? call.parameters : new ArrayList<>();
        boolean[] kept = new boolean[oldValues.size()];
        ArrayList<String> newValues = new ArrayList<>();
        for (Param param : newParams) {
            int index = oldParams.indexOf(param);
            if (index >= 0 && index < oldValues.size()) {
                newValues.add(oldValues.get(index));
                kept[index] = true;
            } else {
                newValues.add("");
            }
        }
        for (int i = 0; i < oldValues.size(); i++) {
            String value = oldValues.get(i);
            if (!kept[i] && value != null && value.startsWith("@")) {
                droppedBlockIds.add(value.substring(1));
            }
        }
        call.spec = newCallSpec;
        call.parameters = newValues;
        // Recomputed from the new spec on next use.
        call.paramClassInfo = null;
        call.classInfo = null;
    }

    /** Removes the given blocks and everything attached to them. */
    static void removeBlocks(ArrayList<BlockBean> blocks, Set<String> rootIds) {
        Map<String, BlockBean> byId = new HashMap<>();
        for (BlockBean block : blocks) {
            byId.put(block.id, block);
        }
        Set<String> toRemove = new HashSet<>();
        ArrayList<String> pending = new ArrayList<>(rootIds);
        while (!pending.isEmpty()) {
            String id = pending.remove(pending.size() - 1);
            BlockBean block = byId.get(id);
            if (block == null || !toRemove.add(id)) continue;
            if (block.parameters != null) {
                for (String value : block.parameters) {
                    if (value != null && value.startsWith("@")) pending.add(value.substring(1));
                }
            }
            if (block.subStack1 >= 0) pending.add(String.valueOf(block.subStack1));
            if (block.subStack2 >= 0) pending.add(String.valueOf(block.subStack2));
            if (block.nextBlock >= 0) pending.add(String.valueOf(block.nextBlock));
        }
        blocks.removeIf(block -> toRemove.contains(block.id));
    }
}
