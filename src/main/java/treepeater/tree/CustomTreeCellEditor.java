package treepeater.tree;
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
import treepeater.Treepeater;

public class CustomTreeCellEditor extends DefaultTreeCellEditor {
    /**
     * Editing types triggered by the extension itself.
     * 
     * When setting the editing type, the editor will automatically show the appropriate component and focus the appropriate field.
     * This allows the extension to trigger the editing without the user clicking on the cell.
     */
    public enum ProgrammaticEdit {
        NONE, RENAME, STATUS
    }

    private final CustomTreeCell cell = new CustomTreeCell();
    private ProgrammaticEdit programmaticEdit = ProgrammaticEdit.NONE;

    public CustomTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
        super(tree, renderer);
    }

    /**
     * Set the programmatic edit type.
     * 
     * This will cause the editor to show the appropriate component and focus the appropriate field.
     * This allows the extension to trigger the editing without the user clicking on the cell.
     * 
     * @param editType The type of edit to perform.
     */
    public void setProgrammaticEdit(ProgrammaticEdit editType) {
        Treepeater.api.logging().logToOutput("Setting programmatic edit: " + editType);
        this.programmaticEdit = editType;
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        RequestTreeNode node = (RequestTreeNode) value;
        this.cell.setNode(node);

        // Handle the programmatic edit.
        ProgrammaticEdit intent = this.programmaticEdit;
        Treepeater.api.logging().logToOutput("Programmatic edit: " + intent);
        this.programmaticEdit = ProgrammaticEdit.NONE;
        if (intent == ProgrammaticEdit.RENAME) {
            this.cell.showField();
            SwingUtilities.invokeLater(this.cell::focusNameFieldForEditing);
        } else if (intent == ProgrammaticEdit.STATUS) {
            this.cell.showLabel();
            SwingUtilities.invokeLater(this.cell::openStatusPopup);
        }
        return this.cell;
    }
    
    @Override
    public boolean isCellEditable(EventObject event) {
        Logging log = Treepeater.api.logging();
        log.logToOutput("isCellEditable? " + event);

        if (event == null && this.programmaticEdit != ProgrammaticEdit.NONE) {
            return true;
        }

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
        if (relativeX >= bounds.width - this.cell.getCloseReservedWidth()) {
            log.logToOutput("Close strip click - not editable");
            return false;
        }
        int buttonWidth = this.cell.getComboBoxWidth();

        boolean isInButton = relativeX <= buttonWidth;

        if (!isInButton && mouseEvent.getClickCount() == 2) {
            this.cell.showField();
        } else {
            this.cell.showLabel();
        }

        if (isInButton || mouseEvent.getClickCount() == 2) {
            log.logToOutput("Editable");
            return true;
        }

        if (isInButton) {
            SwingUtilities.invokeLater(this.cell::openStatusPopup);
        }

        log.logToOutput("Not editable");
        return false;
    }
}

