package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.lq;

/** The list of built-in blocks must agree with the editor, which this test reads from its sources and its classes. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35, application = android.app.Application.class)
public class BlockCatalogTest {
    private static final File SOURCES = new File("src/main/java");

    @Test
    public void everyBuiltInBlockHasTheParameterTypesTheEditorGives() {
        for (BlockDef def : BlockCatalog.builtIn().all()) {
            ArrayList<String> params = lq.a(def.opCode());
            assertNotNull(def.opCode() + " is not a block of the editor", params);
            assertEquals(def.opCode(), params, def.params());
        }
    }

    @Test
    public void everyBlockOfThePaletteIsInTheList() throws Exception {
        Map<String, String> registered = new HashMap<>();
        Pattern call = Pattern.compile("\\.a\\(\"([ bdscfeghpvl]{1,2})\",\\s*\"([^\"\\s]+)\"\\)");
        try (var files = Files.walk(SOURCES.toPath())) {
            for (var path : (Iterable<java.nio.file.Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = call.matcher(Files.readString(path));
                while (matcher.find()) registered.putIfAbsent(matcher.group(2), matcher.group(1));
            }
        }
        BlockCatalog catalog = BlockCatalog.builtIn();
        List<String> missing = new ArrayList<>();
        List<String> wrongType = new ArrayList<>();
        for (Map.Entry<String, String> entry : registered.entrySet()) {
            if (lq.a(entry.getKey()) == null) continue; // an extra block: it comes with its own definition
            BlockDef def = catalog.find(entry.getKey());
            if (def == null) missing.add(entry.getKey());
            else if (!def.type().equals(entry.getValue())) wrongType.add(entry.getKey() + " " + def.type() + " vs " + entry.getValue());
        }
        assertTrue("built-in blocks missing from builtin-blocks.tsv: " + missing, missing.isEmpty());
        assertTrue("block types that differ: " + wrongType, wrongType.isEmpty());
    }

    @Test
    public void everyOpcodeTheGeneratorHandlesIsKnownOrDeliberatelyLeftOut() throws Exception {
        String source = Files.readString(new File(SOURCES, "a/a/a/Fx.java").toPath());
        Set<String> handled = new HashSet<>();
        Matcher matcher = Pattern.compile("case \"([^\"]+)\"").matcher(source);
        while (matcher.find()) handled.add(matcher.group(1));
        // blocks that are not in the palette as such: they are made from variables, arguments, more blocks and resources,
        // or are old names of blocks the palette no longer offers
        Set<String> deliberate = Set.of("getVar", "getArg", "getResStr", "definedFunc", "Accelerate", "AccelerateDeccelerate", "Bounce", "Decelerate",
                "adViewLoadAd", "calendarViewGetDate", "currentTime", "fileSetFileName", "interstitialadCreate", "interstitialadLoadAd",
                "interstitialadShow", "listGetCheckedCount", "listGetCheckedPosition", "listGetCheckedPositions", "listSetItemChecked",
                "setClickable", "setThumbResource", "setTrackResource");
        // the extra blocks of the editor (their specs come from its block list) complete the built-in ones
        ArrayList<HashMap<String, Object>> extras = new ArrayList<>();
        mod.hilal.saif.blocks.BlocksHandler.builtInBlocks(extras);
        BlockCatalog catalog = BlockCatalog.with(extras);
        List<String> missing = new ArrayList<>();
        for (String opCode : handled) if (catalog.find(opCode) == null && !deliberate.contains(opCode)) missing.add(opCode);
        assertTrue("opcodes the generator handles but the list doesn't know: " + missing, missing.isEmpty());
    }

    @Test
    public void extraBlocksCarryTheirOwnSpec() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("name", "myBlock");
        extra.put("type", "c");
        extra.put("typeName", "");
        extra.put("spec", "do %s twice on %m.view and %b");
        BlockCatalog catalog = BlockCatalog.with(List.of(extra));
        BlockDef def = catalog.find("myBlock");
        assertNotNull(def);
        assertEquals(List.of("%s", "%m.view", "%b"), def.params());
        assertEquals(1, def.stacks());
        assertTrue(!def.builtIn());
    }

    @Test
    public void anExtraBlockNeverReplacesABuiltInOne() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("name", "setVarInt");
        extra.put("type", "d");
        extra.put("spec", "something else");
        assertTrue(BlockCatalog.with(List.of(extra)).find("setVarInt").builtIn());
    }

    @Test
    public void badExtraDefinitionsAreIgnored() {
        Map<String, Object> noType = new HashMap<>();
        noType.put("name", "a");
        noType.put("spec", "x");
        Map<String, Object> noSpec = new HashMap<>();
        noSpec.put("name", "b");
        noSpec.put("type", " ");
        BlockCatalog catalog = BlockCatalog.with(List.of(noType, noSpec));
        assertNull(catalog.find("a"));
        assertNull(catalog.find("b"));
    }

    @Test
    public void shapesTellStatementsFromReporters() {
        BlockCatalog catalog = BlockCatalog.builtIn();
        assertTrue(catalog.find("setVarInt").isStatement());
        assertTrue(catalog.find("if").isStatement());
        assertEquals(1, catalog.find("if").stacks());
        assertEquals(2, catalog.find("ifElse").stacks());
        assertTrue(catalog.find("stringJoin").isReporter());
        assertTrue(catalog.find("+").isReporter());
        assertEquals("d", catalog.find("+").type());
    }
}
