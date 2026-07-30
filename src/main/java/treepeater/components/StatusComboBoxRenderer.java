package treepeater.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import treepeater.requestResponse.Status;

/** Renders {@link Status} entries in {@link StatusComboBox} dropdowns and value areas. */
public class StatusComboBoxRenderer extends JLabel implements ListCellRenderer<Status> {

    private final boolean showName;

    /** Icon-only renderer for compact tree rows. */
    public StatusComboBoxRenderer() {
        this(false);
    }

    /** @param showName when {@code true}, shows the status icon and display name */
    public StatusComboBoxRenderer(boolean showName) {
        this.showName = showName;
        setVerticalAlignment(CENTER);
        if (showName) {
            setHorizontalAlignment(LEFT);
            setIconTextGap(6);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        } else {
            setHorizontalAlignment(CENTER);
            setIconTextGap(0);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        }
        applyUiDefaults();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyUiDefaults();
    }

    private void applyUiDefaults() {
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        putClientProperty("ComboBox.padding", new Insets(0, 0, 0, 0));
        putClientProperty("ComboBox.popupInsets", new Insets(0, 0, 0, 0));
        putClientProperty("ComboBox.selectionInsets", new Insets(0, 0, 0, 0));
        Color popupBg = UIManager.getColor("PopupMenu.background");
        if (popupBg != null) {
            putClientProperty("ComboBox.popupBackground", popupBg);
        }
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends Status> list, Status status, int index, boolean isSelected, boolean cellHasFocus) {
        if (status == null) {
            setIcon(null);
            setText("");
            return this;
        }

        setIcon(status.getIcon().withColor(status.getBorderColor()));
        setText(this.showName ? status.getStatus() : "");

        return this;
    }
}
