package treepeater.requestResponse.toolbar.ai;

import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import treepeater.icons.CloseIcon;

/**
 * Tab strip header for an agent chat: title on the left, close control on the right (same pattern as
 * {@link treepeater.requestResponse.RequestResponseTab} for main request tabs).
 */
final class AIAgentChatTabTitle extends JPanel {

    private final JButton closeButton;

    AIAgentChatTabTitle(String title, Runnable onClose) {
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setOpaque(false);

        JLabel label = new JLabel(title);
        this.add(label);
        this.add(Box.createHorizontalGlue());

        this.closeButton = new JButton();
        this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        this.closeButton.addActionListener(e -> onClose.run());
        this.closeButton.setBorderPainted(false);
        this.closeButton.setContentAreaFilled(false);
        this.closeButton.setFocusPainted(false);
        this.closeButton.setOpaque(false);
        this.closeButton.setMargin(new Insets(0, 4, 0, 0));
        this.closeButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.add(this.closeButton);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.closeButton != null) {
            this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        }
    }
}
