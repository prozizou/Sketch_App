package pro.sketchware.logic;

import java.util.List;

/**
 * The blocks of one event of a screen. Like the code generator, the first block is where the event starts;
 * blocks that are not connected to it are kept by the editor but never run.
 *
 * @param key the event's key, like {@code button1_onClick} or {@code onCreate_initializeLogic}
 */
public record LogicEvent(String key, List<LogicBlock> blocks) {
    public LogicEvent {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
