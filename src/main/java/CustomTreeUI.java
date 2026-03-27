import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.plaf.basic.BasicTreeUI.NodeDimensionsHandler;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreePath;

public class CustomTreeUI extends BasicTreeUI {

        @Override
        protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
            return new NodeDimensionsHandler() {
                @Override
                public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
                    Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
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