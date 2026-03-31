import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import burp.api.montoya.logging.Logging;

public class CustomTreeCellEditor extends DefaultTreeCellEditor {
    private CustomTreeCell cell = new CustomTreeCell();

    public CustomTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
        super(tree, renderer);
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        RequestTreeNode node = (RequestTreeNode) value;
        this.cell.setNode(node);
        return this.cell;
    }
    
    @Override
    public boolean isCellEditable(EventObject event) {
        Logging log = Treepeater.api.logging();
        log.logToOutput("isCellEditable? " + event);

        //if (event == null) {
        //    log.logToOutput("Make editable");
        //    this.cell.showLabel();
        //    this.cell.selectNode();
        //    return true;
        //}


        if (!(event instanceof MouseEvent)) {
            log.logToOutput("Event not a mouse event");
            return false;
        }

        MouseEvent mouseEvent = (MouseEvent) event;

        if (mouseEvent.getButton() != MouseEvent.BUTTON1) {
            log.logToOutput("Not left mouse button clicked");
            return false;
        }

        TreePath path = this.tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());

        if (path == null) {
            log.logToOutput("Path not found");
            return false;
        }

        Rectangle bounds = tree.getPathBounds(path);

        if (bounds == null) {
            log.logToOutput("Could not identify node bounds");
            return false;
        }

        int relativeX = mouseEvent.getX() - bounds.x;
        int buttonWidth = this.cell.getButtonWidth();

        boolean isInButton = relativeX <= buttonWidth;

        if (!isInButton && mouseEvent.getClickCount() == 2) {
            this.cell.showField();
        } else {
            this.cell.showLabel();
        }

        //if (!isInButton && mouseEvent.getClickCount() == 1) {
        //    this.cell.selectNode();
        //}

        if (isInButton || mouseEvent.getClickCount() == 2) {
            log.logToOutput("Editable");
            return true;
        }

        if (isInButton) {
            SwingUtilities.invokeLater(this.cell::clickButton);
        }

        log.logToOutput("Not editable");
        return false;
    }
}

