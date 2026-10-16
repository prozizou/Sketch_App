package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class JavaToBlocksTest {
    private static JavaToBlocks.Result convert(String java) {
        JavaToBlocks.Result result = SyncFixtures.converter().convert(java, List.of());
        assertTrue(result.problems().toString(), result.ok());
        return result;
    }

    private static String outline(String java) {
        return SyncFixtures.outline(convert(java).blocks(), false).trim();
    }

    /** Whatever the code, the blocks must generate the same code back. */
    private static void assertRoundTrip(String java) {
        JavaToBlocks.Result result = convert(java);
        assertTrue("same code back for:\n" + java + "\n--- got:\n" + SyncFixtures.generate(result.blocks()),
                SyncFixtures.converter().sameCode(SyncFixtures.generate(result.blocks()), java));
    }

    // ---- statements

    @Test
    public void aToastBecomesTheToastBlock() {
        assertEquals("doToast('hi')", outline("SketchwareUtil.showMessage(getApplicationContext(), \"hi\");"));
    }

    @Test
    public void severalStatementsBecomeAChainInOrder() {
        assertEquals("doToast('a')\nincreaseInt('counter')\ndoToast('b')",
                outline("SketchwareUtil.showMessage(getApplicationContext(), \"a\");\ncounter++;\nSketchwareUtil.showMessage(getApplicationContext(), \"b\");"));
    }

    @Test
    public void anAssignmentUsesTheVariableTypeOfTheBlock() {
        assertEquals("setVarString('name', 'bob')", outline("name = \"bob\";"));
        assertEquals("setVarInt('counter', '5')", outline("counter = 5;"));
        assertEquals("setVarBoolean('flag', 'true')", outline("flag = true;"));
    }

    @Test
    public void aNameThatIsNotAVariableIsNotMadeIntoABlock() {
        JavaToBlocks.Result result = convert("mystery = 5;");
        assertEquals("addSourceDirectly('mystery = 5;')", SyncFixtures.outline(result.blocks(), false).trim());
        assertEquals(1, result.sourceBlocks().size());
        assertEquals(1, result.sourceBlocks().get(0).line());
    }

    @Test
    public void expressionsBecomeReportersInsideTheParameters() {
        assertEquals("setVarInt('counter', +(getVar(counter), '1'))", outline("counter = counter + 1;"));
        assertEquals("setVarInt('total', *(+(getVar(counter), '1'), '2'))", outline("total = (counter + 1) * 2;"));
        assertEquals("setText('textview1', getVar(name))", outline("textview1.setText(name);"));
    }

    @Test
    public void groupingIsKept() {
        assertRoundTrip("total = (counter + 1) * 2;");
        assertRoundTrip("total = counter + 1 * 2;");
        assertRoundTrip("total = counter - (total - 1);");
    }

    @Test
    public void listsAreRecognisedByTheirKind() {
        assertEquals("addListStr('a', 'items')\naddListInt(getVar(counter), 'numbers')", outline("items.add(\"a\");\nnumbers.add(Double.valueOf(counter));"));
    }

    @Test
    public void eventArgumentsAreArgumentBlocks() {
        assertEquals("setVarString('name', getArg(text))", outline("name = _text;"));
        assertEquals("setVarInt('counter', getArg(position))", outline("counter = _position;"));
    }

    @Test
    public void moreBlocksAreCalledByTheirNames() {
        assertEquals("definedFunc('bob')", outline("_greet(\"bob\");"));
        assertEquals("setVarInt('total', definedFunc(getVar(counter), '2'))", outline("total = _addUp(counter, 2);"));
        assertEquals("if(definedFunc(getVar(counter)))\n  increaseInt('counter')", outline("if (_isBig(counter)) {\n counter++;\n}"));
    }

    @Test
    public void aMoreBlockCallWithTheWrongNumberOfArgumentsIsKeptAsText() {
        assertEquals("addSourceDirectly('_greet(\"a\", \"b\");')", outline("_greet(\"a\", \"b\");"));
    }

    // ---- structure

    @Test
    public void ifElseKeepsBothStacks() {
        assertEquals("ifElse(>(getVar(counter), '5'))\n  setVarString('name', 'big')\nelse\n  setVarString('name', 'small')",
                outline("if (counter > 5) {\n  name = \"big\";\n} else {\n  name = \"small\";\n}"));
    }

    @Test
    public void anIfWithoutBracesIsTheSameBlock() {
        assertEquals("if(getVar(flag))\n  increaseInt('counter')", outline("if (flag) counter++;"));
    }

    @Test
    public void anElseIfChainBecomesNestedBlocksThatGiveTheSameCode() {
        String java = "if (counter > 5) {\n  name = \"a\";\n} else if (counter > 2) {\n  name = \"b\";\n} else {\n  name = \"c\";\n}";
        assertEquals("ifElse(>(getVar(counter), '5'))\n  setVarString('name', 'a')\nelse\n  ifElse(>(getVar(counter), '2'))\n    setVarString('name', 'b')\n  else\n    setVarString('name', 'c')",
                outline(java));
        assertRoundTrip(java);
    }

    @Test
    public void loopsKeepTheirBody() {
        assertEquals("forever\n  break", outline("while (true) {\n  break;\n}"));
        assertEquals("repeat('3')\n  increaseInt('counter')", outline("for (int _repeat5 = 0; _repeat5 < (int)(3); _repeat5++) {\n  counter++;\n}"));
    }

    @Test
    public void nestedStacksAreConvertedToTheirDepth() {
        String java = "while (true) {\n  if (counter > 3) {\n    break;\n  }\n  counter++;\n}";
        assertEquals("forever\n  if(>(getVar(counter), '3'))\n    break\n  increaseInt('counter')", outline(java));
    }

    @Test
    public void aListenerBlockHoldsItsStatements() {
        String java = "textview1.setOnClickListener(new View.OnClickListener() {\n@Override\npublic void onClick(View _view) {\ncounter++;\n}\n});";
        assertEquals("viewOnClick('textview1')\n  increaseInt('counter')", outline(java));
    }

    // ---- code that has no block

    @Test
    public void aLocalVariableIsKeptAsTextWithItsLine() {
        JavaToBlocks.Result result = convert("counter++;\nint local = 5;\ncounter--;");
        assertEquals("increaseInt('counter')\naddSourceDirectly('int local = 5;')\ndecreaseInt('counter')", SyncFixtures.outline(result.blocks(), false).trim());
        assertEquals(1, result.sourceBlocks().size());
        assertEquals(2, result.sourceBlocks().get(0).line());
        assertEquals("int local = 5;", result.sourceBlocks().get(0).code());
    }

    @Test
    public void anUnknownCallIsKeptAsText() {
        assertEquals("addSourceDirectly('doSomething(counter);')", outline("doSomething(counter);"));
    }

    @Test
    public void anExpressionWithNoBlockInsideAKnownBlockIsAnInlineJavaBlock() {
        assertEquals("setVarInt('counter', asdNumber('Math.abs(counter) + helper()'))", outline("counter = Math.abs(counter) + helper();"));
        assertRoundTrip("counter = Math.abs(counter) + helper();");
    }

    @Test
    public void aTextWithAnEscapeABlockCannotHoldIsKeptAsInlineJava() {
        String java = "name = \"tab\\u0009here\";";
        assertEquals("setVarString('name', asdString('\"tab\\u0009here\"'))", outline(java));
        assertRoundTrip(java);
    }

    @Test
    public void quotesAndBackslashesInTextAreKept() {
        assertRoundTrip("name = \"say \\\"hi\\\"\";");
        assertRoundTrip("name = \"line\\nbreak\";");
        assertRoundTrip("name = \"back\\\\slash\";");
        assertEquals("setVarString('name', 'say \"hi\"')", outline("name = \"say \\\"hi\\\"\";"));
    }

    @Test
    public void aSwitchIsKeptAsText() {
        String java = "switch (counter) {\n  case 1:\n    name = \"one\";\n    break;\n  default:\n    break;\n}";
        assertEquals("addSourceDirectly('" + java.replace("\n", "\\n") + "')", outline(java));
    }

    // ---- comments

    @Test
    public void commentsBetweenStatementsAreKept() {
        JavaToBlocks.Result result = convert("// first\ncounter++;\n// second\ncounter--;\n/* end */");
        assertEquals("addSourceDirectly('// first')\nincreaseInt('counter')\naddSourceDirectly('// second')\ndecreaseInt('counter')\naddSourceDirectly('/* end */')",
                SyncFixtures.outline(result.blocks(), false).trim());
        assertRoundTrip("// first\ncounter++;\n// second\ncounter--;\n/* end */");
    }

    @Test
    public void commentsInsideAStackAreKept() {
        String java = "if (flag) {\n  // inside\n  counter++;\n  // after\n}";
        assertEquals("if(getVar(flag))\n  addSourceDirectly('// inside')\n  increaseInt('counter')\n  addSourceDirectly('// after')", outline(java));
        assertRoundTrip(java);
    }

    @Test
    public void aCommentInAnEmptyStackIsNeverLost() {
        assertRoundTrip("if (flag) {\n  // todo\n}");
        assertRoundTrip("while (true) { /* nothing */ }");
    }

    @Test
    public void aCommentAfterAStatementOnTheSameLineIsKept() {
        assertRoundTrip("counter++; // bump");
    }

    // ---- guarantees

    @Test
    public void invalidJavaConvertsNothingAndSaysWhy() {
        JavaToBlocks.Result result = SyncFixtures.converter().convert("foo(;", List.of());
        assertFalse(result.ok());
        assertTrue(result.blocks().isEmpty());
        assertFalse(result.problems().isEmpty());
        assertEquals(1, result.problems().get(0).line());
    }

    @Test
    public void anEmptyEventHasNoBlocks() {
        JavaToBlocks.Result result = convert("");
        assertTrue(result.ok());
        assertTrue(result.blocks().isEmpty());
        assertTrue(result.sourceBlocks().isEmpty());
    }

    @Test
    public void blocksAlwaysGenerateTheCodeThatWasWritten() {
        String[] samples = {
                "SketchwareUtil.showMessage(getApplicationContext(), \"hi \" + name);",
                "if (counter > 5 && flag || !flag) {\n  counter = counter * 2;\n}",
                "for (int i = 0; i < 10; i++) {\n  items.add(String.valueOf(i));\n}",
                "try {\n  counter = Integer.parseInt(name);\n} catch (Exception e) {\n  counter = 0;\n}",
                "textview1.setText(String.valueOf((long)(counter)));",
                "name = name.concat(\"x\").trim().toUpperCase();",
                "data.put(\"k\", name);\nname = data.get(\"k\").toString();",
                "rows.get((int)(counter)).put(\"a\", \"b\");",
                "new Thread(new Runnable() {\n  @Override\n  public void run() {\n    counter++;\n  }\n}).start();",
        };
        for (String sample : samples) assertRoundTrip(sample);
    }

    @Test
    public void everyIdIsUniqueAndEveryLinkPointsAtABlock() {
        JavaToBlocks.Result result = convert("while (true) {\n  if (flag) {\n    counter = counter + 1;\n  } else {\n    name = \"x\";\n  }\n  doIt();\n}");
        List<String> ids = new ArrayList<>();
        for (BlockBean bean : result.blocks()) {
            assertFalse("duplicate id " + bean.id, ids.contains(bean.id));
            ids.add(bean.id);
        }
        for (BlockBean bean : result.blocks()) {
            for (int link : new int[]{bean.nextBlock, bean.subStack1, bean.subStack2}) {
                if (link >= 0) assertTrue("dangling link " + link, ids.contains(String.valueOf(link)));
            }
            for (String parameter : bean.parameters) if (parameter.startsWith("@")) assertTrue(ids.contains(parameter.substring(1)));
        }
        assertEquals("the first block is the first statement", "forever", result.blocks().get(0).opCode);
    }

    @Test
    public void everyBlockCarriesTheTextTheEditorShows() {
        JavaToBlocks.Result result = convert("counter = 1;\nSketchwareUtil.showMessage(getApplicationContext(), \"x\");");
        assertEquals("set %m.varInt to %d", result.blocks().get(0).spec);
        assertEquals("Toast %s", result.blocks().get(1).spec);
    }
}
