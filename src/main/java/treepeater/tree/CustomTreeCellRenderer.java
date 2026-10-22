package treepeater.tree;
import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Reuses one {@link CustomTreeCell} for all rows. JTree calls
 * {@link #getTreeCellRendererComponent} on every paint; allocating a new panel each time
 * caused flicker on resize and other repaints.
 */
public class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
    
    private CustomTreeCell cell = null;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof TreepeaterNode)) {
            return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
        if (this.cell == null) {
            this.cell = new CustomTreeCell();
        }
        this.cell.setNode((TreepeaterNode) value, matchFor(tree, (TreepeaterNode) value));
        return this.cell;
    }

    /** Search-filter display state from the hosting tree, when it keeps one. */
    static FilteredTreeModel.NodeMatch matchFor(JTree tree, TreepeaterNode node) {
        return tree instanceof RequestTree requestTree ? requestTree.getNodeMatch(node) : null;
    }
}