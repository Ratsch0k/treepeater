package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-oriented LCS diffs of two multiline strings for red/green tool-card display: {@link #removed()} are lines
 * only in the first string, {@link #added()} only in the second. Consecutive diff lines that were not adjacent in
 * the source (skipped unchanged lines in between) are separated by a single "…" line on that side. Middle omitted
 * when {@link #LINE_DIFF_MAX_CELLS} is exceeded; both fields then hold the full before/after text.
 */
public final class LineDiffer {
    private static final int LINE_DIFF_MAX_CELLS = 1_200_000;

    /** One visual row in a single-side (removed or added) tool diff, before line-number or gutter formatting. */
    public sealed interface LineDiffRow permits LineDiffRow.Numbered, LineDiffRow.HunkSeparator, LineDiffRow.MiddleOmitted {
        /** A changed line: {@code line1Based} is 1-based in that side's file. */
        record Numbered(int line1Based, String text) implements LineDiffRow {
            public Numbered {
                if (line1Based < 1) {
                    throw new IllegalArgumentException("line1Based: " + line1Based);
                }
                if (text == null) {
                    text = "";
                }
            }
        }

        record HunkSeparator() implements LineDiffRow {}

        /**
         * Filler for {@link #truncateDisplayRowsForToolCard}; expands to a four-line "lines omitted" block when
         * {@linkplain #toPlainString(List) rendered as plain} or with {@linkplain #formatWithLineGutter(List) gutter}.
         */
        record MiddleOmitted(int lineCount) implements LineDiffRow {
            public MiddleOmitted {
                if (lineCount < 1) {
                    throw new IllegalArgumentException("lineCount: " + lineCount);
                }
            }
        }
    }

    public record RedGreen(String removed, String added) {
        public RedGreen {
            removed = removed != null ? removed : "";
            added = added != null ? added : "";
        }
    }

    public record LineDiffData(List<LineDiffRow> removed, List<LineDiffRow> added) {
        public LineDiffData {
            removed = removed != null ? List.copyOf(removed) : List.of();
            added = added != null ? List.copyOf(added) : List.of();
        }
    }

    /**
     * In-order, unified diff: each changed line in the “before” file is immediately followed by the corresponding
     * “after” line when both apply; non-adjacent change regions are separated by {@link UnifiedLineRow.HunkSeparator}.
     */
    public sealed interface UnifiedLineRow
            permits UnifiedLineRow.Old, UnifiedLineRow.New, UnifiedLineRow.HunkSeparator, UnifiedLineRow.MiddleOmitted {
        record Old(int line1Before, String text) implements UnifiedLineRow {
            public Old {
                if (line1Before < 1) {
                    throw new IllegalArgumentException("line1Before: " + line1Before);
                }
                if (text == null) {
                    text = "";
                }
            }
        }

        record New(int line1After, String text) implements UnifiedLineRow {
            public New {
                if (line1After < 1) {
                    throw new IllegalArgumentException("line1After: " + line1After);
                }
                if (text == null) {
                    text = "";
                }
            }
        }

        record HunkSeparator() implements UnifiedLineRow {}

        /**
         * Placeholder for {@link #truncateUnifiedForToolCard}; see {@link LineDiffRow.MiddleOmitted#MiddleOmitted}.
         */
        record MiddleOmitted(int rowCount) implements UnifiedLineRow {
            public MiddleOmitted {
                if (rowCount < 1) {
                    throw new IllegalArgumentException("rowCount: " + rowCount);
                }
            }
        }
    }

    public static RedGreen lineDiffForDisplay(String before, String after) {
        if (before == null) {
            before = "";
        }
        if (after == null) {
            after = "";
        }
        if (before.equals(after)) {
            return new RedGreen("", "");
        }
        String[] a0 = before.split("\\R", -1);
        String[] b0 = after.split("\\R", -1);
        if ((long) a0.length * (long) b0.length > LINE_DIFF_MAX_CELLS) {
            return new RedGreen(before, after);
        }
        LineDiffData d = lineDiffDataFromLcs(a0, b0);
        return new RedGreen(toPlainString(d.removed()), toPlainString(d.added()));
    }

    public static String toPlainString(List<LineDiffRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < rows.size(); k++) {
            if (k > 0) {
                b.append('\n');
            }
            b.append(plainForRow(rows.get(k)));
        }
        return b.toString();
    }

    /**
     * Renders a side of the diff with 1-based line numbers in a right-aligned column and a "│" separator, using a
     * monospaced layout. Hunk and middle-omitted rows use an empty number column.
     */
    public static String formatWithLineGutter(List<LineDiffRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        int w = 1;
        for (LineDiffRow r : rows) {
            if (r instanceof LineDiffRow.Numbered n) {
                w = Math.max(w, String.valueOf(n.line1Based()).length());
            }
        }
        w = Math.max(1, w);
        int finalW = w;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < rows.size(); k++) {
            if (k > 0) {
                b.append('\n');
            }
            b.append(switch (rows.get(k)) {
                case LineDiffRow.Numbered n -> String.format(
                        "%" + finalW + "d │ %s", n.line1Based(), n.text());
                case LineDiffRow.HunkSeparator() -> " ".repeat(finalW) + " │ " + "…";
                case LineDiffRow.MiddleOmitted mo -> {
                    var block = new StringBuilder();
                    block.append(" ".repeat(finalW))
                            .append(" │ …\n")
                            .append(" ".repeat(finalW))
                            .append(" │ 〔 ")
                            .append(mo.lineCount())
                            .append(" line(s) omitted 〕\n")
                            .append(" ".repeat(finalW))
                            .append(" │ …");
                    yield block.toString();
                }
            });
        }
        return b.toString();
    }

    /**
     * If there are more than {@code headRows} + {@code tailRows} rows, keeps the first and last row runs and
     * inserts a single {@link LineDiffRow.MiddleOmitted} between.
     */
    public static List<LineDiffRow> truncateDisplayRowsForToolCard(List<LineDiffRow> rows, int headRows, int tailRows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        if (rows.size() <= headRows + tailRows) {
            return List.copyOf(rows);
        }
        int omitted = rows.size() - headRows - tailRows;
        if (omitted < 1) {
            return List.copyOf(rows);
        }
        var out = new ArrayList<LineDiffRow>(headRows + 1 + tailRows);
        out.addAll(rows.subList(0, headRows));
        out.add(new LineDiffRow.MiddleOmitted(omitted));
        out.addAll(rows.subList(rows.size() - tailRows, rows.size()));
        return List.copyOf(out);
    }

    /**
     * Walks the LCS in forward (document) order, emitting an old line then a new line as they are produced, so
     * substitutions read as a typical text diff. Returns {@code null} when the LCS table would exceed {@link
     * #LINE_DIFF_MAX_CELLS} (use a two-block fallback in the UI).
     */
    public static List<UnifiedLineRow> unifiedLineDiffData(String before, String after) {
        if (before == null) {
            before = "";
        }
        if (after == null) {
            after = "";
        }
        if (before.equals(after)) {
            return List.of();
        }
        String[] a = before.split("\\R", -1);
        String[] b = after.split("\\R", -1);
        if ((long) a.length * (long) b.length > LINE_DIFF_MAX_CELLS) {
            return null;
        }
        int[][] lcs = computeLcsTable(a, b);
        return withHunkSeparators(
                normalizeUnifiedOldBeforeNew(
                        unifiedInterleavedFromLcsBacktrack(a, b, lcs)));
    }

    public static List<UnifiedLineRow> truncateUnifiedForToolCard(
            List<UnifiedLineRow> rows, int headRows, int tailRows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        if (rows.size() <= headRows + tailRows) {
            return List.copyOf(rows);
        }
        int omitted = rows.size() - headRows - tailRows;
        if (omitted < 1) {
            return List.copyOf(rows);
        }
        var out = new ArrayList<UnifiedLineRow>(headRows + 1 + tailRows);
        out.addAll(rows.subList(0, headRows));
        out.add(new UnifiedLineRow.MiddleOmitted(omitted));
        out.addAll(rows.subList(rows.size() - tailRows, rows.size()));
        return List.copyOf(out);
    }

    /** Monospace gutter width (digits of line numbers) for {@link #formatGutterForUnifiedLine} / hunk/omit. */
    public static int unifiedGutterWidth(List<UnifiedLineRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 1;
        }
        int w = 1;
        for (UnifiedLineRow r : rows) {
            switch (r) {
                case UnifiedLineRow.Old o -> w = Math.max(w, String.valueOf(o.line1Before()).length());
                case UnifiedLineRow.New n -> w = Math.max(w, String.valueOf(n.line1After()).length());
                case UnifiedLineRow.HunkSeparator() -> { }
                case UnifiedLineRow.MiddleOmitted mo -> { }
            }
        }
        return Math.max(1, w);
    }

    public static String formatGutterForUnifiedLine(int gutterWidth, int line1, String text) {
        if (line1 < 1) {
            return " ".repeat(gutterWidth) + " │ " + (text == null ? "" : text);
        }
        return String.format("%" + Math.max(1, gutterWidth) + "d │ %s", line1, text == null ? "" : text);
    }

    public static String formatGutterForUnifiedHunk(int gutterWidth) {
        return " ".repeat(Math.max(1, gutterWidth)) + " │ " + "…";
    }

    public static String formatGutterForUnifiedMiddleOmitted(int gutterWidth, int omittedRowCount) {
        int w = Math.max(1, gutterWidth);
        return " ".repeat(w)
                + " │ …\n"
                + " ".repeat(w)
                + " │ 〔 "
                + omittedRowCount
                + " line(s) omitted 〕\n"
                + " ".repeat(w)
                + " │ …";
    }

    /**
     * When the forward walk first inserts and then deletes at the same 1-based line, the LCS can prefer the insert
     * path; a typical file diff always shows the old line, then the new line, so we swap that pair in place.
     */
    private static List<UnifiedLineRow> normalizeUnifiedOldBeforeNew(List<UnifiedLineRow> rows) {
        if (rows == null || rows.size() < 2) {
            return rows == null ? List.of() : rows;
        }
        var a = new ArrayList<>(rows);
        for (int k = 0; k < a.size() - 1; k++) {
            if (a.get(k) instanceof UnifiedLineRow.New n
                    && a.get(k + 1) instanceof UnifiedLineRow.Old o) {
                if (n.line1After() == o.line1Before()) {
                    a.set(k, o);
                    a.set(k + 1, n);
                }
            }
        }
        return List.copyOf(a);
    }

    /**
     * Same edit script as {@link #lineDiffDataFromLcsBacktrack} but a single time-ordered list: each delete/insert
     * step from the (n,m) walk is prepended, matching the per-side {@link LineDiffRow.Numbered} construction.
     */
    private static List<UnifiedLineRow> unifiedInterleavedFromLcsBacktrack(
            String[] a, String[] b, int[][] lcs) {
        int n = a.length;
        int m = b.length;
        int i = n;
        int j = m;
        var out = new ArrayList<UnifiedLineRow>();
        while (i > 0 && j > 0) {
            if (a[i - 1].equals(b[j - 1])) {
                i--;
                j--;
            } else if (lcs[i - 1][j] >= lcs[i][j - 1]) {
                i--;
                out.add(0, new UnifiedLineRow.Old(i + 1, a[i]));
            } else {
                j--;
                out.add(0, new UnifiedLineRow.New(j + 1, b[j]));
            }
        }
        while (i > 0) {
            i--;
            out.add(0, new UnifiedLineRow.Old(i + 1, a[i]));
        }
        while (j > 0) {
            j--;
            out.add(0, new UnifiedLineRow.New(j + 1, b[j]));
        }
        return out;
    }

    private static List<UnifiedLineRow> withHunkSeparators(List<UnifiedLineRow> raw) {
        if (raw == null || raw.isEmpty()) {
            return raw == null ? List.of() : List.copyOf(raw);
        }
        int lastBefore1 = 0;
        int lastAfter1 = 0;
        var out = new ArrayList<UnifiedLineRow>(raw.size() + 4);
        for (UnifiedLineRow x : raw) {
            if (x instanceof UnifiedLineRow.Old o) {
                if (lastBefore1 > 0 && o.line1Before() > lastBefore1 + 1) {
                    out.add(new UnifiedLineRow.HunkSeparator());
                }
                out.add(o);
                lastBefore1 = o.line1Before();
            } else if (x instanceof UnifiedLineRow.New n) {
                if (hunkBeforeNewInUnified(out, lastAfter1, n)) {
                    out.add(new UnifiedLineRow.HunkSeparator());
                }
                out.add(n);
                lastAfter1 = n.line1After();
            }
        }
        return out;
    }

    private static boolean hunkBeforeNewInUnified(
            List<UnifiedLineRow> out, int lastAfter1, UnifiedLineRow.New n) {
        if (n.line1After() <= lastAfter1 + 1) {
            return false;
        }
        if (!out.isEmpty() && out.get(out.size() - 1) instanceof UnifiedLineRow.Old o) {
            if (o.line1Before() == n.line1After()) {
                return false;
            }
        }
        if (lastAfter1 > 0) {
            return true;
        }
        if (n.line1After() <= 1) {
            return false;
        }
        if (out.isEmpty()) {
            return true;
        }
        return false;
    }

    public static LineDiffData lineDiffData(String before, String after) {
        if (before == null) {
            before = "";
        }
        if (after == null) {
            after = "";
        }
        if (before.equals(after)) {
            return new LineDiffData(List.of(), List.of());
        }
        String[] a = before.split("\\R", -1);
        String[] b = after.split("\\R", -1);
        int n = a.length;
        int m = b.length;
        if ((long) n * (long) m > LINE_DIFF_MAX_CELLS) {
            return new LineDiffData(
                    fullFileRows(a), fullFileRows(b));
        }
        return lineDiffDataFromLcs(a, b);
    }

    private static LineDiffData lineDiffDataFromLcs(String[] a, String[] b) {
        return lineDiffDataFromLcsBacktrack(a, b, computeLcsTable(a, b));
    }

    private static int[][] computeLcsTable(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                lcs[i + 1][j + 1] =
                        a[i].equals(b[j])
                                ? 1 + lcs[i][j]
                                : Math.max(lcs[i][j + 1], lcs[i + 1][j]);
            }
        }
        return lcs;
    }

    private static LineDiffData lineDiffDataFromLcsBacktrack(
            String[] a, String[] b, int[][] lcs) {
        int n = a.length;
        int m = b.length;
        int i = n;
        int j = m;
        List<Integer> remIndex = new ArrayList<>();
        List<String> rem = new ArrayList<>();
        List<Integer> addIndex = new ArrayList<>();
        List<String> add = new ArrayList<>();
        while (i > 0 && j > 0) {
            if (a[i - 1].equals(b[j - 1])) {
                i--;
                j--;
            } else if (lcs[i - 1][j] >= lcs[i][j - 1]) {
                i--;
                remIndex.add(0, i);
                rem.add(0, a[i]);
            } else {
                j--;
                addIndex.add(0, j);
                add.add(0, b[j]);
            }
        }
        while (i > 0) {
            i--;
            remIndex.add(0, i);
            rem.add(0, a[i]);
        }
        while (j > 0) {
            j--;
            addIndex.add(0, j);
            add.add(0, b[j]);
        }
        return new LineDiffData(
                rowsFromIndexedLinesWithHunkGaps(rem, remIndex),
                rowsFromIndexedLinesWithHunkGaps(add, addIndex));
    }

    private static List<LineDiffRow> fullFileRows(String[] lines) {
        var out = new ArrayList<LineDiffRow>(lines.length);
        for (int k = 0; k < lines.length; k++) {
            out.add(new LineDiffRow.Numbered(k + 1, lines[k]));
        }
        return out;
    }

    private static List<LineDiffRow> rowsFromIndexedLinesWithHunkGaps(
            List<String> lines, List<Integer> lineIndices0Based) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        if (lineIndices0Based == null || lineIndices0Based.isEmpty() || lineIndices0Based.size() != lines.size()) {
            var a = new ArrayList<LineDiffRow>(lines.size());
            for (int k = 0; k < lines.size(); k++) {
                a.add(new LineDiffRow.Numbered(k + 1, lines.get(k)));
            }
            return a;
        }
        var out = new ArrayList<LineDiffRow>(lines.size() + 4);
        for (int k = 0; k < lines.size(); k++) {
            if (k > 0) {
                int prev = lineIndices0Based.get(k - 1);
                int cur = lineIndices0Based.get(k);
                if (cur - prev > 1) {
                    out.add(new LineDiffRow.HunkSeparator());
                }
            }
            int idx0 = lineIndices0Based.get(k);
            out.add(new LineDiffRow.Numbered(idx0 + 1, lines.get(k)));
        }
        return out;
    }

    private static String plainForRow(LineDiffRow row) {
        return switch (row) {
            case LineDiffRow.Numbered n -> n.text();
            case LineDiffRow.HunkSeparator() -> "…";
            case LineDiffRow.MiddleOmitted mo -> "…\n〔 "
                    + mo.lineCount()
                    + " line(s) omitted 〕\n…";
        };
    }

    private LineDiffer() {}
}
