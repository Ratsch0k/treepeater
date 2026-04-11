package treepeater.requestResponse;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Placeholder content for the resizable area opened from the request/response side toolbar.
 */
final class RequestResponseExpandPanel extends JPanel {

    public RequestResponseExpandPanel() {
        super(new BorderLayout());
        this.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1, RequestResponsePanelUi.uiBorderColor()),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel label = new JLabel("Example content for the expanded panel. Drag the divider to resize.");
        this.add(label, BorderLayout.NORTH);
    }
}
