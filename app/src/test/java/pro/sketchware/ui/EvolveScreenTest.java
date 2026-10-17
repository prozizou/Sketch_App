package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.evolve.EvolveSheet;
import pro.sketchware.evolve.SharedBlockCodec;

/** Evolve replaces the third tab of the Logic editor: the tab, the sheet and the block format it shares. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class EvolveScreenTest {

    private static AppCompatActivity activity() {
        RuntimeEnvironment.setQualifiers("+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static BlockBean block(String id, String opCode, String... parameters) {
        BlockBean bean = new BlockBean(id, "spec of " + opCode, " ", opCode);
        bean.parameters.addAll(List.of(parameters));
        return bean;
    }

    @Test
    public void theTabsAreBlocksAndEvolveOnly() {
        AppCompatActivity activity = activity();
        Menu menu = new PopupMenu(activity, new View(activity)).getMenu();
        new MenuInflater(activity).inflate(R.menu.logic_bottom_nav, menu);
        assertEquals(2, menu.size());
        assertEquals(R.id.nav_blocks, menu.getItem(0).getItemId());
        assertEquals(R.id.nav_evolve, menu.getItem(1).getItemId());
        assertEquals("Evolve", menu.getItem(1).getTitle().toString());
        for (int i = 0; i < menu.size(); i++) {
            assertFalse("Variables live in Blocks, not in a tab", "My Variables".contentEquals(menu.getItem(i).getTitle()));
        }
        for (int i = 0; i < menu.size(); i++) {
            assertFalse("Components is no longer a tab", "Components".contentEquals(menu.getItem(i).getTitle()));
        }
    }

    @Test
    public void blocksSurviveTheTripThroughJson() {
        BlockBean ifBlock = block("99000001", "if", "@99000002");
        ifBlock.subStack1 = 99000003;
        BlockBean condition = block("99000002", "true");
        BlockBean inner = block("99000003", "showToast", "Hello éè \"quoted\"");
        ArrayList<BlockBean> blocks = new ArrayList<>(List.of(ifBlock, condition, inner));

        ArrayList<BlockBean> back = SharedBlockCodec.decode(SharedBlockCodec.encode(blocks));
        assertNotNull(back);
        assertEquals(3, back.size());
        assertEquals("99000001", back.get(0).id);
        assertEquals("if", back.get(0).opCode);
        assertEquals(99000003, back.get(0).subStack1);
        assertEquals(-1, back.get(0).nextBlock);
        assertEquals(List.of("@99000002"), back.get(0).parameters);
        assertEquals("Hello éè \"quoted\"", back.get(2).parameters.get(0));
        assertEquals(List.of("if", "true", "showToast"), SharedBlockCodec.opCodes(back));
    }

    @Test
    public void anythingThatIsNotAWellFormedStackIsRejected() {
        assertNull(SharedBlockCodec.decode(null));
        assertNull(SharedBlockCodec.decode(""));
        assertNull(SharedBlockCodec.decode("not json"));
        assertNull(SharedBlockCodec.decode("{}"));
        assertNull(SharedBlockCodec.decode("[]"));
        assertNull(SharedBlockCodec.decode("[null]"));
        assertNull("a block needs an opcode", SharedBlockCodec.decode("[{\"id\":\"1\"}]"));
        assertNull("a block needs an id", SharedBlockCodec.decode("[{\"opCode\":\"if\"}]"));
    }

    @Test
    public void missingPartsOfABlockAreFilledSoTheEditorCantChokeOnThem() {
        ArrayList<BlockBean> blocks = SharedBlockCodec.decode("[{\"id\":\"1\",\"opCode\":\"if\",\"parameters\":[null,\"x\"]}]");
        assertNotNull(blocks);
        BlockBean bean = blocks.get(0);
        assertEquals(List.of("", "x"), bean.parameters);
        assertNotNull(bean.spec);
        assertNotNull(bean.type);
        assertNotNull(bean.typeName);
        BlockBean noParameters = SharedBlockCodec.decode("[{\"id\":\"1\",\"opCode\":\"if\",\"parameters\":null}]").get(0);
        assertNotNull(noParameters.parameters);
    }

    @Test
    public void theSheetSaysSoInsteadOfCrashingWhenFirebaseIsNotSetUp() {
        AppCompatActivity activity = activity();
        EvolveSheet sheet = EvolveSheet.newInstance();
        sheet.show(activity.getSupportFragmentManager(), "evolve-test");
        ShadowLooper.idleMainLooper();
        View root = sheet.getView();
        assertNotNull(root);
        TextView state = root.findViewById(R.id.evolve_state);
        assertEquals(View.VISIBLE, state.getVisibility());
        assertEquals(activity.getString(R.string.evolve_unavailable), state.getText().toString());
        assertFalse(root.findViewById(R.id.evolve_share).isEnabled());
        assertFalse(root.findViewById(R.id.evolve_refresh).isEnabled());
        assertFalse("nothing to retry when Evolve simply isn't available", root.findViewById(R.id.evolve_retry).getVisibility() == View.VISIBLE);
        assertEquals(View.GONE, root.findViewById(R.id.evolve_skeleton).getVisibility());
        com.google.android.material.chip.ChipGroup modes = root.findViewById(R.id.evolve_modes);
        assertEquals("All, Popular, Recent, My uploads", 4, modes.getChildCount());
        assertEquals("one kind offered: no kind chips", View.GONE, root.findViewById(R.id.evolve_kinds).getVisibility());
        sheet.dismissAllowingStateLoss();
        ShadowLooper.idleMainLooper();
        assertTrue(true);
    }

    @Test
    public void everyKindOfStackCanBeSplitFromTheCanvas() {
        // Stack A: if (nest holds setText) then toast. Stack B: a lone reporter plugged nowhere.
        BlockBean ifBlock = block("10", "if");
        ifBlock.subStack1 = 11;
        ifBlock.nextBlock = 12;
        ifBlock.parameters.add("@13");
        BlockBean inner = block("11", "setText");
        BlockBean after = block("12", "showToast");
        BlockBean condition = block("13", "isEmpty");
        BlockBean lone = block("20", "listGet");
        List<BlockBean> canvas = List.of(ifBlock, inner, after, condition, lone);

        List<pro.sketchware.evolve.BlockStacks.Stack> stacks = pro.sketchware.evolve.BlockStacks.split(canvas);

        assertEquals(2, stacks.size());
        assertEquals("10", stacks.get(0).root().id);
        assertEquals(List.of("10", "11", "12", "13"), ids(stacks.get(0).blocks()));
        assertEquals(List.of("20"), ids(stacks.get(1).blocks()));
        assertTrue("copies, never the canvas beans", stacks.get(0).blocks().get(0) != ifBlock);
    }

    private static List<String> ids(List<BlockBean> blocks) {
        List<String> ids = new ArrayList<>();
        for (BlockBean bean : blocks) {
            ids.add(bean.id);
        }
        return ids;
    }

    @Test
    public void aLayoutIsCheckedBeforeItIsSharedOrPicked() {
        pro.sketchware.evolve.SharedXml.Result ok = pro.sketchware.evolve.SharedXml.check(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><TextView/><Button/></LinearLayout>");
        assertEquals(pro.sketchware.evolve.SharedBlockRules.Problem.NONE, ok.problem());
        assertEquals(3, ok.elements());
        assertEquals(pro.sketchware.evolve.SharedBlockRules.Problem.XML_EMPTY, pro.sketchware.evolve.SharedXml.check("  ").problem());
        assertEquals(pro.sketchware.evolve.SharedBlockRules.Problem.XML_INVALID, pro.sketchware.evolve.SharedXml.check("<a><b></a>").problem());
        assertEquals(pro.sketchware.evolve.SharedBlockRules.Problem.TOO_LARGE,
                pro.sketchware.evolve.SharedXml.check("<a>" + "x".repeat(pro.sketchware.evolve.SharedBlockRules.MAX_JSON_LENGTH) + "</a>").problem());
    }

    @Test
    public void whatThePickerShowsBeforeAddingIsReal() {
        BlockBean raw = block("1", "addSourceDirectly", "System.out.println(1);");
        BlockBean variable = new BlockBean("2", "counter", " ", "getVar");
        BlockBean other = block("3", "showToast", "@2");
        List<BlockBean> stack = List.of(raw, variable, other);

        assertEquals(List.of("counter"), pro.sketchware.evolve.EvolveChecks.variables(stack));
        List<pro.sketchware.evolve.EvolveChecks.RawSnippet> snippets = pro.sketchware.evolve.EvolveChecks.rawCode(stack);
        assertEquals(1, snippets.size());
        assertEquals("addSourceDirectly", snippets.get(0).opCode());
        assertEquals("System.out.println(1);", snippets.get(0).code());
        assertTrue(pro.sketchware.evolve.EvolveChecks.madeWithNewerApp(200, 157));
        assertFalse(pro.sketchware.evolve.EvolveChecks.madeWithNewerApp(157, 157));
        String json = SharedBlockCodec.encode(stack);
        assertTrue(pro.sketchware.evolve.EvolveChecks.alreadyHave(json, List.of("[]", json)));
        assertFalse(pro.sketchware.evolve.EvolveChecks.alreadyHave(json, List.of("[]")));
    }
}
