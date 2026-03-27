import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class StatusComboBoxRenderer extends JLabel implements ListCellRenderer {
    public StatusComboBoxRenderer() {
        setOpaque(true);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int arg2, boolean arg3, boolean arg4) {
        Status status = (Status) value;

        setIcon(status.getIcon());

        return this;
    }
}
