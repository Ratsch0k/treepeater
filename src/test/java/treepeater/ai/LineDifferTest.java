package treepeater.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LineDifferTest {

    @Test
    void nonAdjacentRemovalsGetEllipsisLineBetween() {
        String before = "a\nb\nc\nd\ne";
        String after = "a\nB\nc\nd\nE";
        LineDiffer.RedGreen rg = LineDiffer.lineDiffForDisplay(before, after);
        assertEquals("b\n…\ne", rg.removed());
        assertEquals("B\n…\nE", rg.added());
        LineDiffer.LineDiffData data = LineDiffer.lineDiffData(before, after);
        String red = LineDiffer.formatWithLineGutter(data.removed());
        assertTrue(red.contains("2 │ b"), red);
        assertTrue(red.contains("5 │ e"), red);
        assertTrue(red.contains("│ …"), red);
    }

    @Test
    void adjacentRemovalsNoEllipsisBetweenThem() {
        String before = "a\nb\nc";
        String after = "a\nX\nY";
        LineDiffer.RedGreen rg = LineDiffer.lineDiffForDisplay(before, after);
        assertEquals("b\nc", rg.removed());
        assertEquals("X\nY", rg.added());
        assertFalse(rg.removed().contains("…"));
    }

    @Test
    void singleHunkUnchanged() {
        String before = "x\ny\nz";
        String after = "x\nY\nz";
        LineDiffer.RedGreen rg = LineDiffer.lineDiffForDisplay(before, after);
        assertEquals("y", rg.removed());
        assertEquals("Y", rg.added());
    }

    @Test
    void unifiedInterleavesOldAndNew() {
        String before = "a\nb\nc\nd\ne";
        String after = "a\nB\nc\nd\nE";
        List<LineDiffer.UnifiedLineRow> u = LineDiffer.unifiedLineDiffData(before, after);
        assertEquals(5, u.size(), u::toString);
        assertEquals(
                1,
                u.stream().filter(x -> x instanceof LineDiffer.UnifiedLineRow.HunkSeparator).count(),
                u::toString);
        assertTrue(u.get(0) instanceof LineDiffer.UnifiedLineRow.Old, u::toString);
        assertTrue(u.get(1) instanceof LineDiffer.UnifiedLineRow.New, u::toString);
        assertTrue(u.get(2) instanceof LineDiffer.UnifiedLineRow.HunkSeparator, u::toString);
        assertTrue(u.get(3) instanceof LineDiffer.UnifiedLineRow.Old, u::toString);
        assertTrue(u.get(4) instanceof LineDiffer.UnifiedLineRow.New, u::toString);
    }
}
