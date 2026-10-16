package pro.sketchware.logic.sync;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.Rs;
import pro.sketchware.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class BlockSpecsTest {
    @Test
    public void everyBuiltInBlockHasTheSpecThePaletteGivesIt() throws Exception {
        Context raw = ApplicationProvider.getApplicationContext();
        Context context = new android.view.ContextThemeWrapper(raw, R.style.Theme_SketchwarePro);
        ThemeHarnessAccess.prepare();
        BlockSpecs.Strings strings = name -> {
            int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
            return id == 0 ? null : context.getString(id);
        };
        List<String> report = new ArrayList<>();
        int same = 0;
        for (BlockDef def : BlockCatalog.builtIn().all()) {
            String assembled = BlockSpecs.specOf(def, strings);
            String real;
            try {
                Rs block = new Rs(context, -1, "", def.type(), def.opCode());
                real = block.getBean().spec;
            } catch (Throwable t) {
                real = "!! " + t;
            }
            if (assembled.equals(real)) same++;
            else report.add(def.opCode() + " assembled=[" + assembled + "] real=[" + real + "]");
        }
        assertTrue("specs that differ from the palette's: " + report, report.isEmpty());
        assertTrue(same > 300);
    }

    @Test
    public void anExtraBlockKeepsItsOwnSpec() {
        BlockDef def = new BlockDef("mine", " ", "", java.util.List.of("%s"), false);
        assertEquals("mine %s", BlockSpecs.specOf(def, name -> null));
    }

    @Test
    public void aBuiltInBlockWithoutTextsFallsBackToItsParameters() {
        BlockDef def = BlockCatalog.builtIn().find("setVarInt");
        assertEquals("setVarInt %m.varInt %d", BlockSpecs.specOf(def, name -> null));
    }

    @Test
    public void theTextsAreJoinedAroundTheParameters() throws Exception {
        ThemeHarnessAccess.prepare();
        BlockDef def = BlockCatalog.builtIn().find("setVarInt");
        java.util.Map<String, String> texts = java.util.Map.of("block_set_var_int_head", "set", "block_set_var_int_body_1", "to");
        assertEquals("set %m.varInt to %d", BlockSpecs.specOf(def, texts::get));
        BlockDef two = BlockCatalog.builtIn().find("if");
        assertEquals("if %b then", BlockSpecs.specOf(two, java.util.Map.of("block_if_head", "if", "block_if_tail", "then")::get));
    }
}
