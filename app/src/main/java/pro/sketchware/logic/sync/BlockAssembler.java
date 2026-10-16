package pro.sketchware.logic.sync;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a forest of {@link BNode} into the beans an event stores: ids, the chain of {@code nextBlock}, the first block of
 * each inner stack, and {@code @id} in the parameters that hold a reporter. The first statement comes first in the list,
 * which is what the generator and the editor start from.
 */
final class BlockAssembler {
    private final ArrayList<BlockBean> beans = new ArrayList<>();
    private final Set<Integer> used = new HashSet<>();
    private int next;

    private BlockAssembler(int firstFreeId) {
        next = firstFreeId;
    }

    /**
     * @param roots       the event's statements in order
     * @param firstFreeId an id larger than any id in use that a block with {@link BNode#reuseId} keeps
     */
    static ArrayList<BlockBean> assemble(List<BNode> roots, int firstFreeId) {
        BlockAssembler assembler = new BlockAssembler(firstFreeId);
        assembler.reserve(roots);
        assembler.chain(roots);
        return assembler.beans;
    }

    private void reserve(List<BNode> nodes) {
        for (BNode node : nodes) {
            if (node.reuseId != null) {
                try {
                    int id = Integer.parseInt(node.reuseId);
                    if (id >= 0) used.add(id);
                } catch (NumberFormatException ignored) {
                    node.reuseId = null;
                }
            }
            for (BNode.Param param : node.params) if (param.block() != null) reserve(List.of(param.block()));
            reserve(node.stack1);
            reserve(node.stack2);
        }
    }

    private int allocate(BNode node) {
        if (node.reuseId != null) return Integer.parseInt(node.reuseId);
        while (used.contains(next)) next++;
        used.add(next);
        return next++;
    }

    /** Adds the blocks of a chain and returns the id of its first, -1 for an empty chain. */
    private int chain(List<BNode> nodes) {
        if (nodes.isEmpty()) return -1;
        int[] ids = new int[nodes.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = allocate(nodes.get(i));
        for (int i = 0; i < ids.length; i++) {
            BNode node = nodes.get(i);
            BlockBean bean = bean(node, ids[i]);
            beans.add(bean);
            bean.parameters.clear();
            for (BNode.Param param : node.params) {
                if (param.block() != null) bean.parameters.add("@" + reporter(param.block()));
                else bean.parameters.add(param.text() == null ? "" : param.text());
            }
            bean.subStack1 = chain(node.stack1);
            bean.subStack2 = chain(node.stack2);
            bean.nextBlock = i + 1 < ids.length ? ids[i + 1] : -1;
        }
        return ids[0];
    }

    private int reporter(BNode node) {
        int id = allocate(node);
        BlockBean bean = bean(node, id);
        beans.add(bean);
        bean.parameters.clear();
        for (BNode.Param param : node.params) {
            if (param.block() != null) bean.parameters.add("@" + reporter(param.block()));
            else bean.parameters.add(param.text() == null ? "" : param.text());
        }
        return id;
    }

    private static BlockBean bean(BNode node, int id) {
        BlockBean bean = new BlockBean(String.valueOf(id), node.spec, node.type, node.typeName, node.opCode);
        bean.subStack1 = -1;
        bean.subStack2 = -1;
        bean.nextBlock = -1;
        return bean;
    }
}
