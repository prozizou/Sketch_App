package pro.sketchware.logic.sync;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.jq;
import pro.sketchware.blocks.GeneratedCode;

/** A screen with a few of everything, and a converter for it. */
final class SyncFixtures {
    private static JavaToBlocks converter;

    private SyncFixtures() {
    }

    static ProjectContext context() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("counter", "d");
        variables.put("total", "d");
        variables.put("name", "s");
        variables.put("flag", "b");
        variables.put("data", "a");
        Map<String, String> lists = new LinkedHashMap<>();
        lists.put("items", "listStr");
        lists.put("numbers", "listInt");
        lists.put("rows", "listMap");
        Map<String, ProjectContext.MoreBlock> more = new LinkedHashMap<>();
        more.put("greet", new ProjectContext.MoreBlock("greet", "greet %s", " ", ""));
        more.put("addUp", new ProjectContext.MoreBlock("addUp", "addUp %d %d", "d", ""));
        more.put("isBig", new ProjectContext.MoreBlock("isBig", "isBig %d", "b", ""));
        Map<String, String> args = new LinkedHashMap<>();
        args.put("position", "d");
        args.put("text", "s");
        return new ProjectContext("MainActivity", false, false, variables, lists, Set.of("textview1", "button1", "edittext1", "linear1"),
                Set.of("intent", "timer", "calendar"), more, args);
    }

    static synchronized JavaToBlocks converter() {
        if (converter == null) {
            ArrayList<HashMap<String, Object>> extras = new ArrayList<>();
            mod.hilal.saif.blocks.BlocksHandler.builtInBlocks(extras);
            converter = JavaToBlocks.create(BlockCatalog.with(extras), context(), new jq(), SyncFixtures::generate, strings());
        }
        return converter;
    }

    static BlockSpecs.Strings strings() {
        android.content.Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        return name -> {
            int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
            return id == 0 ? null : context.getString(id);
        };
    }

    static String generate(ArrayList<BlockBean> blocks) {
        return GeneratedCode.forEvent("MainActivity", new jq(), blocks, false, false);
    }

    /** The blocks as an indented outline, for reading in a failure message. */
    static String outline(List<BlockBean> beans) {
        return outline(beans, true);
    }

    /** The blocks reachable from the first one, one per line; {@code ids} adds each block's id. */
    static String outline(List<BlockBean> beans, boolean ids) {
        Map<String, BlockBean> byId = new HashMap<>();
        for (BlockBean bean : beans) byId.put(bean.id, bean);
        StringBuilder out = new StringBuilder();
        if (!beans.isEmpty()) chain(byId, beans.get(0), 0, out, ids);
        return out.toString();
    }

    private static void chain(Map<String, BlockBean> byId, BlockBean bean, int depth, StringBuilder out, boolean ids) {
        while (bean != null) {
            out.append("  ".repeat(depth)).append(bean.opCode);
            if (ids) out.append('#').append(bean.id);
            out.append(params(byId, bean)).append('\n');
            if (bean.subStack1 >= 0) chain(byId, byId.get(String.valueOf(bean.subStack1)), depth + 1, out, ids);
            if (bean.subStack2 >= 0) {
                out.append("  ".repeat(depth)).append("else\n");
                chain(byId, byId.get(String.valueOf(bean.subStack2)), depth + 1, out, ids);
            }
            bean = bean.nextBlock >= 0 ? byId.get(String.valueOf(bean.nextBlock)) : null;
        }
    }

    private static String params(Map<String, BlockBean> byId, BlockBean bean) {
        if (bean.parameters.isEmpty() && !bean.opCode.equals("getVar") && !bean.opCode.equals("getArg")) return "";
        StringBuilder out = new StringBuilder("(");
        if (bean.opCode.equals("getVar") || bean.opCode.equals("getArg")) out.append(bean.spec);
        for (int i = 0; i < bean.parameters.size(); i++) {
            if (i > 0 || bean.opCode.equals("getVar")) out.append(", ");
            String parameter = bean.parameters.get(i);
            if (parameter.startsWith("@")) {
                BlockBean inner = byId.get(parameter.substring(1));
                out.append(inner == null ? parameter : inner.opCode + params(byId, inner));
            } else {
                out.append('\'').append(parameter.replace("\n", "\\n")).append('\'');
            }
        }
        return out.append(')').toString();
    }
}
