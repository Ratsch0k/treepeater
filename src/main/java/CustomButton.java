import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Flat button with reliable custom background and hover colors.
 * Designed to avoid Look & Feel rollover painting overriding custom colors.
 */
public class CustomButton extends JButton {
    private static final int ARC = 8;

    private String text;
    private final Color baseTextColor;
    private final Color baseBackgroundColor;
    private final Color hoverBackgroundColor;

    public CustomButton(String text, Color textColor, Color backgroundColor, Color hoverColor) {
        super(text);
        this.text = text;
        this.setText(text);

        this.baseTextColor = textColor;
        this.baseBackgroundColor = backgroundColor;
        this.hoverBackgroundColor = hoverColor;

        setForeground(this.baseTextColor);
        setRolloverEnabled(true);
        setMargin(new Insets(4, 12, 4, 12));
        setBorder(new EmptyBorder(4, 12, 4, 12));
        setPreferredSize(new Dimension(90, 28));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            FontMetrics fm = g2.getFontMetrics();

            Color fill = colorForState();
            Color border = fill.darker();

            int w = getWidth();
            int h = getHeight();

            int buttonWidth = getWidth();
            int buttonHeight = getHeight() + fm.getHeight();

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, buttonWidth - 1, buttonHeight - 1, ARC, ARC);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, buttonWidth - 1, buttonHeight - 1, ARC, ARC);

            if (this.text != null && !this.text.isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                int textX = (w - fm.stringWidth(text)) / 2;
                int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
                if (getModel().isPressed()) {
                    textX += 1;
                    textY += 1;
                }
                g2.drawString(text, textX, textY);
            }

        } finally {
            g2.dispose();
        }
    }

    private Color colorForState() {
        if (!isEnabled()) {
            return blend(baseBackgroundColor, uiDisabledBackground(), 0.45f);
        }
        if (getModel().isPressed() || getModel().isArmed()) {
            return hoverBackgroundColor.darker();
        }
        if (getModel().isRollover()) {
            return hoverBackgroundColor;
        }
        return baseBackgroundColor;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setForeground(baseTextColor);
        } else {
            Color disabledText = UIManager.getColor("Label.disabledForeground");
            setForeground(disabledText != null ? disabledText : blend(baseTextColor, Color.GRAY, 0.45f));
        }
        repaint();
    }

    private static Color uiDisabledBackground() {
        Color c = UIManager.getColor("Panel.background");
        if (c != null) {
            return c;
        }
        c = UIManager.getColor("control");
        return c != null ? c : Color.GRAY;
    }

    private static Color blend(Color a, Color b, float amountB) {
        float amount = Math.max(0f, Math.min(1f, amountB));
        float amountA = 1f - amount;
        int r = Math.round((a.getRed() * amountA) + (b.getRed() * amount));
        int g = Math.round((a.getGreen() * amountA) + (b.getGreen() * amount));
        int bl = Math.round((a.getBlue() * amountA) + (b.getBlue() * amount));
        return new Color(r, g, bl, 255);
    }
}
