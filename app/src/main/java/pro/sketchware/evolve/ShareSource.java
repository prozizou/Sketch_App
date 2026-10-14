package pro.sketchware.evolve;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;

/**
 * Something the open screen can share: what it is, how to call it in the chooser, and its content.
 *
 * @param kind   what is shared
 * @param origin where it comes from, shown before the name (My Block Collection, This event, More blocks...)
 * @param name   the suggested name
 * @param blocks the blocks, for the block kinds (null for XML)
 * @param spec   a more block's spec ("" otherwise)
 * @param xml    the XML, for {@link SharedKind#VIEW} ("" otherwise)
 */
public record ShareSource(SharedKind kind, String origin, String name, ArrayList<BlockBean> blocks, String spec, String xml) {

    public static ShareSource ofBlocks(String origin, String name, ArrayList<BlockBean> blocks) {
        return new ShareSource(SharedKind.BLOCKS, origin, name, blocks, "", "");
    }

    public static ShareSource ofMoreBlock(String origin, String name, String spec, ArrayList<BlockBean> blocks) {
        return new ShareSource(SharedKind.MOREBLOCK, origin, name, blocks, spec, "");
    }

    public static ShareSource ofXml(String origin, String name, String xml) {
        return new ShareSource(SharedKind.VIEW, origin, name, null, "", xml);
    }
}
