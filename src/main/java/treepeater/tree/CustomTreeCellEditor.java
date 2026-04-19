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
    private boolean pendingOpenStatusPopup;
    /**
     * When true, {@link JTree#startEditingAtPath(TreePath)} may start editing (it calls
     * {@code isCellEditable(null)}). Drag-enabled trees often skip editing on the first press when
     * selection changes; the tree uses this one-shot to open the editor from {@code mouseClicked}.
     */
    private boolean permitEditStartWithNullMouseEvent;

    public CustomTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
        super(tree, renderer);
    }

    /** Bind the shared editor cell to {@code node} so combo / close hit widths match this row. */
    public void prepareHitTest(TreepeaterNode node) {
        this.cell.setNode(node);
    }

    public CustomTreeCell getEditingPanel() {
        return this.cell;
    }

    /** Next {@link #getTreeCellEditorComponent} after a user combo click will open the status popup. */
    public void armOpenStatusPopupOnNextShow() {
        this.pendingOpenStatusPopup = true;
    }

    /**
     * Allow the next {@code isCellEditable(null)} (from {@link JTree#startEditingAtPath(TreePath)})
     * to return true. Caller must reset to false after {@code startEditingAtPath}.
     */
    public void setPermitEditStartWithNullMouseEvent(boolean permit) {
        this.permitEditStartWithNullMouseEvent = permit;
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
        this.programmaticEdit = editType;
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        this.lastRow = row;
        TreepeaterNode node = (TreepeaterNode) value;
        this.cell.setNode(node);

        // Handle the programmatic edit.
        ProgrammaticEdit intent = this.programmaticEdit;
        this.programmaticEdit = ProgrammaticEdit.NONE;
        if (intent == ProgrammaticEdit.RENAME) {
            this.cell.showField();
            SwingUtilities.invokeLater(this.cell::focusNameFieldForEditing);
        } else if (intent == ProgrammaticEdit.STATUS) {
            this.cell.showLabel();
            SwingUtilities.invokeLater(this.cell::openStatusPopup);
        } else if (this.pendingOpenStatusPopup) {
            this.pendingOpenStatusPopup = false;
            SwingUtilities.invokeLater(this.cell::openStatusPopup);
        }
        return this.cell;
    }
    
    @Override
    public boolean isCellEditable(EventObject event) {
        if (event == null) {
            if (this.programmaticEdit != ProgrammaticEdit.NONE) {
                return true;
            }
            return this.permitEditStartWithNullMouseEvent;
        }

        if (!(event instanceof MouseEvent)) {
            return false;
        }

        MouseEvent mouseEvent = (MouseEvent) event;

        if (mouseEvent.getButton() != MouseEvent.BUTTON1) {
            return false;
        }

        TreePath path = this.tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());

        if (path == null) {
            return false;
        }

        Rectangle bounds = tree.getPathBounds(path);

        if (bounds == null) {
            return false;
        }

        this.pendingOpenStatusPopup = false;
        this.cell.setNode((TreepeaterNode) path.getLastPathComponent());

        int relativeX = mouseEvent.getX() - bounds.x;
        if (relativeX >= bounds.width - this.cell.getCloseReservedWidth()) {
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
            if (isInButton) {
                this.pendingOpenStatusPopup = true;
            }
            return true;
        }

        return false;
    }
}
