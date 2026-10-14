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
    public void theThirdTabIsEvolveAndTheComponentsTabIsGone() {
        AppCompatActivity activity = activity();
        Menu menu = new PopupMenu(activity, new View(activity)).getMenu();
        new MenuInflater(activity).inflate(R.menu.logic_bottom_nav, menu);
        assertEquals(3, menu.size());
        assertEquals(R.id.nav_variables, menu.getItem(0).getItemId());
        assertEquals(R.id.nav_blocks, menu.getItem(1).getItemId());
        assertEquals(R.id.nav_evolve, menu.getItem(2).getItemId());
        assertEquals("Evolve", menu.getItem(2).getTitle().toString());
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
        sheet.dismissAllowingStateLoss();
        ShadowLooper.idleMainLooper();
        assertTrue(true);
    }
}
