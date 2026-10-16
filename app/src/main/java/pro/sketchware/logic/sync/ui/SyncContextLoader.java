package pro.sketchware.logic.sync.ui;

import android.util.Pair;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import pro.sketchware.logic.sync.ProjectContext;
import pro.sketchware.utility.CustomVariableUtil;

/** Reads what the open screen declares (variables, lists, widgets, components, more blocks) for the Java to blocks conversion. */
public final class SyncContextLoader {
    private SyncContextLoader() {
    }

    /**
     * @param args the event's parameters by name (without the underscore) to type letter
     */
    public static ProjectContext load(String scId, ProjectFileBean file, boolean viewBinding, Map<String, String> args) {
        String javaName = file.getJavaName();
        String xmlName = file.getXmlName();

        Map<String, String> variables = new LinkedHashMap<>();
        for (String name : jC.a(scId).e(javaName, 0)) variables.put(name, "b");
        for (String name : jC.a(scId).e(javaName, 1)) variables.put(name, "d");
        for (String name : jC.a(scId).e(javaName, 2)) variables.put(name, "s");
        for (String name : jC.a(scId).e(javaName, 3)) variables.put(name, "a");
        for (String declaration : jC.a(scId).e(javaName, 5)) {
            String[] parts = declaration.split(" ");
            if (parts.length > 1) variables.put(parts[1], "v:" + parts[0]);
        }
        for (String declaration : jC.a(scId).e(javaName, 6)) {
            String type = CustomVariableUtil.getVariableType(declaration);
            String name = CustomVariableUtil.getVariableName(declaration);
            if (type == null || name == null) continue;
            variables.put(name, switch (type) {
                case "boolean", "Boolean" -> "b";
                case "String" -> "s";
                case "double", "Double", "int", "Integer", "float", "Float", "long", "Long", "short", "Short" -> "d";
                default -> "v:" + type;
            });
        }

        Map<String, String> lists = new LinkedHashMap<>();
        for (Pair<Integer, String> list : jC.a(scId).j(javaName)) {
            if (list.first == null || list.second == null) continue;
            lists.put(list.second, switch (list.first) {
                case 1 -> "listInt";
                case 2 -> "listStr";
                default -> "listMap";
            });
        }

        Set<String> views = new LinkedHashSet<>();
        addViews(views, jC.a(scId).d(xmlName));
        addViews(views, jC.a(scId).d("_drawer_" + xmlName));

        Set<String> components = new LinkedHashSet<>();
        if (jC.a(scId).e(javaName) != null) {
            for (ComponentBean component : jC.a(scId).e(javaName)) if (component.componentId != null) components.add(component.componentId);
        }

        Map<String, ProjectContext.MoreBlock> moreBlocks = new LinkedHashMap<>();
        for (Pair<String, String> moreBlock : jC.a(scId).i(javaName)) {
            if (moreBlock.second == null) continue;
            String spec = ReturnMoreblockManager.getMbName(moreBlock.second);
            int space = spec.indexOf(' ');
            String name = space < 0 ? spec : spec.substring(0, space);
            String character = ReturnMoreblockManager.getMoreblockChar(moreBlock.second);
            String type = character;
            String typeName = "";
            int bar = character.indexOf('|');
            if (bar >= 0) {
                type = character.substring(0, bar);
                typeName = character.substring(bar + 1);
            }
            moreBlocks.put(name, new ProjectContext.MoreBlock(name, spec, type, typeName));
        }

        boolean fragment = file.fileName != null && file.fileName.contains("_fragment");
        return new ProjectContext(file.getActivityName(), fragment, viewBinding, variables, lists, views, components, moreBlocks, args);
    }

    private static void addViews(Set<String> views, java.util.List<ViewBean> beans) {
        if (beans == null) return;
        for (ViewBean bean : beans) if (bean.id != null) views.add(bean.id);
    }
}
