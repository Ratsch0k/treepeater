package treepeater;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;

import burp.api.montoya.core.ByteArray;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;

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

    public static Color uiBorderColor() {
        return UIManager.getColor("Separator.foreground");
    }

    /**
     * Styles a custom combo shell (panel + text field + arrow button). The panel carries {@code ComboBox.border}
     * and background; the field stays non-opaque so it does not paint over the border.
     */
    public static void applyComboBoxShellStyle(JPanel panel, JTextField field, boolean enabled) {
        Border border = UIManager.getBorder("ComboBox.border");
        if (border == null) {
            border =
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(uiBorderColor()),
                            BorderFactory.createEmptyBorder(2, 4, 2, 2));
        }
        panel.setBorder(border);
        panel.setOpaque(true);
        Color bg =
                enabled
                        ? UIManager.getColor("ComboBox.background")
                        : UIManager.getColor("ComboBox.disabledBackground");
        if (bg == null) {
            bg = flatPanelBackground();
        }
        panel.setBackground(bg);
        if (field != null) {
            field.setOpaque(false);
            field.setBackground(bg);
        }
    }

    public static Color flatPanelBackground() {
        return UIManager.getColor("Panel.background");
    }

    public static Color uiHoverColor() {
        return UIManager.getColor("Button.hoverBackground");
    }

    /**
     * Returns the slash path for a given node.
     * <p>
     * The slash path is a string that represents the path to the node from the root.
     * @param node the node to get the slash path for
     * @return the slash path for the node
     */
    public static String slashPathForNode(RequestTreeNode node) {
        return String.join("/", collectSlashPathParts(node, true));
    }

    /** Slash path of parent folders only (excludes the node's own name / tab title). */
    public static String parentSlashPathForNode(RequestTreeNode node) {
        return String.join("/", collectSlashPathParts(node, false));
    }

    private static final int SYNTHETIC_ROOT_ID = 0;

    private static List<String> collectSlashPathParts(TreepeaterNode start, boolean includeStart) {
        List<String> parts = new ArrayList<>();
        TreepeaterNode cur =
                includeStart ? start : start.getParent() instanceof TreepeaterNode tn ? tn : null;
        while (cur != null) {
            if (cur.getId() == SYNTHETIC_ROOT_ID) {
                break;
            }
            String name = cur.getName();
            parts.add(name != null ? name : "#" + cur.getId());
            cur = cur.getParent() instanceof TreepeaterNode p ? p : null;
        }
        Collections.reverse(parts);
        return parts;
    }

    /**
     * Truncates {@code path} to fit {@code maxWidthPx}, keeping the first and last segment when possible.
     */
    public static String truncatePathMiddle(String path, int maxWidthPx, FontMetrics fm) {
        if (path == null || path.isEmpty() || maxWidthPx <= 0 || fm == null) {
            return path != null ? path : "";
        }
        if (fm.stringWidth(path) <= maxWidthPx) {
            return path;
        }
        String[] parts = path.split("/", -1);
        if (parts.length <= 1) {
            return truncateEnd(path, maxWidthPx, fm);
        }

        String ellipsis = "/.../";
        String first = parts[0];
        String last = parts[parts.length - 1];
        while (true) {
            String candidate = first + ellipsis + last;
            if (fm.stringWidth(candidate) <= maxWidthPx) {
                return candidate;
            }
            if (first.length() > 1 && first.length() >= last.length()) {
                first = first.substring(0, first.length() - 1);
            } else if (last.length() > 1) {
                last = last.substring(0, last.length() - 1);
            } else if (first.length() > 1) {
                first = first.substring(0, first.length() - 1);
            } else {
                return truncateEnd(candidate, maxWidthPx, fm);
            }
        }
    }

    private static String truncateEnd(String text, int maxWidthPx, FontMetrics fm) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String ell = "...";
        int ellW = fm.stringWidth(ell);
        if (ellW >= maxWidthPx) {
            return "";
        }
        for (int len = text.length(); len > 0; len--) {
            String candidate = text.substring(0, len) + ell;
            if (fm.stringWidth(candidate) <= maxWidthPx) {
                return candidate;
            }
        }
        return "";
    }

    public static String decodeUtf8Strict(byte[] chunk) {
        if (chunk.length == 0) {
            return "";
        }
        CharsetDecoder dec =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return dec.decode(ByteBuffer.wrap(chunk)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * UTF-8 text of full wire bytes (request or response). Non-UTF-8 sequences yield a one-line placeholder.
     */
    public static String decodeWireBytesToDisplayString(ByteArray r) {
        byte[] raw = r.getBytes();
        if (raw.length == 0) {
            return "";
        }
        String t = decodeUtf8Strict(raw);
        if (t == null) {
            return "\u00abnon-UTF-8 wire, " + raw.length + " byte(s)\u00bb";
        }
        return t;
    }
}
