package treepeater.tree;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreePath;

public class CustomTreeUI extends BasicTreeUI {

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
                    int treeW = tree.getWidth();
                    if (treeW > 0) {
                        int remainder = treeW - dimensions.x;
                        if (remainder > 0) {
                            dimensions.width = Math.max(dimensions.width, remainder);
                        }
                    }
                    return dimensions;
                }
            };
        }

        @Override
        protected void paintHorizontalLine(Graphics g, JComponent c,
                                           int y, int left, int right) {
            // do nothing.
        }

        @Override
        protected void paintVerticalPartOfLeg(Graphics g, Rectangle clipBounds,
                                              Insets insets, TreePath path) {
            // do nothing.
        }
}