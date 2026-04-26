package treepeater.ai;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import javax.swing.UIManager;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Converts markdown text to a themed HTML document suitable for display in a Swing {@link javax.swing.JEditorPane}.
 * Colors and fonts are pulled from {@link UIManager} at render time so the output adapts to FlatLaf theme changes.
 */
public final class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    private MarkdownRenderer() {}

    /** Parses {@code markdown} and returns a full {@code <html>} document with inline CSS themed from UIManager. */
    public static String renderToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        String bodyHtml = RENDERER.render(document);
        return wrapWithStyles(bodyHtml);
    }

    private static String wrapWithStyles(String bodyHtml) {
        Font labelFont = UIManager.getFont("Label.font");
        String fontFamily = labelFont != null ? labelFont.getFamily() : "SansSerif";
        int fontSize = labelFont != null ? labelFont.getSize() : 13;

        Color fg = UIManager.getColor("Label.foreground");
        Color codeBg = UIManager.getColor("Colors.ui.background.3");
        Color mutedFg = UIManager.getColor("Label.disabledForeground");
        Color linkColor = UIManager.getColor("Component.linkColor");
        if (linkColor == null) {
            linkColor = new Color(0x589df6);
        }

        String fgHex = toHex(fg != null ? fg : Color.WHITE);
        String codeBgHex = toHex(codeBg != null ? codeBg : new Color(0x3c3f41));
        String mutedHex = toHex(mutedFg != null ? mutedFg : Color.GRAY);
        String linkHex = toHex(linkColor);

        int h1 = Math.round(fontSize * 1.5f);
        int h2 = Math.round(fontSize * 1.3f);
        int h3 = Math.round(fontSize * 1.15f);

        StringBuilder css = new StringBuilder();
        css.append("body { font-family: '").append(fontFamily).append("', sans-serif; ")
                .append("font-size: ").append(fontSize).append("pt; ")
                .append("color: ").append(fgHex).append("; ")
                // HTMLEditorKit list views often omit line wrapping unless the box can break long tokens.
                .append("margin: 0; padding: 0; word-wrap: break-word; }");
        css.append("p { margin-top: 4px; margin-bottom: 4px; word-wrap: break-word; }");
        css.append("h1 { font-size: ").append(h1).append("pt; margin-top: 10px; margin-bottom: 4px; }");
        css.append("h2 { font-size: ").append(h2).append("pt; margin-top: 8px; margin-bottom: 4px; }");
        css.append("h3 { font-size: ").append(h3).append("pt; margin-top: 6px; margin-bottom: 4px; }");
        css.append("h4, h5, h6 { margin-top: 6px; margin-bottom: 4px; }");
        css.append("pre { font-family: monospace; background-color: ").append(codeBgHex)
                .append("; padding: 8px; margin-top: 6px; margin-bottom: 6px; }");
        css.append("code { font-family: monospace; word-wrap: break-word; }");
        css.append("blockquote { margin-left: 4px; padding-left: 10px; color: ")
                .append(mutedHex).append("; word-wrap: break-word; }");
        // HTMLEditorKit's built-in default.css (not Web CSS) indents ul/ol with margin-left-ltr: 50 and ul
        // with -bullet-gap: 10. Padding/margin in normal CSS does not override those properties, so changing
        // padding-left here had no visible effect. Halve the JDK defaults (50→25, 10→5, nested 25→12).
        int listLeftRtlHalf = 25;
        int listNestedLtrHalf = 12;
        int listBulletGapHalf = 5;
        css.append("ul { margin-top: 4px; margin-bottom: 4px; margin-left-ltr: ")
                .append(listLeftRtlHalf)
                .append("; margin-right-rtl: ")
                .append(listLeftRtlHalf)
                .append("; -bullet-gap: ")
                .append(listBulletGapHalf)
                .append("; list-style-type: disc; word-wrap: break-word; }");
        css.append("ol { margin-top: 4px; margin-bottom: 4px; margin-left-ltr: ")
                .append(listLeftRtlHalf)
                .append("; margin-right-rtl: ")
                .append(listLeftRtlHalf)
                .append("; list-style-type: decimal; word-wrap: break-word; }");
        css.append("ul li ul, ul li menu { list-style-type: circle; margin-left-ltr: ")
                .append(listNestedLtrHalf)
                .append("; margin-right-rtl: ")
                .append(listNestedLtrHalf)
                .append("; }");
        css.append("ol li ol { margin-left-ltr: ")
                .append(listNestedLtrHalf)
                .append("; margin-right-rtl: ")
                .append(listNestedLtrHalf)
                .append("; }");
        css.append("ul li ul li ul, ul li ul li menu, ol li ol li ol { list-style-type: square; margin-left-ltr: ")
                .append(listNestedLtrHalf)
                .append("; margin-right-rtl: ")
                .append(listNestedLtrHalf)
                .append("; }");
        // Loose lists use <p> inside <li>; tight lists use text directly in <li> — both need explicit wrapping.
        css.append("li { word-wrap: break-word; }");
        css.append("a { color: ").append(linkHex).append("; }");
        css.append("table { border-collapse: collapse; margin-top: 6px; margin-bottom: 6px; }");
        css.append("th, td { border: 1px solid ").append(mutedHex)
                .append("; padding: 4px 8px; }");
        css.append("th { background-color: ").append(codeBgHex).append("; font-weight: bold; }");
        css.append("hr { border: none; border-top: 1px solid ").append(mutedHex)
                .append("; margin-top: 8px; margin-bottom: 8px; }");

        return "<html><head><style>" + css + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
