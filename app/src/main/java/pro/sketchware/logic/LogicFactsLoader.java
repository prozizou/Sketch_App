package pro.sketchware.logic;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;
import mod.hey.studios.moreblock.ReturnMoreblockManager;

/**
 * Reads the blocks, variables, lists and more blocks of every screen of the open project into the plain models
 * of the logic analyses. Read-only; must run while the project is open in the editor, off the main thread.
 */
public final class LogicFactsLoader {
    /** Variable types the Variable manager uses for an import and for a custom declaration. */
    private static final int TYPE_IMPORT = 9;
    private static final int TYPE_CUSTOM = 6;

    private LogicFactsLoader() {
    }

    public static List<LogicScreen> load(String scId) {
        List<LogicScreen> screens = new ArrayList<>();
        for (ProjectFileBean file : jC.b(scId).b()) {
            screens.add(screen(scId, file));
        }
        return screens;
    }

    /** The blocks, variables, lists and more blocks of one screen. */
    public static LogicScreen loadScreen(String scId, ProjectFileBean file) {
        return screen(scId, file);
    }

    /** The blocks of one event, from the editor's own beans. */
    public static LogicEvent event(String key, List<BlockBean> beans) {
        List<LogicBlock> blocks = new ArrayList<>();
        if (beans != null) {
            for (BlockBean bean : beans) {
                if (bean != null && bean.id != null) blocks.add(toBlock(bean));
            }
        }
        return new LogicEvent(key, blocks);
    }

    static LogicBlock toBlock(BlockBean bean) {
        return new LogicBlock(bean.id, bean.opCode == null ? "" : bean.opCode, bean.spec, bean.type,
                bean.parameters == null ? List.of() : new ArrayList<>(bean.parameters),
                bean.nextBlock, bean.subStack1, bean.subStack2);
    }

    private static LogicScreen screen(String scId, ProjectFileBean file) {
        String javaName = file.getJavaName();
        List<LogicEvent> events = new ArrayList<>();
        HashMap<String, ArrayList<BlockBean>> blocks = jC.a(scId).b(javaName);
        if (blocks != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : blocks.entrySet()) {
                events.add(event(entry.getKey(), entry.getValue()));
            }
        }
        Map<String, Integer> variables = new LinkedHashMap<>();
        for (Pair<Integer, String> variable : jC.a(scId).k(javaName)) {
            if (variable.first == null || variable.second == null || variable.first == TYPE_IMPORT) continue;
            String name = variable.first == TYPE_CUSTOM ? LogicScreen.declaredName(variable.second) : variable.second;
            if (!name.isEmpty()) variables.put(name, variable.first);
        }
        Set<String> lists = new LinkedHashSet<>();
        for (Pair<Integer, String> list : jC.a(scId).j(javaName)) {
            if (list.second != null) lists.add(list.second);
        }
        Set<String> moreBlocks = new LinkedHashSet<>();
        for (Pair<String, String> moreBlock : jC.a(scId).i(javaName)) {
            if (moreBlock.first != null) moreBlocks.add(ReturnMoreblockManager.getMbName(moreBlock.first));
        }
        Set<String> widgets = new LinkedHashSet<>();
        ArrayList<ViewBean> views = jC.a(scId).d(file.getXmlName());
        if (views != null) {
            for (ViewBean view : views) widgets.add(view.id);
        }
        return new LogicScreen(javaName, events, variables, lists, moreBlocks, widgets);
    }
}
