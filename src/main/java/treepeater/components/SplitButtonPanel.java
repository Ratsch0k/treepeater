package treepeater.components;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class SplitButtonPanel extends JPanel {
    private final int arc = 10;

    public SplitButtonPanel() {
        setOpaque(false);
        // Give the outline room to draw.
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    }

    private static Color resolveBorderColor() {
        Color c =
                UIManager.getColor("Separator.foreground") != null ? UIManager.getColor("Separator.foreground") :
                UIManager.getColor("Component.borderColor") != null ? UIManager.getColor("Component.borderColor") :
                UIManager.getColor("controlShadow");
        if (c != null) {
            return c;
        }
        return new Color(0, 0, 0, 80);
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

                Color line = resolveBorderColor();
                g2.setColor(line);
                g2.drawLine(dividerX, 2, dividerX, height - 3);
            }

            // Draw the rounded outline last so it stays visible over hover backgrounds.
            g2.setColor(resolveBorderColor());
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}
