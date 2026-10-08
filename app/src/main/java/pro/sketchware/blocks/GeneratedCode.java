package pro.sketchware.blocks;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;

import a.a.a.Fx;
import a.a.a.Lx;
import a.a.a.jq;

/**
 * The Java an event's blocks turn into, as the build generates it. Used to show the code next to the blocks.
 */
public final class GeneratedCode {
    private GeneratedCode() {
    }

    /**
     * @param activityName the generated class the event belongs to
     * @param buildConfig  the project's build settings the generator reads
     * @param isFragment   whether the screen is a fragment (changes what {@code $context} stands for)
     * @return the formatted code, or an empty string when there are no blocks
     */
    public static String forEvent(String activityName, @Nullable jq buildConfig, ArrayList<BlockBean> blocks,
                                  boolean isViewBindingEnabled, boolean isFragment) {
        String code = new Fx(activityName, buildConfig, blocks, isViewBindingEnabled).a();
        if (code.isBlank()) {
            return "";
        }
        code = code.replaceAll("\\$className", activityName)
                .replaceAll("\\$context", isFragment ? "getContext()" : activityName + ".this");
        return Lx.j(code, false);
    }
}
