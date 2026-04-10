package treepeater.requestResponse;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;


public class StatusComboBoxRenderer extends JLabel implements ListCellRenderer<Status> {
    public StatusComboBoxRenderer() {
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
        setIconTextGap(0);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
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
    public Component getListCellRendererComponent(JList<? extends Status> list, Status status, int arg2, boolean arg3, boolean arg4) {
        setIcon(status.getIcon().withColor(status.getBorderColor()));

        return this;
    }
}
