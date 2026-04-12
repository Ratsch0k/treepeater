package treepeater.components;

import javax.swing.JPanel;
import javax.swing.border.Border;

import treepeater.Treepeater;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A JPanel with rounded corners and a customizable border color.
 */
public class RoundedPanel extends JPanel {

    private int cornerArc = 10; // Default arc for rounded corners
    private Color borderColor = null; // If null, fallback to darkened background color
    private Color backgroundColor = null;

    public RoundedPanel() {
        super();
        this.backgroundColor = getBackground();
    }

    public RoundedPanel(int arc) {
        super();
        this.cornerArc = arc;
        this.backgroundColor = getBackground();
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        repaint();
    }

    public Color getBackgroundColor() {
        return this.backgroundColor;
    }

    /**
     * Set the arc/dimension of the rounded corners.
     * @param arc the radius of the arc
     */
    public void setCornerArc(int arc) {
        this.cornerArc = arc;
        repaint();
    }

    /**
     * Get the arc/dimension of the rounded corners.
     * @return the arc radius
     */
    public int getCornerArc() {
        return this.cornerArc;
    }

    /**
     * Set the color of the rounded border.
     * @param color the border color
     */
    public void setBorderColor(Color color) {
        this.borderColor = color;
        repaint();
    }

    /**
     * Gets the currently set border color, or null if not set.
     * @return the border color
     */
    public Color getBorderColor() {
        return this.borderColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        Color bg = this.getBackgroundColor();
        if (bg != null) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, width - 1, height - 1, cornerArc, cornerArc);
        }

        g2.dispose();

        // JPanel's UI delegate fills a rectangle when opaque, which would undo rounded corners.
        boolean opaque = isOpaque();
        setOpaque(false);
        super.paintComponent(g);
        setOpaque(opaque);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Border border = getBorder();
        if (border != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();


            Color drawColor = borderColor != null ? borderColor : getForeground();
            g2.setColor(drawColor);

            // Draw rounded border rectangle just inside full bounds
            g2.drawRoundRect(0, 0, width - 1, height - 1, cornerArc, cornerArc);

            g2.dispose();
        } else {
            super.paintBorder(g);
        }
    }
}