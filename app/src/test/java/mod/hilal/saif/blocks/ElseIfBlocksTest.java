package mod.hilal.saif.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * The "else if" and "else" blocks are meant to be stacked under an "if": their generated code has to
 * chain into valid Java, and they must not clash with other built-in blocks.
 */
public class ElseIfBlocksTest {
    private static HashMap<String, Object> block(String name) {
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        BlocksHandler.builtInBlocks(blocks);
        for (HashMap<String, Object> block : blocks) {
            if (name.equals(block.get("name"))) {
                return block;
            }
        }
        return null;
    }

    /** Fills the %s placeholders in order, the way the code generator does for these blocks. */
    private static String generate(String name, String... values) {
        String code = (String) block(name).get("code");
        for (String value : values) {
            code = code.replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(value));
        }
        return code;
    }

    @Test
    public void elseIfIsAConditionalBlockWithOneBranch() {
        HashMap<String, Object> elseIf = block("elseIf");
        assertNotNull(elseIf);
        assertEquals("c", elseIf.get("type"));
        assertEquals("else if %b", elseIf.get("spec"));
        assertEquals("else if (x > 3) {\r\ndoThing();\r\n}", generate("elseIf", "x > 3", "doThing();"));
    }

    @Test
    public void elseIsABranchWithoutCondition() {
        HashMap<String, Object> otherwise = block("else");
        assertNotNull(otherwise);
        assertEquals("c", otherwise.get("type"));
        assertEquals("else", otherwise.get("spec"));
        assertEquals("else {\r\nfallback();\r\n}", generate("else", "fallback();"));
    }

    @Test
    public void stackedBlocksFormOneIfElseIfElseChain() {
        String chain = String.join("\r\n",
                "if (a == 1) {\r\none();\r\n}",
                generate("elseIf", "a == 2", "two();"),
                generate("elseIf", "a == 3", "three();"),
                generate("else", "none();"));
        assertEquals("""
                if (a == 1) {\r
                one();\r
                }\r
                else if (a == 2) {\r
                two();\r
                }\r
                else if (a == 3) {\r
                three();\r
                }\r
                else {\r
                none();\r
                }""".replace("\r\n", "\n").replace("\n", "\r\n"), chain);
    }

    @Test
    public void placeholdersMatchTheBlocksInputs() {
        // elseIf: the condition, then the branch. else: the branch only.
        assertEquals(2, count((String) block("elseIf").get("code"), "%s"));
        assertEquals(1, count((String) block("else").get("code"), "%s"));
    }

    @Test
    public void blockNamesAreUnique() {
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        BlocksHandler.builtInBlocks(blocks);
        Set<Object> names = new HashSet<>();
        for (HashMap<String, Object> block : blocks) {
            assertTrue("Duplicate built-in block: " + block.get("name"), names.add(block.get("name")));
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
        }
        return count;
    }
}
