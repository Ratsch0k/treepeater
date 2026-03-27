import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

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