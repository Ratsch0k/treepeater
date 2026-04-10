package treepeater.components;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A configurable SVG icon. Supports both classpath resources and inline SVG content.
 */
public class SvgIcon implements Icon {

    private static final int DEFAULT_SIZE = 16;

    private final String resourcePath;
    private final String svgContent;
    private int width  = DEFAULT_SIZE;
    private int height = DEFAULT_SIZE;
    private Color color = null;

    // Cached URL for content-based icons (points to a temp file).
    private URL contentUrl = null;

    // base holds a sized icon without any color filter applied
    private FlatSVGIcon base;
    // resolved holds base + the active color filter (or base itself when color is null)
    private FlatSVGIcon resolved;

    /**
     * @param resourcePath Classpath-absolute path to the SVG resource, e.g. {@code "/icons/check.svg"}.
     *                     A leading slash is accepted and handled automatically.
     */
    public SvgIcon(String resourcePath) {
        // ClassLoader.getResourceAsStream does not accept a leading slash
        this.resourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        this.svgContent = null;
    }

    private SvgIcon(String svgContent, boolean isContent) {
        this.resourcePath = null;
        this.svgContent = svgContent;
    }

    /**
     * Creates an {@code SvgIcon} from raw SVG XML content.
     * The source file may be deleted after calling this method; the content is stored in memory
     * and written to a temporary file on first render.
     *
     * @param svgContent the raw SVG XML string
     * @return a new {@code SvgIcon} backed by the given content
     */
    public static SvgIcon fromContent(String svgContent) {
        return new SvgIcon(svgContent, true);
    }

    /**
     * Sets the icon's rendered size in pixels.
     *
     * @return this, for chaining
     */
    public SvgIcon withSize(int width, int height) {
        this.width    = width;
        this.height   = height;
        this.base     = null;
        this.resolved = null;
        return this;
    }

    /**
     * Sets the tint color applied to all SVG fills at paint time.
     * The alpha channel of each original SVG pixel is preserved so that anti-aliased
     * edges and semi-transparent areas continue to render correctly.
     * Pass {@code null} to restore the SVG's original colors.
     *
     * @return this, for chaining
     */
    public SvgIcon withColor(Color color) {
        this.color    = color;
        this.resolved = null;
        return this;
    }


    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        getResolved().paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    private FlatSVGIcon getBase() {
        if (base == null) {
            if (svgContent != null) {
                base = new FlatSVGIcon(getContentUrl()).derive(width, height);
            } else {
                base = new FlatSVGIcon(resourcePath, width, height, SvgIcon.class.getClassLoader());
            }
        }
        return base;
    }

    private URL getContentUrl() {
        if (contentUrl == null) {
            try {
                Path tmp = Files.createTempFile("treepeater-svg-", ".svg");
                tmp.toFile().deleteOnExit();
                Files.writeString(tmp, svgContent, StandardCharsets.UTF_8);
                contentUrl = tmp.toUri().toURL();
            } catch (IOException e) {
                contentUrl = SvgIcon.class.getClassLoader().getResource("icons/hourglass.svg");
            }
        }
        return contentUrl;
    }

    private FlatSVGIcon getResolved() {
        if (resolved == null) {
            if (color != null) {
                Color target = color;
                resolved = getBase()
                    .derive(width, height)
                    .setColorFilter(new FlatSVGIcon.ColorFilter(orig ->
                        new Color(target.getRed(), target.getGreen(), target.getBlue(), orig.getAlpha())
                    ));
            } else {
                resolved = getBase();
            }
        }
        return resolved;
    }
}
