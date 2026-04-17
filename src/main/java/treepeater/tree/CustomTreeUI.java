package treepeater.tree;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.Enumeration;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreePath;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.Utilities;
import treepeater.components.SvgIcon;
import treepeater.icons.FolderIcon;
import treepeater.icons.FolderOpenIcon;
import treepeater.requestResponse.Status;


public class CustomTreeUI extends BasicTreeUI {

    /**
     * The radius of the rounded corners.
     */
    private static final int ARC = 10;
    /**
     * The padding on the left and right sides of the row.
     */
    private static final int PAD_LEFT = 16;
    /**
     * The padding on the left and right sides of the row.
     */
        private static final int PAD_RIGHT = 4;
    /**
     * The padding on the top and bottom sides of the row.
     */
    private static final int PAD_Y = 3;
    /**
     * The amount to shift the expand/collapse and line geometry right.
     */
    private static final int EXPAND_CONTROL_NUDGE_PX = 8;
    /**
     * Horizontal inset per tree side; together with {@link #COMPACT_RIGHT_CHILD_INDENT} sets
     * {@code totalChildIndent} for each nesting level (see {@link BasicTreeUI#getRowX}). LAF defaults
     * are often noticeably larger.
     */
    private static final int COMPACT_LEFT_CHILD_INDENT = 6;
    private static final int COMPACT_RIGHT_CHILD_INDENT = 6;
    /**
     * Extra horizontal inset for {@code depth == 1} (first model level under the root: top-level
     * folders/requests when the root row is hidden). Deeper levels keep the compact per-level step only.
     */
    private static final int FIRST_LAYER_EXTRA_INDENT_PX = 7;
    /** Max characters of {@link HttpRequest#method()} drawn in the expand-control slot for {@link RequestTreeNode}. */
    private static final int REQUEST_METHOD_LABEL_MAX_CHARS = 4;

    private int hoverRow = -1;
    private MouseMotionAdapter motion;
    private MouseAdapter mouse;

    /**
     * We have to keep track of the viewport context to get the width of the tree.
     */
    private JViewport viewportContext;

    private final SvgIcon folderClosedExpandIcon = new FolderIcon().withSize(16, 16);
    private final SvgIcon folderOpenExpandIcon = new FolderOpenIcon().withSize(16, 16);

    public CustomTreeUI() {
        super();
        this.viewportContext = null;
    }

    public void setViewportContext(JViewport viewport) {
        this.viewportContext = viewport;
    }

    /**
     * Override this function to shift the expand/collapse and line geometry right.
     */
    @Override
    public int getRightChildIndent() {
        return Math.max(0, super.getRightChildIndent() - EXPAND_CONTROL_NUDGE_PX);
    }

    @Override
    protected int getRowX(int row, int depth) {
        int x = super.getRowX(row, depth);
        if (depth == 1) {
            x += FIRST_LAYER_EXTRA_INDENT_PX;
        }
        return x;
    }

    /**
     * Clears the layout cache and notifies the tree so row bounds are recomputed with the
     * current width. {@code revalidate()} alone does not invalidate {@code treeState}.
     */
    public void invalidateNodeLayoutCache() {
        if (treeState != null) {
            treeState.invalidateSizes();
        }
        updateSize();
    }

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
        return new NodeDimensionsHandler() {
            @Override
            public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
                Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
                if (dimensions == null) {
                    return null;
                }
                int treeW = viewportContext != null ? viewportContext.getWidth() : tree.getWidth();
                if (treeW > 0) {
                    int remainder = treeW - dimensions.x - 4;
                    dimensions.width = remainder;
                }
                return dimensions;
            }
        };
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        setLeftChildIndent(COMPACT_LEFT_CHILD_INDENT);
        setRightChildIndent(COMPACT_RIGHT_CHILD_INDENT);

        JTree t = (JTree) c;

        motion = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = Utilities.rowAtPoint(t, e.getPoint());
                if (row != hoverRow) {
                    int prev = hoverRow;
                    hoverRow = row;
                    repaintRowStrip(t, prev);
                    repaintRowStrip(t, row);
                }
            }
        };
        mouse = new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverRow >= 0) {
                    int prev = hoverRow;
                    hoverRow = -1;
                    repaintRowStrip(t, prev);
                }
            }
        };
        t.addMouseMotionListener(motion);
        t.addMouseListener(mouse);
    }

    /**
     * Repaints the full tree width for {@code row} so {@link #paintFullWidthRowStrip} runs after
     * model data used only for row chrome (e.g. status colors) changes without touching child components.
     */
    static void repaintRowStrip(JTree tree, int row) {
        if (row < 0) {
            return;
        }
        Rectangle r = tree.getRowBounds(row);
        if (r != null) {
            tree.repaint(0, r.y, tree.getWidth(), r.height);
        }
    }

    @Override
    public void uninstallUI(JComponent c) {
        JTree t = (JTree) c;
        if (motion != null) {
            t.removeMouseMotionListener(motion);
        }
        if (mouse != null) {
            t.removeMouseListener(mouse);
        }
        motion = null;
        mouse = null;
        super.uninstallUI(c);
    }

    /**
     * Empty folders and request rows are leaves, so the default UI would not paint an expand control.
     * We still use that slot for a folder glyph or HTTP method label (see {@link #paintExpandControl}).
     */
    @Override
    protected boolean shouldPaintExpandControl(TreePath path, int row, boolean isExpanded,
            boolean hasBeenExpanded, boolean isLeaf) {
        if (path.getLastPathComponent() instanceof FolderTreeNode && isLeaf) {
            return leafExpandControlSlotVisible(path);
        }
        if (path.getLastPathComponent() instanceof RequestTreeNode && isLeaf) {
            return leafExpandControlSlotVisible(path);
        }
        return super.shouldPaintExpandControl(path, row, isExpanded, hasBeenExpanded, isLeaf);
    }

    /**
     * Same depth/root-handle rules as {@link BasicTreeUI#shouldPaintExpandControl} for custom paint in the knob area.
     */
    private boolean leafExpandControlSlotVisible(TreePath path) {
        int depth = path.getPathCount() - 1;
        return !((depth == 0 || (depth == 1 && !tree.isRootVisible())) && !getShowsRootHandles());
    }

    private int expandKnobMiddleX(Rectangle bounds) {
        if (tree.getComponentOrientation().isLeftToRight()) {
            return bounds.x - getRightChildIndent() + 1;
        }
        return bounds.x + bounds.width + getRightChildIndent() - 1;
    }

    private static int expandKnobMiddleY(Rectangle bounds) {
        return bounds.y + bounds.height / 2;
    }

    private void paintHttpMethodAbbrev(Graphics g, Rectangle bounds, RequestTreeNode node) {
        HttpRequest req = node.getRequest();
        if (req == null) {
            return;
        }
        String method = req.method();
        if (method == null || method.isEmpty()) {
            return;
        }
        if (method.length() > REQUEST_METHOD_LABEL_MAX_CHARS) {
            method = method.substring(0, REQUEST_METHOD_LABEL_MAX_CHARS);
        }
        int middleXOfKnob = this.expandKnobMiddleX(bounds);
        int middleYOfKnob = expandKnobMiddleY(bounds);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font base = tree.getFont();
            float size = Math.max(8f, base.getSize2D() - 3f);
            g2.setFont(base.deriveFont(Font.PLAIN, size));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(method);
            int x = middleXOfKnob - w / 2;
            int baselineY = middleYOfKnob - fm.getHeight() / 2 + fm.getAscent();
            g2.setColor(UIManager.getColor("Label.foreground"));
            g2.drawString(method, x, baselineY);
        } finally {
            g2.dispose();
        }
    }

    /**
     * {@link FolderTreeNode}: folder / folder-open SVGs (closed only for empty folders).
     * {@link RequestTreeNode}: abbreviated {@link HttpRequest#method()} in the same slot.
     */
    @Override
    protected void paintExpandControl(Graphics g, Rectangle clipBounds, Insets insets, Rectangle bounds,
            TreePath path, int row, boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
        Object value = path.getLastPathComponent();
        if (value instanceof FolderTreeNode) {
            boolean nonLeafGlyph = !isLeaf && (!hasBeenExpanded || treeModel.getChildCount(value) > 0);
            boolean leafFolderGlyph = isLeaf;
            if (nonLeafGlyph || leafFolderGlyph) {
                int middleXOfKnob = this.expandKnobMiddleX(bounds);
                int middleYOfKnob = expandKnobMiddleY(bounds);

                Color iconColor = UIManager.getColor("Label.foreground");

                SvgIcon icon = leafFolderGlyph
                        ? this.folderClosedExpandIcon
                        : (isExpanded ? this.folderOpenExpandIcon : this.folderClosedExpandIcon);
                icon.withColor(iconColor);
                drawCentered(tree, g, icon, middleXOfKnob, middleYOfKnob);
            }
        } else if (value instanceof RequestTreeNode) {
            this.paintHttpMethodAbbrev(g, bounds, (RequestTreeNode) value);
        } else {
            super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
        }
    }

    /**
     * Override the default painting order. Otherwise the background is painted over the expand/collapse and line geometry.
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        if (tree == c && treeState != null) {
            Graphics gBg = g.create();
            try {
                paintVisibleRowBackgrounds(gBg);
            } finally {
                gBg.dispose();
            }
        }
        super.paint(g, c);
    }

    /**
     * Paints the background of all visible rows.
     * @param g the graphics context
     */
    private void paintVisibleRowBackgrounds(Graphics g) {
        Rectangle paintBounds = g.getClipBounds();
        if (paintBounds == null) {
            paintBounds = tree.getVisibleRect();
        }
        TreePath initialPath = getClosestPathForLocation(tree, 0, paintBounds.y);
        if (initialPath == null) {
            return;
        }
        Enumeration<?> paintingEnumerator = treeState.getVisiblePathsFrom(initialPath);
        if (paintingEnumerator == null) {
            return;
        }
        int row = treeState.getRowForPath(initialPath);
        int endY = paintBounds.y + paintBounds.height;
        boolean done = false;
        while (!done && paintingEnumerator.hasMoreElements()) {
            TreePath path = (TreePath) paintingEnumerator.nextElement();
            if (path == null) {
                done = true;
            } else {
                Rectangle bounds = tree.getPathBounds(path);
                if (bounds == null) {
                    return;
                }
                paintFullWidthRowStrip(g, bounds, path, row);
                if (bounds.y + bounds.height >= endY) {
                    done = true;
                }
            }
            row++;
        }
    }

    /**
     * Paints a full-width row strip.
     * 
     * The background for each row is a rounded colored rectangle.
     * The fill color is determined by the status of the node.
     * @param g the graphics context
     * @param bounds the bounds of the row
     * @param path the path of the row
     * @param row the row index
     */
    private void paintFullWidthRowStrip(Graphics g, Rectangle bounds, TreePath path, int row) {
        if (tree == null || path == null || bounds == null) {
            return;
        }
        int fullW = tree.getWidth();
        if (fullW <= PAD_LEFT * 2 + ARC) {
            return;
        }

        Object component = path.getLastPathComponent();
        Color fill;
        Color border;
        if (component instanceof TreepeaterNode) {
            Status status = ((TreepeaterNode) component).getStatus();

            fill = status.getBackgroundColor();
            border = status.getBorderColor();
        } else {
            fill = UIManager.getColor("Button.default.background");
            border = UIManager.getColor("Button.default.border");
        }

        Color hoverFill = Utilities.interpolateColor(fill, border, 0.2f);

        fill = (row == hoverRow) ? hoverFill : fill;

        int x = bounds.x - PAD_LEFT;
        int y = bounds.y + PAD_Y;
        int w = fullW - x - PAD_RIGHT;
        int h = bounds.height - 2 * PAD_Y;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D rr = new RoundRectangle2D.Float(x, y, w, h, ARC, ARC);
            g2.setColor(fill);
            g2.fill(rr);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(rr);
        } finally {
            g2.dispose();
        }
    }
}