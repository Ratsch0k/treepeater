package treepeater.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Title matching for the tree search field, in three tiers (best first):
 *
 * <ol>
 *   <li><b>Substring</b>: the whole trimmed query occurs in the title.</li>
 *   <li><b>Token AND</b>: only for multi-token queries; every whitespace-separated token occurs.</li>
 *   <b>Fuzzy</b>: in-order subsequence with a compactness bound (see {@link #FUZZY_MIN_CHARS} and
 *       {@link #FUZZY_MAX_WINDOW_FACTOR}).</li>
 * </ol>
 *
 * All matching is case-insensitive via {@link Locale#ROOT}. Highlight ranges refer to indices in the
 * original (non-lowercased) title; titles whose lowercase form has a different length (rare Unicode
 * case folding) still match but are returned without ranges.
 */
public final class TreeTitleMatcher {
    /** Minimum length (after whitespace removal) for fuzzy matching to be attempted. */
    public static final int FUZZY_MIN_CHARS = 3;

    /** A fuzzy match window may be at most this multiple of the query length. */
    public static final int FUZZY_MAX_WINDOW_FACTOR = 2;

    public enum Tier {
        SUBSTRING, TOKENS, FUZZY
    }

    /**
     * A successful match.
     *
     * @param tier  which tier produced the match; lower ordinal = better
     * @param ranges character ranges ({@code {start, end}} pairs, end exclusive) to draw bold;
     *               merged, sorted, non-overlapping
     */
    public record Match(Tier tier, List<int[]> ranges) {
    }

    /** A parsed search query; an empty query means "no filter". */
    public record Query(String raw, String normalized, List<String> tokens, String compact) {
        public boolean isEmpty() {
            return this.normalized.isEmpty();
        }

        /** @see TreeTitleMatcher#match */
        public Optional<Match> match(String title) {
            return TreeTitleMatcher.match(this, title);
        }
    }

    private TreeTitleMatcher() {
    }

    /**
     * Parses raw search text. Trims, lowercases with {@link Locale#ROOT} and splits on whitespace.
     */
    public static Query parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        String compact = String.join("", tokens);
        return new Query(trimmed, normalized, List.copyOf(tokens), compact);
    }

    /**
     * Matches a node title against the query, trying tiers in order. Returns empty for a blank
     * query or a non-matching title.
     */
    public static Optional<Match> match(Query query, String title) {
        if (query == null || query.isEmpty()) {
            return Optional.empty();
        }
        String safeTitle = title == null ? "" : title;
        Lowercased lower = lowerCase(safeTitle);
        String lc = lower.text;

        // 1. Substring: the whole normalized query occurs.
        int substringIndex = lc.indexOf(query.normalized());
        if (substringIndex >= 0) {
            return Optional.of(new Match(Tier.SUBSTRING,
                    lower.withRanges(List.of(new int[] { substringIndex, substringIndex + query.normalized().length() }))));
        }

        // 2. Token AND: every token occurs (only meaningful with >= 2 tokens; a single token is
        // already covered by the substring tier).
        if (query.tokens().size() >= 2) {
            List<int[]> ranges = new ArrayList<>();
            boolean all = true;
            for (String token : query.tokens()) {
                int idx = lc.indexOf(token);
                if (idx < 0) {
                    all = false;
                    break;
                }
                ranges.add(new int[] { idx, idx + token.length() });
            }
            if (all) {
                return Optional.of(new Match(Tier.TOKENS, lower.withRanges(mergeRanges(ranges))));
            }
        }

        // 3. Fuzzy: in-order subsequence with a compactness bound.
        if (query.compact().length() >= FUZZY_MIN_CHARS) {
            List<int[]> ranges = fuzzyMatch(query.compact(), lc);
            if (ranges != null) {
                return Optional.of(new Match(Tier.FUZZY, lower.withRanges(ranges)));
            }
        }

        return Optional.empty();
    }

    /**
     * Finds a fuzzy subsequence match of {@code queryChars} in {@code lc}: a forward scan to the
     * first position where the last query character matches, then a backward scan from there for
     * the tightest start. Rejects windows longer than {@link #FUZZY_MAX_WINDOW_FACTOR} x query
     * length. Returns per-character ranges of the matched characters, or {@code null}.
     */
    private static List<int[]> fuzzyMatch(String queryChars, String lc) {
        int qlen = queryChars.length();

        // Forward scan: first end position where all query characters have matched in order.
        int q = 0;
        int end = -1;
        for (int i = 0; i < lc.length() && q < qlen; i++) {
            if (lc.charAt(i) == queryChars.charAt(q)) {
                q++;
                end = i;
            }
        }
        if (q < qlen) {
            return null;
        }

        // Backward scan from that end: tightest start for the same end position.
        List<int[]> ranges = new ArrayList<>(qlen);
        int r = qlen - 1;
        int start = end;
        for (int i = end; i >= 0 && r >= 0; i--) {
            if (lc.charAt(i) == queryChars.charAt(r)) {
                ranges.add(new int[] { i, i + 1 });
                r--;
                start = i;
            }
        }
        if (r >= 0) {
            return null;
        }

        if (end - start + 1 > FUZZY_MAX_WINDOW_FACTOR * qlen) {
            return null;
        }
        return mergeRanges(ranges);
    }

    /** Sorts ranges by start and merges overlapping or adjacent ones. */
    static List<int[]> mergeRanges(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return List.of();
        }
        List<int[]> sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : sorted) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new int[] { range[0], range[1] });
            }
        }
        return List.copyOf(merged);
    }

    /**
     * Lowercases a title, tracking whether index positions are unchanged so highlight ranges can
     * be mapped back onto the original string.
     */
    private static Lowercased lowerCase(String title) {
        String lc = title.toLowerCase(Locale.ROOT);
        return new Lowercased(lc, lc.length() == title.length());
    }

    private record Lowercased(String text, boolean sameLength) {
        List<int[]> withRanges(List<int[]> ranges) {
            return this.sameLength ? ranges : List.of();
        }
    }
}
