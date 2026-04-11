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

    /**
     * Converts a Color to a hex string representation (e.g., "#RRGGBB" or "#AARRGGBB" if alpha is not 255).
     *
     * @param color the Color to convert
     * @return the hex string representation of the color
     */
    public static String colorToHex(Color color) {
        if (color == null) throw new IllegalArgumentException("Color is null");
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = color.getAlpha();
        if (a == 255) {
            return String.format("#%02X%02X%02X", r, g, b);
        } else {
            return String.format("#%02X%02X%02X%02X", a, r, g, b);
        }
    }

    /**
     * Parses a hex color string (e.g., "#RRGGBB" or "#AARRGGBB") to a Color.
     *
     * @param hex the hex string representation of the color
     * @return the Color object, or null if input is invalid
     */
    public static Color hexToColor(String hex) {
        if (hex == null) throw new IllegalArgumentException("Hex string is null");
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            if (value.length() == 6) {
                // RRGGBB
                int r = Integer.parseInt(value.substring(0, 2), 16);
                int g = Integer.parseInt(value.substring(2, 4), 16);
                int b = Integer.parseInt(value.substring(4, 6), 16);
                return new Color(r, g, b);
            } else if (value.length() == 8) {
                // AARRGGBB
                int a = Integer.parseInt(value.substring(0, 2), 16);
                int r = Integer.parseInt(value.substring(2, 4), 16);
                int g = Integer.parseInt(value.substring(4, 6), 16);
                int b = Integer.parseInt(value.substring(6, 8), 16);
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex color string: " + hex);
        }
        throw new IllegalArgumentException("Invalid hex color string: " + hex);
    }

}
