package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import treepeater.tree.TreeTitleMatcher.Match;
import treepeater.tree.TreeTitleMatcher.Query;
import treepeater.tree.TreeTitleMatcher.Tier;

class TreeTitleMatcherTest {
    private static Optional<Match> match(String query, String title) {
        return TreeTitleMatcher.parse(query).match(title);
    }

    @Test
    void blankQueryNeverMatches() {
        Query blank = TreeTitleMatcher.parse("   ");
        assertTrue(blank.isEmpty());

        for (String title : new String[] { "", "Login", "anything" }) {
            assertTrue(blank.match(title).isEmpty());
        }
        assertTrue(TreeTitleMatcher.parse(null).isEmpty());
    }

    @Test
    void substringTierCoversWholeQueryCaseInsensitively() {
        Match m = match("login", "GET /LOGIN page").orElseThrow();

        assertEquals(Tier.SUBSTRING, m.tier());
        assertEquals(1, m.ranges().size());
        assertRange(m.ranges().get(0), 5, 10);
    }

    @Test
    void substringRangeSpansWholeQuery() {
        Match m = match("log in", "user LOG IN flow").orElseThrow();

        assertEquals(Tier.SUBSTRING, m.tier());
        assertRange(m.ranges().get(0), 5, 11);
    }

    @Test
    void singleTokenQueryGoesStraightToSubstringAndNeverTokenTier() {
        Match m = match("bearer", "Bearer token").orElseThrow();

        assertEquals(Tier.SUBSTRING, m.tier());
    }

    @Test
    void tokenTierRequiresEveryTokenInAnyOrder() {
        Match m = match("token bearer", "A Bearer authorization token").orElseThrow();

        assertEquals(Tier.TOKENS, m.tier());
        // First occurrence of each token, sorted and non-overlapping.
        assertEquals(2, m.ranges().size());
        assertRange(m.ranges().get(0), 2, 8);
        assertRange(m.ranges().get(1), 23, 28);
    }

    @Test
    void tokenTierFailsWhenOneTokenIsMissing() {
        assertTrue(match("token refresh", "A Bearer authorization token").isEmpty());
    }

    @Test
    void tokenRangesMergeAdjacentOccurrences() {
        // The whole query "ab cd" never occurs contiguously, but both tokens do, adjacent to
        // each other, so their ranges merge into one.
        Match m = match("ab cd", "xx abcd yy").orElseThrow();

        assertEquals(Tier.TOKENS, m.tier());
        assertEquals(1, m.ranges().size());
        assertRange(m.ranges().get(0), 3, 7);
    }

    @Test
    void tokenRangesMergeDistinctOccurrences() {
        Match m = match("ab cd", "xx ab yy cd").orElseThrow();

        assertEquals(Tier.TOKENS, m.tier());
        assertEquals(2, m.ranges().size());
        assertRange(m.ranges().get(0), 3, 5);
        assertRange(m.ranges().get(1), 9, 11);
    }

    @Test
    void fuzzyMatchesInOrderSubsequence() {
        Match m = match("lgn", "Login").orElseThrow();

        assertEquals(Tier.FUZZY, m.tier());
        // l, g, n at 0, 2, 4.
        assertEquals(3, m.ranges().size());
        assertRange(m.ranges().get(0), 0, 1);
        assertRange(m.ranges().get(1), 2, 3);
        assertRange(m.ranges().get(2), 4, 5);
    }

    @Test
    void fuzzyCompactnessRejectsScatteredCharacters() {
        // "abc" needs a window of 12 characters here (a...b...c), far above 2 * 3.
        assertTrue(match("abc", "alpha beta charlie delta").isEmpty());
    }

    @Test
    void fuzzyIsSkippedBelowThreeCharacters() {
        // "ab" as a subsequence of "aXb" (window 3 <= 4) would fit compactness, but short
        // queries never reach the fuzzy tier.
        assertTrue(match("ab", "aXb").isEmpty());
    }

    @Test
    void fuzzyCompactWindowAcceptsNearbyCharacters() {
        Match m = match("usr", "Current user session").orElseThrow();

        assertEquals(Tier.FUZZY, m.tier());
    }

    @Test
    void substringTierWinsWhenWholeQueryOccurs() {
        assertEquals(Tier.SUBSTRING, match("token bear", "token bearer").orElseThrow().tier());
    }

    @Test
    void tokensTierAppliesWhenWholeQueryOrderDiffers() {
        assertEquals(Tier.TOKENS, match("bear token", "bearer token auth").orElseThrow().tier());
    }

    @Test
    void specialCharactersMatchVerbatim() {
        assertTrue(match("[tr]", "Request [TR] copied").isPresent());
        assertTrue(match("a/b", "path a/b").isPresent());
        assertTrue(match("a/b", "path b/a").isEmpty());
    }

    @Test
    void nullOrEmptyTitleNeverMatches() {
        assertTrue(match("x", "").isEmpty());
        assertTrue(match("x", null).isEmpty());
    }

    @Test
    void parseLowercasesAndSplitsTokens() {
        Query q = TreeTitleMatcher.parse("  Foo   BAR ");

        assertEquals("Foo   BAR", q.raw());
        assertEquals("foo   bar", q.normalized());
        assertEquals(List.of("foo", "bar"), q.tokens());
        assertEquals("foobar", q.compact());
    }

    @Test
    void fuzzyAdjacentMatchedCharactersMergeIntoOneRange() {
        // "a" and "b" are adjacent (0..2) while "c" sits at 3, so the first two ranges merge.
        Match m = match("abc", "abxc").orElseThrow();

        assertEquals(Tier.FUZZY, m.tier());
        assertEquals(2, m.ranges().size());
        assertRange(m.ranges().get(0), 0, 2);
        assertRange(m.ranges().get(1), 3, 4);
    }

    private static void assertRange(int[] range, int start, int end) {
        assertEquals(start, range[0], "range start");
        assertEquals(end, range[1], "range end");
    }
}
