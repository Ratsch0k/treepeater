package treepeater.diff;

import java.awt.Color;

import javax.swing.UIManager;

/** Theme-aware red/green diff highlight colors (matches AI tool-card palette). */
public final class DiffTheme {

    private DiffTheme() {}

    public static boolean isDarkLaf() {
        return Boolean.TRUE.equals(UIManager.getBoolean("laf.dark"));
    }

    public static Color removedBackground() {
        return isDarkLaf() ? new Color(64, 36, 36) : new Color(255, 230, 230);
    }

    public static Color removedForeground() {
        return isDarkLaf() ? new Color(255, 170, 170) : new Color(140, 30, 30);
    }

    public static Color addedBackground() {
        return isDarkLaf() ? new Color(36, 64, 40) : new Color(230, 255, 230);
    }

    public static Color addedForeground() {
        return isDarkLaf() ? new Color(170, 255, 190) : new Color(25, 100, 45);
    }

    public static Color normalForeground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : (isDarkLaf() ? Color.LIGHT_GRAY : Color.DARK_GRAY);
    }

    public static Color panelBackground() {
        Color c = UIManager.getColor("Colors.ui.background.1");
        if (c != null) {
            return c;
        }
        c = UIManager.getColor("control");
        return c != null ? c : Color.WHITE;
    }

    public static String removedSpanStyle() {
        return rgbStyle(removedForeground(), removedBackground());
    }

    public static String addedSpanStyle() {
        return rgbStyle(addedForeground(), addedBackground());
    }

    public static String normalSpanStyle() {
        return "color:" + rgb(normalForeground());
    }

    public static String preWrapperStyle() {
        return "font-family:monospace;white-space:pre-wrap;word-wrap:break-word;margin:0;padding:2px;"
                + "background-color:" + rgb(panelBackground());
    }

    private static String rgb(Color c) {
        return "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
    }

    private static String rgbStyle(Color fg, Color bg) {
        return "background-color:" + rgb(bg) + ";color:" + rgb(fg);
    }
}
