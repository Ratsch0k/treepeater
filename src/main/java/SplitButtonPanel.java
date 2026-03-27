import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

class SplitButtonPanel extends JPanel {
    private final Color borderColor;
    private final Color dividerColor;
    private final int arc = 10;

    SplitButtonPanel(Color borderColor, Color dividerColor) {
        this.borderColor = borderColor;
        this.dividerColor = dividerColor;
        setOpaque(false);
        // Give the outline room to draw.
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
    }

    @Override
    protected void paintChildren(java.awt.Graphics g) {
        super.paintChildren(g);

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new java.awt.BasicStroke(1.2f));

            int width = getWidth();
            int height = getHeight();

            if (getComponentCount() >= 2) {
                Component nav = getComponent(0);
                int dividerX = nav.getX() + nav.getWidth();
                dividerX = Math.max(2, Math.min(width - 3, dividerX));

                g2.setColor(dividerColor);
                g2.drawLine(dividerX, 2, dividerX, height - 3);
            }

            // Draw the rounded outline last so it stays visible over hover backgrounds.
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}
