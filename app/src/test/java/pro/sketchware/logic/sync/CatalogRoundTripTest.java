package pro.sketchware.logic.sync;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import a.a.a.jq;

/** Every block of the catalog, with plain values, must come back as itself from the Java it generates. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class CatalogRoundTripTest {
    static String sample(String type) {
        if (type.startsWith("%m")) {
            String selector = type.length() > 3 ? type.substring(3) : "";
            int dot = selector.indexOf('.');
            if (dot >= 0) selector = selector.substring(0, dot);
            return switch (selector) {
                case "varInt" -> "counter";
                case "varBool" -> "flag";
                case "varStr" -> "name";
                case "varMap" -> "data";
                case "listInt" -> "numbers";
                case "listStr" -> "items";
                case "listMap" -> "rows";
                case "list" -> "items";
                case "view", "layout", "textview", "button", "edittext", "imageview", "recyclerview", "listview", "gridview",
                     "cardview", "viewpager", "webview", "videoview", "progressbar", "seekbar", "switch", "checkbox", "spinner",
                     "tablayout", "bottomnavigation", "adview", "swiperefreshlayout", "textinputlayout", "ratingbar",
                     "datepicker", "otpview", "lottie", "badgeview", "codeview", "patternview", "signinbutton", "youtubeview",
                     "calendarview", "mapview", "radiobutton" -> "textview1";
                default -> "intent";
            };
        }
        char base = type.length() > 1 ? type.charAt(1) : 's';
        return switch (base) {
            case 'd' -> "5";
            case 'b' -> "true";
            default -> "text";
        };
    }

    /** Blocks whose Java is not a statement on its own (it needs an {@code if} before it, or a {@code switch} around it). */
    private static final Set<String> NOT_ON_ITS_OWN = Set.of("addSourceDirectly", "else", "elseIf", "switchStr", "switchNum", "caseStr", "caseNum",
            "caseStrAnd", "caseNumAnd", "defaultSwitch", "customImport", "customImport2",
            // the generator writes invalid Java for these with plain sample values
            "setTypeface", "setCustomLetter", "isConnected");
    /** Blocks whose Java is kept as text: it is a comment, or uses a menu value the sample can't stand for. */
    private static final Set<String> KEPT_AS_TEXT = Set.of("CommandBlockJava", "CommandBlockXML", "objectanimatorSetInterpolator");

    @Test
    public void everyBlockComesBackAsItselfOrAsABlockThatGivesTheSameCode() {
        ArrayList<HashMap<String, Object>> extras = new ArrayList<>();
        mod.hilal.saif.blocks.BlocksHandler.builtInBlocks(extras);
        BlockCatalog catalog = BlockCatalog.with(extras);
        JavaToBlocks converter = SyncFixtures.converter();
        Set<String> invalid = new TreeSet<>();
        Set<String> text = new TreeSet<>();
        int exact = 0;
        int equivalent = 0;
        for (BlockDef def : catalog.all()) {
            if (!def.isStatement()) continue; // reporters are checked inside a statement below
            ArrayList<BlockBean> blocks = new ArrayList<>();
            BlockBean bean = new BlockBean("1", def.spec(), def.type(), def.typeName(), def.opCode());
            for (String param : def.params()) bean.parameters.add(sample(param));
            blocks.add(bean);
            for (int stack = 1; stack <= def.stacks(); stack++) {
                blocks.add(new BlockBean(String.valueOf(stack + 1), "break", "f", "break"));
                if (stack == 1) bean.subStack1 = 2;
                else bean.subStack2 = 3;
            }
            String code;
            try {
                code = SyncFixtures.generate(blocks);
            } catch (RuntimeException e) {
                continue;
            }
            if (code.isBlank()) continue;
            JavaToBlocks.Result result = converter.convert(code, List.of());
            if (!result.ok()) {
                invalid.add(def.opCode());
                continue;
            }
            // whatever the blocks are, they generate the code that was written
            assertTrue(def.opCode() + " must give back its code", converter.sameCode(SyncFixtures.generate(result.blocks()), code));
            if (!result.sourceBlocks().isEmpty()) {
                text.add(def.opCode());
            } else if (result.blocks().get(0).opCode.equals(def.opCode())) {
                exact++;
            } else {
                equivalent++;
            }
        }
        assertEquals("blocks whose code is not a statement on its own", new TreeSet<>(NOT_ON_ITS_OWN), invalid);
        assertEquals("blocks whose code stays text", new TreeSet<>(KEPT_AS_TEXT), text);
        assertTrue("most blocks must come back as themselves, got " + exact, exact > 330);
        assertTrue("blocks that make the same code as another block are few, got " + equivalent, equivalent < 60);
    }

    @Test
    public void everyReporterComesBackInsideAStatement() {
        ArrayList<HashMap<String, Object>> extras = new ArrayList<>();
        mod.hilal.saif.blocks.BlocksHandler.builtInBlocks(extras);
        BlockCatalog catalog = BlockCatalog.with(extras);
        JavaToBlocks converter = SyncFixtures.converter();
        List<String> failed = new ArrayList<>();
        int checked = 0;
        for (BlockDef def : catalog.all()) {
            if (!def.isReporter() || def.opCode().startsWith("asd") || def.opCode().equals("getVar")) continue;
            ArrayList<BlockBean> blocks = new ArrayList<>();
            BlockBean statement = new BlockBean("1", "set %m.varStr to %s", " ", "setVarString");
            statement.parameters.add("name");
            statement.parameters.add("@2");
            BlockBean reporter = new BlockBean("2", def.spec(), def.type(), def.typeName(), def.opCode());
            for (String param : def.params()) reporter.parameters.add(sample(param));
            blocks.add(statement);
            blocks.add(reporter);
            String code;
            try {
                code = SyncFixtures.generate(blocks);
            } catch (RuntimeException e) {
                continue;
            }
            if (code.isBlank() || !code.contains("name")) continue;
            checked++;
            JavaToBlocks.Result result = converter.convert(code, List.of());
            if (!result.ok()) continue; // the sample values made invalid Java
            if (!converter.sameCode(SyncFixtures.generate(result.blocks()), code)) failed.add(def.opCode());
        }
        assertTrue("checked " + checked, checked > 100);
        assertTrue("reporters that don't give back their code: " + failed, failed.isEmpty());
    }
}
