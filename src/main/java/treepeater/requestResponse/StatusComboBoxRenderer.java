package treepeater.requestResponse;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class StatusComboBoxRenderer extends JLabel implements ListCellRenderer<Status> {
    public StatusComboBoxRenderer() {
        setOpaque(true);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Status> list, Status status, int arg2, boolean arg3, boolean arg4) {
        setIcon(status.getIcon());

        return this;
    }
}
