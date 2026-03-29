import java.awt.Component;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof RequestTreeNode)) {
            return new CustomTreeCell();
        }

        RequestTreeNode node = (RequestTreeNode) value;

        Treepeater.api.logging().logToOutput("Generated cell renderer component: " + node.getStatus().getStatus() + " " + node.getName());


        CustomTreeCell cell = new CustomTreeCell();
        cell.setNode(node);

        return cell;
    }
}