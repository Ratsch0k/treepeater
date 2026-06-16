package treepeater.diff;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;

import treepeater.Treepeater;
import treepeater.Utilities;

/**
 * Read-only side-by-side diff view with synchronized vertical scrolling.
 */
public final class SideBySideDiffPane extends JPanel {

    private final JTextPane leftPane;
    private final JTextPane rightPane;
    private final JScrollPane leftScroll;
    private final JScrollPane rightScroll;
    private boolean syncingScroll;

    public SideBySideDiffPane(String leftLabel, String rightLabel) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder());

        this.leftPane = createDiffPane();
        this.rightPane = createDiffPane();
        this.leftScroll = wrapScrollPane(this.leftPane);
        this.rightScroll = wrapScrollPane(this.rightPane);

        JPanel leftCol = labeledColumn(leftLabel, this.leftScroll);
        JPanel rightCol = labeledColumn(rightLabel, this.rightScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, rightCol);
        split.setResizeWeight(0.5);
        split.setDividerLocation(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);

        add(split, BorderLayout.CENTER);

        installSyncScroll(this.leftScroll, this.rightScroll);
        installSyncScroll(this.rightScroll, this.leftScroll);

        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this);
        }
    }

    public void setDiff(WordDiffer.SideBySideDiff diff) {
        applyHtml(this.leftPane, diff != null ? diff.leftHtml() : emptyHtml());
        applyHtml(this.rightPane, diff != null ? diff.rightHtml() : emptyHtml());
        SwingUtilities.invokeLater(() -> {
            this.leftScroll.getVerticalScrollBar().setValue(0);
            this.rightScroll.getVerticalScrollBar().setValue(0);
        });
    }

    public void refreshTheme() {
        ColorAware.applyPaneTheme(this.leftPane);
        ColorAware.applyPaneTheme(this.rightPane);
        var border = BorderFactory.createLineBorder(Utilities.uiBorderColor());
        this.leftScroll.setBorder(border);
        this.rightScroll.setBorder(border);
    }

    private static JScrollPane wrapScrollPane(JTextPane pane) {
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(Utilities.uiBorderColor()));
        return scroll;
    }

    private static JTextPane createDiffPane() {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setFocusable(true);
        pane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        pane.setFont(mono);
        ColorAware.applyPaneTheme(pane);
        applyHtml(pane, emptyHtml());
        return pane;
    }

    private static void applyHtml(JTextPane pane, String html) {
        pane.setText(html);
        if (pane.getDocument() instanceof HTMLDocument doc) {
            StyleSheet sheet = doc.getStyleSheet();
            sheet.addRule("body { margin: 0; padding: 0; }");
            sheet.addRule("pre { margin: 0; }");
        }
    }

    private static String emptyHtml() {
        return "<html><body><pre style=\"" + DiffTheme.preWrapperStyle() + "\"></pre></body></html>";
    }

    private static JPanel labeledColumn(String label, Component content) {
        JPanel col = new JPanel(new BorderLayout(0, 2));
        col.setOpaque(false);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.X_AXIS));
        north.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        north.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, lbl.getFont().getSize2D()));
        north.add(lbl);
        col.add(north, BorderLayout.NORTH);
        col.add(content, BorderLayout.CENTER);
        return col;
    }

    private void installSyncScroll(JScrollPane source, JScrollPane target) {
        source.getVerticalScrollBar()
                .addAdjustmentListener(
                        new AdjustmentListener() {
                            @Override
                            public void adjustmentValueChanged(AdjustmentEvent e) {
                                if (SideBySideDiffPane.this.syncingScroll) {
                                    return;
                                }
                                int value = source.getVerticalScrollBar().getValue();
                                int targetValue = target.getVerticalScrollBar().getValue();
                                if (value != targetValue) {
                                    SideBySideDiffPane.this.syncingScroll = true;
                                    target.getVerticalScrollBar().setValue(value);
                                    SideBySideDiffPane.this.syncingScroll = false;
                                }
                            }
                        });
    }

    private static final class ColorAware {
        private ColorAware() {}

        static void applyPaneTheme(JTextPane pane) {
            pane.setBackground(DiffTheme.panelBackground());
            pane.setForeground(DiffTheme.normalForeground());
            pane.setCaretColor(UIManager.getColor("TextArea.caretForeground"));
        }
    }
}
