package treepeater.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Centered, word-wrapped, muted hint shown in place of a component that has nothing to display. */
public class EmptyStateHint extends JPanel {
    private static final int PREFERRED_WIDTH = 240;
    private static final int PADDING = 16;

    private final JTextPane text = new JTextPane();

    public EmptyStateHint(String message) {
        super(new GridBagLayout());
        this.text.setText(message);
        this.text.setEditable(false);
        this.text.setFocusable(false);
        this.text.setOpaque(false);
        this.text.setHighlighter(null);
        this.text.setCaret(new DefaultCaret() {
            @Override
            public void paint(java.awt.Graphics g) {
            }
        });
        this.text.setBorder(BorderFactory.createEmptyBorder());
        this.text.setInheritsPopupMenu(true);

        StyledDocument doc = this.text.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, doc.getLength(), center, false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(PADDING, PADDING, PADDING, PADDING);
        this.add(this.text, gbc);

        this.applyColors();
    }

    /** Fixed preferred width so the message wraps instead of widening its container to fit one line. */
    @Override
    public Dimension getPreferredSize() {
        int innerWidth = PREFERRED_WIDTH - 2 * PADDING;
        this.text.setSize(innerWidth, Short.MAX_VALUE);
        int innerHeight = this.text.getPreferredSize().height;
        return new Dimension(PREFERRED_WIDTH, innerHeight + 2 * PADDING);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.text != null) {
            this.applyColors();
        }
    }

    private void applyColors() {
        Color c = UIManager.getColor("Label.disabledForeground");
        if (c == null) {
            c = Color.GRAY;
        }
        this.text.setForeground(c);
        this.text.setFont(UIManager.getFont("Label.font"));
    }
}
