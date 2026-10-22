package treepeater.tree;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Label that renders its title with selected character ranges drawn in a native bold font
 * (real fonts, not HTML) and optionally in a muted color. Used for search highlighting in the
 * tree: matched ranges bold, nodes inside a matching folder muted, plain otherwise.
 *
 * <p>Extending {@link JLabel} keeps font, colors, theme updates and the row-height calculation
 * in {@link CustomTreeCell} working. With no bold ranges and not muted, painting falls back to
 * normal {@link JLabel} behavior.
 */
public class HighlightedTitleLabel extends JLabel {
    private static final String ELLIPSIS = "\u2026";

    /** One run of text drawn with a single font (plain or bold). */
    record Segment(String text, boolean bold, int width) {
    }

    private String titleText = "";
    private List<int[]> boldRanges = List.of();
    private boolean muted;
    private boolean plainPainting = true;

    private Font cachedBaseFont;
    private Font boldFont;

    public HighlightedTitleLabel() {
        this.setOpaque(false);
    }

    /**
     * Sets the title, the character ranges (in title coordinates, merged as needed) to draw
     * bold, and whether the whole title is drawn muted. Also feeds {@link #setText} for
     * accessibility and tooltips.
     */
    public void setTitle(String text, List<int[]> boldRanges, boolean muted) {
        this.titleText = text != null ? text : "";
        this.boldRanges = clampRanges(boldRanges);
        this.muted = muted;
        this.plainPainting = this.boldRanges.isEmpty() && !muted;
        super.setText(this.titleText);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // The theme/L&F font may have changed; rebuild the derived bold font lazily.
        this.boldFont = null;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        if (this.plainPainting || this.titleText.isEmpty()) {
            return preferred;
        }
        int width = this.getInsets().left + this.getInsets().right;
        for (Segment segment : this.buildSegments(this.plainMetrics(), this.boldMetrics())) {
            width += segment.width();
        }
        return new Dimension(width, preferred.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (this.plainPainting || this.titleText.isEmpty()) {
            super.paintComponent(g);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Font plainFont = this.getFont();
            FontMetrics plainFm = this.plainMetrics();
            FontMetrics boldFm = this.boldMetrics();

            Insets insets = this.getInsets();
            int availWidth = this.getWidth() - insets.left - insets.right;
            List<Segment> segments =
                    this.fitSegmentsToWidth(this.buildSegments(plainFm, boldFm), availWidth, plainFm, boldFm);

            g2.setColor(this.muted ? this.mutedColor() : this.getForeground());
            int maxAscent = Math.max(plainFm.getAscent(), boldFm.getAscent());
            int maxDescent = Math.max(plainFm.getDescent(), boldFm.getDescent());
            int baseline = (this.getHeight() + maxAscent - maxDescent) / 2;
            int x = insets.left;
            for (Segment segment : segments) {
                if (segment.text().isEmpty()) {
                    continue;
                }
                g2.setFont(segment.bold() ? this.boldFont() : plainFont);
                BasicGraphicsUtils.drawString(this, g2, segment.text(), x, baseline);
                x += segment.width();
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Splits the title into plain/bold runs at the bold range boundaries. Exposed
     * package-private for tests.
     */
    List<Segment> buildSegments(FontMetrics plainFm, FontMetrics boldFm) {
        List<Segment> segments = new ArrayList<>();
        int plainStart = 0;
        for (int[] range : this.boldRanges) {
            if (range[0] > plainStart) {
                this.addSegment(segments, plainStart, range[0], false, plainFm);
            }
            this.addSegment(segments, range[0], range[1], true, boldFm);
            plainStart = range[1];
        }
        if (plainStart < this.titleText.length()) {
            this.addSegment(segments, plainStart, this.titleText.length(), false, plainFm);
        }
        return segments;
    }

    private void addSegment(List<Segment> segments, int start, int end, boolean bold, FontMetrics fm) {
        if (end <= start) {
            return;
        }
        String text = this.titleText.substring(start, end);
        segments.add(new Segment(text, bold, width(fm, text)));
    }

    /**
     * Truncates the segments to {@code availWidth}: the segment that no longer fits is cut at
     * the last character that still fits (never splitting a surrogate pair) and ends with an
     * ellipsis in the same font. Exposed package-private for tests.
     */
    List<Segment> fitSegmentsToWidth(List<Segment> segments, int availWidth, FontMetrics plainFm,
            FontMetrics boldFm) {
        int total = 0;
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            if (total + segment.width() > availWidth) {
                FontMetrics fm = segment.bold() ? boldFm : plainFm;
                return truncate(segments, index, segment, fm, availWidth - total);
            }
            total += segment.width();
        }
        return segments;
    }

    private List<Segment> truncate(List<Segment> segments, int overflowIndex, Segment overflow,
            FontMetrics fm, int avail) {
        List<Segment> fitted = new ArrayList<>(segments.subList(0, overflowIndex));
        int ellipsisWidth = width(fm, ELLIPSIS);
        if (avail <= ellipsisWidth) {
            if (ellipsisWidth <= avail) {
                fitted.add(new Segment(ELLIPSIS, overflow.bold(), ellipsisWidth));
            }
            return fitted;
        }
        String text = overflow.text();
        int limit = avail - ellipsisWidth;
        // Walk code points so a supplementary character is never split in half.
        int end = 0;
        int used = 0;
        while (end < text.length()) {
            int next = text.offsetByCodePoints(end, 1);
            int charWidth = width(fm, text.substring(end, next));
            if (used + charWidth > limit) {
                break;
            }
            used += charWidth;
            end = next;
        }
        if (end > 0) {
            String kept = text.substring(0, end);
            fitted.add(new Segment(kept + ELLIPSIS, overflow.bold(), used + ellipsisWidth));
        } else {
            fitted.add(new Segment(ELLIPSIS, overflow.bold(), ellipsisWidth));
        }
        return fitted;
    }

    private FontMetrics plainMetrics() {
        return this.getFontMetrics(this.getFont());
    }

    private FontMetrics boldMetrics() {
        return this.getFontMetrics(this.boldFont());
    }

    private Font boldFont() {
        Font base = this.getFont();
        if (this.boldFont == null || this.cachedBaseFont != base) {
            this.boldFont = base.deriveFont(Font.BOLD);
            this.cachedBaseFont = base;
        }
        return this.boldFont;
    }

    /**
     * Muted text color, resolved at paint time so theme switches apply: the L&F's disabled
     * foreground, or a 50% blend of the label foreground and tree background when unavailable.
     */
    private Color mutedColor() {
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            return muted;
        }
        Color foreground = UIManager.getColor("Label.foreground");
        Color background = UIManager.getColor("Tree.background");
        if (foreground == null || background == null) {
            return this.getForeground();
        }
        return new Color(
                (foreground.getRed() + background.getRed()) / 2,
                (foreground.getGreen() + background.getGreen()) / 2,
                (foreground.getBlue() + background.getBlue()) / 2,
                Math.max(foreground.getAlpha(), background.getAlpha()));
    }

    private int width(FontMetrics fm, String text) {
        return (int) Math.ceil(BasicGraphicsUtils.getStringWidth(this, fm, text));
    }

    /** Clamps, sorts, merges and copies caller-provided ranges to title bounds. */
    private List<int[]> clampRanges(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return List.of();
        }
        int length = this.titleText.length();
        List<int[]> valid = new ArrayList<>();
        for (int[] range : ranges) {
            if (range != null && range.length == 2 && range[0] >= 0 && range[1] > range[0]) {
                valid.add(new int[] { Math.min(range[0], length), Math.min(range[1], length) });
            }
        }
        return TreeTitleMatcher.mergeRanges(valid);
    }
}
