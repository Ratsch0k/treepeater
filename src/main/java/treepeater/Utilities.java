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
