package treepeater.tree;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
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

import treepeater.Utilities;
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

    private int hoverRow = -1;
    private MouseMotionAdapter motion;
    private MouseAdapter mouse;

    /**
     * We have to keep track of the viewport context to get the width of the tree.
     */
    private JViewport viewportContext;

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

    private static void repaintRowStrip(JTree tree, int row) {
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