package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

import treepeater.tree.HighlightedTitleLabel.Segment;

class HighlightedTitleLabelTest {
    private final HighlightedTitleLabel label = new HighlightedTitleLabel();

    HighlightedTitleLabelTest() {
        this.label.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
    }

    @Test
    void segmentsSplitAtRangeBoundaries() {
        this.label.setTitle("Bearer token", List.of(new int[] { 0, 6 }), false);

        List<Segment> segments = this.label.buildSegments(this.label.getFontMetrics(this.label.getFont()),
                this.label.getFontMetrics(this.label.getFont().deriveFont(Font.BOLD)));

        assertEquals(2, segments.size());
        assertEquals("Bearer", segments.get(0).text());
        assertTrue(segments.get(0).bold());
        assertEquals(" token", segments.get(1).text());
        assertTrue(!segments.get(1).bold());
    }

    @Test
    void boldSegmentIsWiderThanTheSamePlainSegment() {
        Font plain = this.label.getFont();
        Font bold = plain.deriveFont(Font.BOLD);
        int plainWidth = this.label.getFontMetrics(plain).stringWidth("Bearer");
        int boldWidth = this.label.getFontMetrics(bold).stringWidth("Bearer");

        assertTrue(boldWidth >= plainWidth);
    }

    @Test
    void preferredWidthWithBoldSegmentsExceedsPlainWidth() {
        this.label.setTitle("Bearer token", null, false);
        int plainWidth = this.label.getPreferredSize().width;

        this.label.setTitle("Bearer token", List.of(new int[] { 0, 6 }), false);
        int boldWidth = this.label.getPreferredSize().width;

        assertTrue(boldWidth > plainWidth);
    }

    @Test
    void plainTitleUsesNormalJLabelPreferredSize() {
        this.label.setTitle("Bearer token", null, false);
        JLabel reference = new JLabel("Bearer token");
        reference.setFont(this.label.getFont());

        assertEquals(reference.getPreferredSize().height, this.label.getPreferredSize().height);
    }

    @Test
    void truncationEndsWithEllipsisAndNeverSplitsACharacter() {
        this.label.setTitle("Bearer token", List.of(new int[] { 0, 6 }), false);
        FontMetricsPair fm = new FontMetricsPair(this.label);

        List<Segment> full = this.label.buildSegments(fm.plain, fm.bold);
        List<Segment> fitted = this.label.fitSegmentsToWidth(full, full.get(0).width() - 2,
                fm.plain, fm.bold);

        assertEquals(1, fitted.size());
        String text = fitted.get(0).text();
        // Truncation ends with the ellipsis and cuts inside the bold segment.
        assertEquals("\u2026", text.substring(text.length() - 1));
        assertTrue(fitted.get(0).bold());
        assertTrue(fitted.get(0).width() <= full.get(0).width() - 2);
    }

    @Test
    void truncationFitsEverythingWhenWideEnough() {
        this.label.setTitle("Bearer token", List.of(new int[] { 0, 6 }), false);
        FontMetricsPair fm = new FontMetricsPair(this.label);
        List<Segment> full = this.label.buildSegments(fm.plain, fm.bold);

        int totalWidth = full.stream().mapToInt(Segment::width).sum();
        assertEquals(full, this.label.fitSegmentsToWidth(full, totalWidth, fm.plain, fm.bold));
    }

    @Test
    void truncationKeepsPlainSegmentsBeforeABoldOverflow() {
        // Bold range at the end: plain "long plain text " + bold "aaaaaaaa".
        String title = "long plain text aaaaaaaa";
        this.label.setTitle(title, List.of(new int[] { 16, 24 }), false);
        FontMetricsPair fm = new FontMetricsPair(this.label);
        List<Segment> full = this.label.buildSegments(fm.plain, fm.bold);

        int keptWidth = full.get(0).width();
        // Enough for the whole plain segment plus roughly two bold characters and the ellipsis.
        int avail = keptWidth + 2 * fm.bold.charWidth('a') + fm.bold.stringWidth("\u2026") + 2;
        List<Segment> fitted = this.label.fitSegmentsToWidth(full, avail, fm.plain, fm.bold);

        assertEquals(2, fitted.size());
        assertEquals(full.get(0), fitted.get(0));
        String cut = fitted.get(1).text();
        assertTrue(cut.endsWith("\u2026"));
        String keptBold = cut.substring(0, cut.length() - 1);
        assertTrue("aaaaaaaa".startsWith(keptBold));
        assertTrue(keptBold.length() >= 2);
        assertTrue(keptBold.length() < 8);
    }

    @Test
    void truncationOfSupplementaryCharactersKeepsPairsWhole() {
        String title = "\ud83d\ude00\ud83d\ude00\ud83d\ude00\ud83d\ude00x";
        this.label.setTitle(title, List.of(new int[] { 0, 2 }), false);
        FontMetricsPair fm = new FontMetricsPair(this.label);
        List<Segment> full = this.label.buildSegments(fm.plain, fm.bold);

        // Room for the whole first (bold) emoji plus roughly one more emoji and the ellipsis;
        // the remaining plain segment overflows and must be truncated.
        int emojiWidth = full.get(0).width();
        int avail = 2 * emojiWidth + fm.plain.stringWidth("\u2026") + 4;
        List<Segment> fitted = this.label.fitSegmentsToWidth(full, avail, fm.plain, fm.bold);

        assertEquals(2, fitted.size());
        assertEquals(full.get(0), fitted.get(0));
        String text = fitted.get(1).text();
        // The cut text ends with the ellipsis and keeps surrogate pairs whole (even char count).
        assertTrue(text.endsWith("\u2026"));
        String emojiPart = text.substring(0, text.length() - 1);
        assertEquals(0, emojiPart.length() % 2);
        assertTrue(emojiPart.length() >= 2);
        assertTrue(emojiPart.length() < title.length() - 1);
    }

    @Test
    void paintingIntoImageDoesNotThrow() {
        this.label.setTitle("Bearer token", List.of(new int[] { 0, 6 }), false);
        this.label.setSize(this.label.getPreferredSize());
        BufferedImage image = new BufferedImage(
                Math.max(1, this.label.getWidth()), Math.max(1, this.label.getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.label.paint(g2);

            // Muted variant too.
            this.label.setTitle("Bearer token", null, true);
            this.label.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    private record FontMetricsPair(java.awt.FontMetrics plain, java.awt.FontMetrics bold) {
        FontMetricsPair(HighlightedTitleLabel label) {
            this(label.getFontMetrics(label.getFont()),
                    label.getFontMetrics(label.getFont().deriveFont(Font.BOLD)));
        }
    }
}
