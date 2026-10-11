package pro.sketchware.blocks.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import pro.sketchware.blocks.editor.BlockSpec.Param;
import pro.sketchware.blocks.editor.BlockSpec.Token;

public class BlockSpecTest {
    @Test
    public void parsesWordsAndEveryKindOfParameter() {
        List<Token> tokens = BlockSpec.parse("set %m.view text to %s then %b and %d or %s.inputOnly");
        assertEquals(11, tokens.size());
        assertEquals(new Param('m', "view"), tokens.get(1));
        assertEquals(new Param('s', null), tokens.get(4));
        assertEquals(new Param('b', null), tokens.get(6));
        assertEquals(new Param('d', null), tokens.get(8));
        assertEquals(new Param('s', "inputOnly"), tokens.get(10));
        assertEquals(5, BlockSpec.paramCount("set %m.view text to %s then %b and %d or %s.inputOnly"));
    }

    @Test
    public void everyEditorParameterTypeParsesAndPrintsAsWritten() {
        for (BlockParamTypes.Type type : BlockParamTypes.ALL) {
            Param param = type.param();
            assertEquals(type.spec(), param.text());
            assertEquals(type.label(), BlockParamTypes.labelOf(param));
        }
        assertEquals("customMenu", BlockParamTypes.labelOf(new Param('m', "customMenu")));
        assertEquals(-1, BlockParamTypes.indexOf(new Param('m', "customMenu")));
    }

    @Test
    public void aSpecWrittenWithSingleSpacesRoundTripsUnchanged() {
        for (String spec : new String[]{"show toast %s", "%m.view visible %b", "repeat %d times", "do", "a %s.menu b %m.listMap"}) {
            assertEquals(spec, BlockSpec.toSpec(BlockSpec.parse(spec)));
        }
    }

    @Test
    public void invalidParameters() {
        List<BlockSpec.Invalid> problems = BlockSpec.problems(BlockSpec.parse("a %m b %x c %s, d %m.view"));
        assertEquals(3, problems.size());
        assertTrue(problems.get(0).error());
        assertTrue(problems.get(0).problem().contains("%m"));
        assertTrue(!problems.get(1).error());
        assertEquals("%s,", problems.get(2).text());
    }

    @Test
    public void editingOperations() {
        List<Token> tokens = BlockSpec.parse("show %s for %d ms");
        assertEquals("show %s for %d ms %b", BlockSpec.toSpec(BlockSpec.addParam(tokens, new Param('b', null))));
        assertEquals("show %s for %d ms now", BlockSpec.toSpec(BlockSpec.addText(tokens, " now ")));
        assertEquals("show for %d ms", BlockSpec.toSpec(BlockSpec.removeParam(tokens, 0)));
        assertEquals("show %m.view for %d ms", BlockSpec.toSpec(BlockSpec.retypeParam(tokens, 0, new Param('m', "view"))));
        assertEquals("show for %s %d ms", BlockSpec.toSpec(BlockSpec.move(tokens, 1, +1)));
        // The original list is never changed.
        assertEquals("show %s for %d ms", BlockSpec.toSpec(tokens));
    }

    @Test
    public void movingAParameterKeepsTheWordsInPlace() {
        List<Token> tokens = BlockSpec.parse("a %s b %d c %b");
        assertEquals("a %d b %b c %s", BlockSpec.toSpec(BlockSpec.moveParam(tokens, 0, 2)));
        assertEquals("a %b b %s c %d", BlockSpec.toSpec(BlockSpec.moveParam(tokens, 2, 0)));
        assertEquals("a %s b %d c %b", BlockSpec.toSpec(BlockSpec.moveParam(tokens, 1, 1)));
        assertEquals("a %s b %d c %b", BlockSpec.toSpec(BlockSpec.moveParam(tokens, 5, 0)));
    }

    @Test
    public void blockShapesAndTypeNames() {
        assertEquals(" ", BlockShapes.of(" ").stored());
        assertEquals(" ", BlockShapes.of("regular").stored());
        assertEquals("c", BlockShapes.of("c").stored());
        assertEquals(null, BlockShapes.of("zz"));
        assertTrue(BlockShapes.of("e").hasSlots());
        assertTrue(BlockShapes.usesTypeName("p"));
        assertTrue(!BlockShapes.usesTypeName("s"));
        // Every shape the editor lets you pick is one the manager accepts on import.
        for (BlockShapes.Shape shape : BlockShapes.ALL) {
            assertTrue(shape.type(), pro.sketchware.blocks.manager.BlockTypes.isKnown(shape.type()));
        }
    }
}
