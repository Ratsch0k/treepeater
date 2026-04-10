package treepeater.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Rounded button that paints itself. {@link BasicButtonUI#installDefaults} calls
 * {@link #setBackground} / {@link #setForeground} with theme colors; those calls must not
 * overwrite colors set by application code. While the look-and-feel is installing
 * ({@link #updateUI}), we skip updating the palette used for {@link #paintComponent}.
 */
public class CustomButton extends JButton {

    private Color defaultForeground;
    private Color defaultBackgroundColor;
    private Color defaultHoverBackgroundColor;

    private int arc = 6;
    private String text;

    /** True only during {@code super.updateUI()} so LAF {@code setBackground} does not clobber app colors. */
    private boolean lafInstalling;

    public CustomButton(String text) {
        super(text);
        this.text = text;
        this.setText(text);

        // Set default colors
        this.defaultForeground = UIManager.getColor("Button.foreground");
        this.defaultBackgroundColor = UIManager.getColor("Button.background");
        this.defaultHoverBackgroundColor = UIManager.getColor("Button.hoverBackground");

        setForeground(this.defaultForeground);
        setRolloverEnabled(true);
        setPreferredSize(new Dimension(86, 22));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
    }

    @Override
    public void updateUI() {
        this.lafInstalling = true;
        try {
            super.updateUI();
        } finally {
            this.lafInstalling = false;
        }
    }

    @Override
    public void setForeground(Color color) {
        super.setForeground(color);
        if (!this.lafInstalling && color != null) {
            this.defaultForeground = color;
        }
    }

    @Override
    public void setBackground(Color color) {
        super.setBackground(color);
        if (!this.lafInstalling && color != null) {
            this.defaultBackgroundColor = color;
        }
    }

    @Override
    public void setBorder(Border border) {
        if (!this.lafInstalling && border != null) {
            super.setBorder(border);
        }
    }

    public void setArc(int arc) {
        this.arc = arc;
    }

    public int getArc() {
        return this.arc;
    }

    public void setHoverBackground(Color color) {
        if (color != null) {
            this.defaultHoverBackgroundColor = color;
        }
    }

    @Override
    public void setText(String text) {
        this.text = text;
        super.setText(text);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            FontMetrics fm = g2.getFontMetrics();
            Insets insets = this.getInsets();
            int w = this.getWidth();
            int h = this.getHeight();

            int innerX = insets.left;
            int innerY = insets.top;
            int innerW = Math.max(0, w - insets.left - insets.right);
            int innerH = Math.max(0, h - insets.top - insets.bottom);

            Color fill = this.getColorForState();
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, this.arc, this.arc);
            }

            if (this.text != null && !this.text.isEmpty()) {
                g2.setFont(this.getFont());
                g2.setColor(this.getForegroundForState());
                int textX = innerX + (innerW - fm.stringWidth(this.text)) / 2;
                int textY = innerY + (innerH - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(this.text, textX, textY);
            }

            Border border = this.getBorder();
            if (border != null) {
                border.paintBorder(this, g2, 0, 0, w, h);
            }

        } finally {
            g2.dispose();
        }
    }

    private Color getColorForState() {
        if (!isEnabled()) {
            return this.defaultBackgroundColor.darker();
        }
        if (getModel().isPressed() || getModel().isArmed()) {
            return this.defaultHoverBackgroundColor.darker();
        }
        if (getModel().isRollover()) {
            return this.defaultHoverBackgroundColor;
        }
        return this.defaultBackgroundColor;
    }

    private Color getForegroundForState() {
        if (!isEnabled()) {
            return this.defaultForeground.darker();
        }
        return this.defaultForeground;
    }
}
