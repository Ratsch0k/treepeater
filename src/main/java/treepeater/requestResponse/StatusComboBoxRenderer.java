package treepeater.requestResponse;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;


public class StatusComboBoxRenderer extends JLabel implements ListCellRenderer<Status> {
    public StatusComboBoxRenderer() {
        setOpaque(false);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
        setIconTextGap(0);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setBackground(new Color(0, 0, 0, 0));
        putClientProperty("ComboBox.padding", new Insets(0, 0, 0, 0));
        putClientProperty("ComboBox.popupInsets", new Insets(0, 0, 0, 0));
        putClientProperty("ComboBox.selectionInsets ", new Insets(0, 0, 0, 0));
        putClientProperty("ComboBox.popupBackground", Color.BLACK);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Status> list, Status status, int arg2, boolean arg3, boolean arg4) {
        setIcon(status.getIcon());

        return this;
    }
}
