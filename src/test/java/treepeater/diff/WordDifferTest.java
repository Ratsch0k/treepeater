package treepeater.diff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WordDifferTest {

    @Test
    void identicalStringsHaveNoHighlights() {
        WordDiffer.SideBySideDiff d = WordDiffer.diff("GET /api HTTP/1.1", "GET /api HTTP/1.1");
        assertFalse(d.leftHtml().contains(DiffTheme.removedSpanStyle()));
        assertFalse(d.rightHtml().contains(DiffTheme.addedSpanStyle()));
        assertTrue(d.leftHtml().contains("GET"));
        assertTrue(d.rightHtml().contains("GET"));
    }

    @Test
    void wordSubstitutionHighlightsRemovedAndAdded() {
        WordDiffer.SideBySideDiff d = WordDiffer.diff("hello world", "hello earth");
        assertTrue(d.leftHtml().contains("world"));
        assertTrue(d.rightHtml().contains("earth"));
        assertTrue(d.leftHtml().contains("background-color"));
        assertTrue(d.rightHtml().contains("background-color"));
    }

    @Test
    void onlyChangedWordIsHighlightedOnMultilineHttp() {
        String a = "GET / HTTP/1.1\r\nHost: one\r\nCookie: a";
        String b = "GET / HTTP/1.1\r\nHost: two\r\nCookie: a";
        WordDiffer.SideBySideDiff d = WordDiffer.diff(a, b);

        int removed = d.leftHtml().indexOf(DiffTheme.removedSpanStyle());
        int added = d.rightHtml().indexOf(DiffTheme.addedSpanStyle());
        assertTrue(removed >= 0);
        assertTrue(added >= 0);

        String removedChunk = d.leftHtml().substring(removed, Math.min(removed + 120, d.leftHtml().length()));
        String addedChunk = d.rightHtml().substring(added, Math.min(added + 120, d.rightHtml().length()));
        assertTrue(removedChunk.contains("one"));
        assertTrue(addedChunk.contains("two"));
        assertFalse(removedChunk.contains("Host:"));
        assertFalse(addedChunk.contains("Host:"));

        int hostA = d.leftHtml().indexOf("Host:");
        int cookieA = d.leftHtml().indexOf("Cookie:");
        int brBetween = d.leftHtml().indexOf("<br>", hostA);
        assertTrue(hostA >= 0);
        assertTrue(cookieA > hostA);
        assertTrue(brBetween > hostA && brBetween < cookieA);
    }

    @Test
    void emptyVsNonEmpty() {
        WordDiffer.SideBySideDiff d = WordDiffer.diff("", "POST");
        assertTrue(d.leftHtml().contains("<pre"));
        assertTrue(d.rightHtml().contains("POST"));
    }

    @Test
    void whitespaceOnlyChange() {
        WordDiffer.SideBySideDiff d = WordDiffer.diff("a b", "a  b");
        assertTrue(d.leftHtml().contains("a"));
        assertTrue(d.rightHtml().contains("a"));
    }

    @Test
    void htmlEscapeEscapesSpecialChars() {
        assertTrue(WordDiffer.htmlEscape("<script>&\"</script>").contains("&lt;"));
        assertTrue(WordDiffer.htmlEscape("<script>&\"</script>").contains("&amp;"));
        assertTrue(WordDiffer.htmlEscape("a\nb").contains("<br>"));
    }

    @Test
    void addedLineDoesNotInsertExtraBreakOnUnchangedSide() {
        String a = "line1";
        String b = "line1\nline2";
        WordDiffer.SideBySideDiff d = WordDiffer.diff(a, b);
        assertFalse(d.leftHtml().contains("<br>"));
        assertTrue(d.rightHtml().contains("line2"));
        assertTrue(d.rightHtml().contains("<br>"));
    }
}
