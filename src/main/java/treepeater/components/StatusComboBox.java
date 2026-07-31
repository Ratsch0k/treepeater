package treepeater.components;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import treepeater.requestResponse.Status;

/**
 * Custom combo box for displaying and selecting Treepeater statuses.
 *
 * <p>Re-applies {@link StatusComboBoxUi} after {@code super.updateUI()} whenever Burp's theme
 * changes; otherwise the LAF replaces our UI and the value area paints incorrectly (often fully
 * transparent).
 */
public class StatusComboBox extends JComboBox<Status> {
    @Override
    public void updateUI() {
        super.updateUI();
        StatusComboBoxUi.install(this);
        ListCellRenderer<? super Status> r = getRenderer();
        if (r instanceof JComponent) {
            SwingUtilities.updateComponentTreeUI((JComponent) r);
        }
    }
}
