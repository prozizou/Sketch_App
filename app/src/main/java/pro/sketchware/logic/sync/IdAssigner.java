package pro.sketchware.logic.sync;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the identity of blocks across a conversion. A block that is still in the event (the same statement, even if it
 * moved or if only a parameter changed) gets the id it had, so ids stay stable, nothing is duplicated, and only the
 * blocks that are really new or gone differ from before.
 * <p>
 * The match is made level by level: first statements that are identical, in order; then identical ones that moved;
 * then, between identical ones, statements of the same kind (a changed parameter is the same block); finally identical
 * subtrees anywhere in the event (a statement moved into or out of a loop).
 */
final class IdAssigner {
    /** A block of the previous event with what it holds, as a tree. */
    private static final class Old {
        final BlockBean bean;
        final List<Old> params = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        final List<Old> stack1 = new ArrayList<>();
        final List<Old> stack2 = new ArrayList<>();
        String signature;
        boolean taken;

        Old(BlockBean bean) {
            this.bean = bean;
        }
    }

    private final Map<String, BlockBean> byId = new HashMap<>();
    private final Map<Old, Boolean> all = new IdentityHashMap<>();
    private final Map<BNode, String> signatures = new IdentityHashMap<>();

    private IdAssigner(List<BlockBean> previous) {
        for (BlockBean bean : previous) if (bean != null && bean.id != null) byId.put(bean.id, bean);
    }

    static void assign(List<BNode> roots, List<BlockBean> previous) {
        if (previous.isEmpty()) return;
        IdAssigner assigner = new IdAssigner(previous);
        List<Old> oldRoots = assigner.oldChain(previous.get(0), new HashSet<>());
        assigner.align(roots, oldRoots);
        assigner.moved(roots);
    }

    /** The blocks of {@code previous} that the event's code doesn't reach: they are kept as they are. */
    static List<BlockBean> loose(List<BlockBean> previous) {
        if (previous.isEmpty()) return List.of();
        Map<String, BlockBean> byId = new HashMap<>();
        for (BlockBean bean : previous) if (bean != null && bean.id != null) byId.put(bean.id, bean);
        Set<String> reached = new HashSet<>();
        reach(previous.get(0), byId, reached);
        List<BlockBean> loose = new ArrayList<>();
        for (BlockBean bean : previous) if (bean != null && bean.id != null && !reached.contains(bean.id)) loose.add(bean);
        return loose;
    }

    private static void reach(@Nullable BlockBean bean, Map<String, BlockBean> byId, Set<String> reached) {
        ArrayDeque<BlockBean> pending = new ArrayDeque<>();
        if (bean != null) pending.add(bean);
        while (!pending.isEmpty()) {
            BlockBean current = pending.poll();
            if (!reached.add(current.id)) continue;
            for (String parameter : current.parameters) {
                if (parameter != null && parameter.startsWith("@")) addIfPresent(byId.get(parameter.substring(1)), pending);
            }
            if (current.subStack1 >= 0) addIfPresent(byId.get(String.valueOf(current.subStack1)), pending);
            if (current.subStack2 >= 0) addIfPresent(byId.get(String.valueOf(current.subStack2)), pending);
            if (current.nextBlock >= 0) addIfPresent(byId.get(String.valueOf(current.nextBlock)), pending);
        }
    }

    private static void addIfPresent(@Nullable BlockBean bean, ArrayDeque<BlockBean> pending) {
        if (bean != null) pending.add(bean);
    }

    /** An id larger than any id in {@code previous}. */
    static int firstFreeId(List<BlockBean> previous) {
        int max = 0;
        for (BlockBean bean : previous) {
            try {
                max = Math.max(max, Integer.parseInt(bean.id));
            } catch (NumberFormatException ignored) {
                // not a number: not an id the editor made
            }
        }
        return max + 1;
    }

    // ---- the previous event as trees

    private List<Old> oldChain(@Nullable BlockBean first, Set<String> seen) {
        List<Old> chain = new ArrayList<>();
        BlockBean bean = first;
        while (bean != null && seen.add(bean.id)) {
            Old old = new Old(bean);
            all.put(old, Boolean.TRUE);
            for (String parameter : bean.parameters) {
                if (parameter != null && parameter.startsWith("@") && byId.containsKey(parameter.substring(1)) && seen.add(parameter.substring(1))) {
                    Old inner = oldReporter(byId.get(parameter.substring(1)), seen);
                    old.params.add(inner);
                    old.texts.add(null);
                } else {
                    old.params.add(null);
                    old.texts.add(parameter == null ? "" : parameter);
                }
            }
            if (bean.subStack1 >= 0) old.stack1.addAll(oldChain(byId.get(String.valueOf(bean.subStack1)), seen));
            if (bean.subStack2 >= 0) old.stack2.addAll(oldChain(byId.get(String.valueOf(bean.subStack2)), seen));
            old.signature = signature(old);
            chain.add(old);
            bean = bean.nextBlock >= 0 ? byId.get(String.valueOf(bean.nextBlock)) : null;
        }
        return chain;
    }

    private Old oldReporter(BlockBean bean, Set<String> seen) {
        Old old = new Old(bean);
        all.put(old, Boolean.TRUE);
        for (String parameter : bean.parameters) {
            if (parameter != null && parameter.startsWith("@") && byId.containsKey(parameter.substring(1)) && seen.add(parameter.substring(1))) {
                old.params.add(oldReporter(byId.get(parameter.substring(1)), seen));
                old.texts.add(null);
            } else {
                old.params.add(null);
                old.texts.add(parameter == null ? "" : parameter);
            }
        }
        old.signature = signature(old);
        return old;
    }

    private static String identity(BlockBean bean) {
        boolean named = bean.opCode.equals("getVar") || bean.opCode.equals("getArg") || bean.opCode.equals("getResStr") || bean.opCode.equals("definedFunc");
        return named ? bean.opCode + ":" + bean.spec : bean.opCode;
    }

    private static String signature(Old old) {
        StringBuilder out = new StringBuilder(identity(old.bean)).append('(');
        for (int i = 0; i < old.params.size(); i++) {
            if (i > 0) out.append(',');
            out.append(old.params.get(i) != null ? old.params.get(i).signature : "'" + old.texts.get(i) + "'");
        }
        out.append("){");
        for (Old child : old.stack1) out.append(child.signature).append(';');
        out.append('|');
        for (Old child : old.stack2) out.append(child.signature).append(';');
        return out.append('}').toString();
    }

    private String signature(BNode node) {
        String known = signatures.get(node);
        if (known != null) return known;
        boolean named = node.opCode.equals("getVar") || node.opCode.equals("getArg") || node.opCode.equals("getResStr") || node.opCode.equals("definedFunc");
        StringBuilder out = new StringBuilder(named ? node.opCode + ":" + node.spec : node.opCode).append('(');
        for (int i = 0; i < node.params.size(); i++) {
            if (i > 0) out.append(',');
            BNode.Param param = node.params.get(i);
            out.append(param.block() != null ? signature(param.block()) : "'" + (param.text() == null ? "" : param.text()) + "'");
        }
        out.append("){");
        for (BNode child : node.stack1) out.append(signature(child)).append(';');
        out.append('|');
        for (BNode child : node.stack2) out.append(signature(child)).append(';');
        String result = out.append('}').toString();
        signatures.put(node, result);
        return result;
    }

    // ---- alignment

    /** Pairs the new statements of a chain with the old ones of the same chain and gives them the old ids. */
    private void align(List<BNode> fresh, List<Old> old) {
        int n = fresh.size();
        int m = old.size();
        boolean[] newDone = new boolean[n];
        boolean[] oldDone = new boolean[m];

        // identical statements, in order (longest common subsequence)
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = signature(fresh.get(i)).equals(old.get(j).signature) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        for (int i = 0, j = 0; i < n && j < m; ) {
            if (signature(fresh.get(i)).equals(old.get(j).signature)) {
                pair(fresh.get(i), old.get(j));
                newDone[i] = true;
                oldDone[j] = true;
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        // identical statements that moved
        for (int i = 0; i < n; i++) {
            if (newDone[i]) continue;
            for (int j = 0; j < m; j++) {
                if (!oldDone[j] && signature(fresh.get(i)).equals(old.get(j).signature)) {
                    pair(fresh.get(i), old.get(j));
                    newDone[i] = true;
                    oldDone[j] = true;
                    break;
                }
            }
        }
        // between identical ones, the same kind of statement is the same block with a change
        for (int i = 0; i < n; i++) {
            if (newDone[i]) continue;
            int low = previousDone(newDone, i);
            int jStart = low < 0 ? 0 : oldIndexAfter(fresh, old, low);
            for (int j = Math.max(0, jStart); j < m; j++) {
                if (!oldDone[j] && sameKind(fresh.get(i), old.get(j))) {
                    modify(fresh.get(i), old.get(j));
                    newDone[i] = true;
                    oldDone[j] = true;
                    break;
                }
            }
        }
    }

    private static int previousDone(boolean[] done, int index) {
        for (int i = index - 1; i >= 0; i--) if (done[i]) return i;
        return -1;
    }

    private int oldIndexAfter(List<BNode> fresh, List<Old> old, int freshIndex) {
        String reuse = fresh.get(freshIndex).reuseId;
        for (int j = 0; j < old.size(); j++) if (old.get(j).bean.id.equals(reuse)) return j + 1;
        return 0;
    }

    private boolean sameKind(BNode node, Old old) {
        return node.opCode.equals(old.bean.opCode) && (!isNamed(node.opCode) || node.spec.equals(old.bean.spec));
    }

    private static boolean isNamed(String opCode) {
        return opCode.equals("getVar") || opCode.equals("getArg") || opCode.equals("getResStr") || opCode.equals("definedFunc");
    }

    /** The two are identical: the whole subtree keeps its ids. */
    private void pair(BNode node, Old old) {
        node.reuseId = old.bean.id;
        old.taken = true;
        for (int i = 0; i < node.params.size() && i < old.params.size(); i++) {
            BNode.Param param = node.params.get(i);
            if (param.block() != null && old.params.get(i) != null) pair(param.block(), old.params.get(i));
        }
        pairAll(node.stack1, old.stack1);
        pairAll(node.stack2, old.stack2);
    }

    private void pairAll(List<BNode> fresh, List<Old> old) {
        for (int i = 0; i < fresh.size() && i < old.size(); i++) pair(fresh.get(i), old.get(i));
    }

    /** Same kind of block, something inside changed: this block keeps its id, what is inside is matched again. */
    private void modify(BNode node, Old old) {
        node.reuseId = old.bean.id;
        old.taken = true;
        for (int i = 0; i < node.params.size() && i < old.params.size(); i++) {
            BNode.Param param = node.params.get(i);
            Old inner = old.params.get(i);
            if (param.block() == null || inner == null || inner.taken) continue;
            if (signature(param.block()).equals(inner.signature)) pair(param.block(), inner);
            else if (sameKind(param.block(), inner)) modify(param.block(), inner);
        }
        align(node.stack1, old.stack1);
        align(node.stack2, old.stack2);
    }

    /** Subtrees that are identical to an old one that nothing else claimed, wherever they now are. */
    private void moved(List<BNode> roots) {
        Map<String, ArrayDeque<Old>> free = new HashMap<>();
        for (Old old : all.keySet()) {
            if (!old.taken) free.computeIfAbsent(old.signature, k -> new ArrayDeque<>()).add(old);
        }
        if (free.isEmpty()) return;
        claim(roots, free);
    }

    private void claim(List<BNode> nodes, Map<String, ArrayDeque<Old>> free) {
        for (BNode node : nodes) {
            if (node.reuseId == null) {
                ArrayDeque<Old> candidates = free.get(signature(node));
                Old old = candidates == null ? null : firstFree(candidates);
                if (old != null) {
                    pair(node, old);
                    continue;
                }
            }
            for (BNode.Param param : node.params) if (param.block() != null) claim(List.of(param.block()), free);
            claim(node.stack1, free);
            claim(node.stack2, free);
        }
    }

    @Nullable
    private static Old firstFree(ArrayDeque<Old> candidates) {
        while (!candidates.isEmpty()) {
            Old old = candidates.poll();
            if (!old.taken) return old;
        }
        return null;
    }
}
