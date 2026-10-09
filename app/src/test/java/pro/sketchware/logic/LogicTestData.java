package pro.sketchware.logic;

import java.util.Arrays;
import java.util.List;

/** Small helpers to write blocks the way the project stores them. */
final class LogicTestData {
    private LogicTestData() {
    }

    static LogicBlock b(int id, String op, String spec, int next, String... params) {
        return new LogicBlock(String.valueOf(id), op, spec, "", Arrays.asList(params), next, -1, -1);
    }

    static LogicBlock c(int id, String op, String spec, int next, int sub1, int sub2, String... params) {
        return new LogicBlock(String.valueOf(id), op, spec, "", Arrays.asList(params), next, sub1, sub2);
    }

    static LogicEvent event(String key, LogicBlock... blocks) {
        return new LogicEvent(key, List.of(blocks));
    }
}
