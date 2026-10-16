package pro.sketchware.logic.sync;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;

/** Lets tests in other packages use the package-private fixtures. */
public final class SyncFixturesBridge {
    private SyncFixturesBridge() {
    }

    public static JavaToBlocks converter() {
        return SyncFixtures.converter();
    }

    public static ProjectContext context() {
        return SyncFixtures.context();
    }

    public static BlockSpecs.Strings strings() {
        return SyncFixtures.strings();
    }

    public static String generate(ArrayList<BlockBean> blocks) {
        return SyncFixtures.generate(blocks);
    }
}
