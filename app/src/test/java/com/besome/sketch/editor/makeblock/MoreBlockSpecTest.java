package com.besome.sketch.editor.makeblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.editor.makeblock.MoreBlockSpec.Draft;
import com.besome.sketch.editor.makeblock.MoreBlockSpec.Param;

import org.junit.Test;

import java.util.List;

public class MoreBlockSpecTest {

    @Test
    public void specKeepsNameThenParametersInOrder() {
        List<Param> params = List.of(
                new Param("s", "prefix"),
                new Param("t", "then"),
                new Param("d", "count"),
                new Param("b", "flag"),
                new Param("m.view", "target"),
                new Param("m.varMap", "data"));
        assertEquals("show %s.prefix then %d.count %b.flag %m.view.target %m.varMap.data",
                MoreBlockSpec.buildSpec("show", params));
    }

    @Test
    public void specWithoutParametersIsJustTheName() {
        assertEquals("reset", MoreBlockSpec.buildSpec("reset", List.of()));
    }

    @Test
    public void reorderingChangesTheSpec() {
        Param a = new Param("s", "a");
        Param b = new Param("d", "b");
        assertEquals("f %s.a %d.b", MoreBlockSpec.buildSpec("f", List.of(a, b)));
        assertEquals("f %d.b %s.a", MoreBlockSpec.buildSpec("f", List.of(b, a)));
    }

    @Test
    public void customTypesAreValidatedLikeTheEngineExpects() {
        assertTrue(MoreBlockSpec.isValidCustomType("m.view"));
        assertTrue(MoreBlockSpec.isValidCustomType("l.myList"));
        assertFalse(MoreBlockSpec.isValidCustomType("view"));
        assertFalse(MoreBlockSpec.isValidCustomType("%m.view"));
        assertFalse(MoreBlockSpec.isValidCustomType("m."));
        assertFalse(MoreBlockSpec.isValidCustomType("x.view"));
        assertFalse(MoreBlockSpec.isValidCustomType(""));
        assertFalse(MoreBlockSpec.isValidCustomType(null));
    }

    @Test
    public void takenNamesIgnoreLabelsAndTheEditedEntry() {
        List<Param> params = List.of(new Param("s", "a"), new Param("t", "label"), new Param("d", "b"));
        assertEquals(List.of("a", "b"), MoreBlockSpec.takenParameterNames(params, -1));
        assertEquals(List.of("b"), MoreBlockSpec.takenParameterNames(params, 0));
    }

    @Test
    public void draftSurvivesEncodingIncludingAwkwardCharacters() {
        Draft draft = new Draft("na\\me[a|Map]", "radio_mb_type_map",
                List.of(new Param("s", "a\u001Fb"), new Param("t", ""), new Param("m.view", "v")), true);
        assertEquals(draft, Draft.decode(draft.encode()));
    }

    @Test
    public void emptyDraftRoundTrips() {
        Draft draft = new Draft("", "radio_mb_type_void", List.of(), false);
        assertEquals(draft, Draft.decode(draft.encode()));
    }

    @Test
    public void garbageIsNotADraft() {
        assertNull(Draft.decode(null));
        assertNull(Draft.decode(""));
        assertNull(Draft.decode("hello"));
        assertNull(Draft.decode("v1"));
        assertNull(Draft.decode("v1\u001Fname\u001Ftype\u001F0\u001Fodd"));
    }
}
