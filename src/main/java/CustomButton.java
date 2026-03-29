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


public class CustomButton extends JButton {
    private Color defaultForeground;
    private Color defaultBackgroundColor;
    private Color defaultHoverBackgroundColor;

    private int arc = 6;
    private String text;
    private int verticalPadding = 6;

    public CustomButton(String text) {
        super(text);
        this.text = text;
        this.setText(text);

        // Set default colors
        this.defaultForeground = UIManager.getColor("Button.foreground");
        this.defaultBackgroundColor = UIManager.getColor("Button.background");
        this.defaultHoverBackgroundColor = UIManager.getColor("Button.hoverBackground");

        setForeground(defaultForeground);
        setRolloverEnabled(true);
        setPreferredSize(new Dimension(90, 32));
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    }

    @Override
    public void setForeground(Color color) {
        super.setForeground(color);
        if (color != null) {
            defaultForeground = color;
        }
    }

    @Override
    public void setBackground(Color color) {
        super.setBackground(color);
        if (color != null) {
            defaultBackgroundColor = color;
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
            defaultHoverBackgroundColor = color;
        }
    }

    @Override
    public void setText(String text) {
        this.text = text;
        super.setText(text);
    }

    public void setVerticalPadding(int padding) {
        this.verticalPadding = padding;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updatePreferredSize();
    }

    private void updatePreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        if (fm == null) return;
        Insets insets = getInsets();
        int height = fm.getHeight() + 2 * verticalPadding + insets.top + insets.bottom;
        int width = Math.max(90, fm.stringWidth(text != null ? text : "") + insets.left + insets.right);
        setPreferredSize(new Dimension(width, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            FontMetrics fm = g2.getFontMetrics();
            Insets insets = getInsets();
            int w = getWidth();
            int h = getHeight();

            int innerX = insets.left;
            int innerY = insets.top;
            int innerW = Math.max(0, w - insets.left - insets.right);
            int innerH = Math.max(0, h - insets.top - insets.bottom);

            Color fill = this.getColorForState();
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, this.getArc(), this.getArc());
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
            return defaultBackgroundColor.darker();
        }
        if (getModel().isPressed() || getModel().isArmed()) {
            return defaultHoverBackgroundColor.darker();
        }
        if (getModel().isRollover()) {
            return defaultHoverBackgroundColor;
        }
        return defaultBackgroundColor;
    }

    private Color getForegroundForState() {
        if (!isEnabled()) {
            return this.defaultForeground.darker();
        }
        return this.defaultForeground;
    }
}
