package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
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
public class BlockJsonTest {
    private static ArrayList<BlockBean> sample() {
        return SyncFixtures.converter().convert("if (flag) {\n  name = \"a\";\n}\ncounter = counter + 1;", List.of()).blocks();
    }

    @Test
    public void blocksSurviveAJsonRoundTrip() {
        ArrayList<BlockBean> blocks = sample();
        ArrayList<BlockBean> back = BlockJson.fromJson(BlockJson.toJson(blocks));
        assertEquals(BlockJson.hash(blocks), BlockJson.hash(back));
        assertEquals(blocks.size(), back.size());
        assertEquals(SyncFixtures.generate(blocks), SyncFixtures.generate(back));
    }

    @Test
    public void theHashIgnoresColoursAndOrder() {
        ArrayList<BlockBean> blocks = sample();
        ArrayList<BlockBean> other = BlockJson.copyOf(blocks);
        for (BlockBean bean : other) bean.color = 0xffabcdef;
        java.util.Collections.reverse(other);
        assertEquals(BlockJson.hash(blocks), BlockJson.hash(other));
    }

    @Test
    public void theHashSeesAChangedParameter() {
        ArrayList<BlockBean> blocks = sample();
        ArrayList<BlockBean> other = BlockJson.copyOf(blocks);
        BlockBean withParameters = other.stream().filter(b -> b.parameters.size() >= 2).findFirst().get();
        withParameters.parameters.set(1, "different");
        assertNotEquals(BlockJson.hash(blocks), BlockJson.hash(other));
    }

    @Test
    public void aCopyIsIndependent() {
        ArrayList<BlockBean> blocks = sample();
        ArrayList<BlockBean> copy = BlockJson.copyOf(blocks);
        copy.get(0).parameters.add("x");
        assertNotEquals(blocks.get(0).parameters.size(), copy.get(0).parameters.size());
    }

    @Test
    public void textThatIsNotBlocksIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BlockJson.fromJson("{not json"));
        assertThrows(IllegalArgumentException.class, () -> BlockJson.fromJson("[{\"spec\":\"x\"}]"));
        assertTrue(BlockJson.fromJson("[]").isEmpty());
        assertTrue(BlockJson.fromJson("null").isEmpty());
    }

    @Test
    public void lineEndingsDoNotChangeATextHash() {
        assertEquals(BlockJson.hash("a\r\nb"), BlockJson.hash("a\nb"));
        assertNotEquals(BlockJson.hash("a"), BlockJson.hash("b"));
    }
}
