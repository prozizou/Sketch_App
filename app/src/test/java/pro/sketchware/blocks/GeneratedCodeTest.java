package pro.sketchware.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class GeneratedCodeTest {
    private static BlockBean block(String id, String opCode, int next) {
        BlockBean bean = new BlockBean(id, "", " ", opCode);
        bean.nextBlock = next;
        return bean;
    }

    @Test
    public void noBlocksGiveNoCode() {
        assertEquals("", GeneratedCode.forEvent("MainActivity", null, new ArrayList<>(), false, false));
    }

    @Test
    public void blocksTurnIntoTheirJavaInOrder() {
        BlockBean loop = new BlockBean("1", "", "c", "forever");
        loop.subStack1 = 2;
        loop.nextBlock = 3;
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(loop);
        blocks.add(block("2", "break", -1));
        blocks.add(block("3", "break", -1));

        String code = GeneratedCode.forEvent("MainActivity", null, blocks, false, false);

        assertTrue(code, code.contains("while(true)"));
        assertTrue(code, code.indexOf("while") < code.lastIndexOf("break;"));
    }

    @Test
    public void aConditionShowsAsAnIfStatement() {
        BlockBean cond = new BlockBean("1", "if %b then", "c", "if");
        cond.parameters.add("true");
        cond.subStack1 = 2;
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(cond);
        blocks.add(block("2", "break", -1));

        String code = GeneratedCode.forEvent("MainActivity", null, blocks, false, false);

        assertTrue(code, code.contains("if (true)"));
        assertTrue(code, code.contains("break;"));
    }
}
