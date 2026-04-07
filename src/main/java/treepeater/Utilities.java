package treepeater;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JTree;

public class Utilities {
    public static Color adjustBrightness(Color color, float factor) {
        return new Color(
            clamp(color.getRed() * factor),
            clamp(color.getGreen() * factor),
            clamp(color.getBlue() * factor),
            color.getAlpha()
        );
    }
    /**
     * Adjusts the brightness of a given {@link Color} by a specified factor.
     * <p>
     * Each color component (red, green, blue) is multiplied by {@code factor}
     * and then clamped between 0 and 255. The alpha remains unchanged.
     *
     * @param color the original color to adjust
     * @param factor the factor by which to adjust each color component
     *               (values &lt; 1.0 darken, values &gt; 1.0 brighten)
     * @return a new {@link Color} with adjusted brightness, or the original color if {@code color} is null
     */

    public static Color interpolateColor(Color a, Color b, float t) {
        return new Color(
            clamp(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            clamp(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t),
            clamp(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)
        );
    }

    public static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    public static int rowAtPoint(JTree tree, Point p) {
        int row = tree.getClosestRowForLocation(p.x, p.y);
        if (row < 0) {
            return -1;
        }
        Rectangle r = tree.getRowBounds(row);
        return (r != null && r.contains(p)) ? row : -1;
    }
}
