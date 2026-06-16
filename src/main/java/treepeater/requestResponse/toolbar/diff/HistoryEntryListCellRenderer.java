package treepeater.requestResponse.toolbar.diff;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import treepeater.requestResponse.HistoryEntry;

/**
 * Renders history rows in the popup ({@code index >= 0}) and a compact {@code #index} label when
 * collapsed ({@code index == -1}), matching {@link javax.swing.JComboBox} renderer conventions.
 */
public final class HistoryEntryListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(
            JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        String text = "";
        if (value instanceof HistoryEntry entry) {
            text = index < 0 ? "#" + entry.getIndex() : entry.toString();
        }
        return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
    }
}
