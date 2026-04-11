package treepeater.requestResponse;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;

import treepeater.Treepeater;
import treepeater.components.CustomButton;
import treepeater.components.SplitButtonPanel;

/**
 * Static Swing helpers for {@link RequestResponsePanel}: themed top bar, flat toolbar buttons,
 * and labeled editor columns.
 */
final class RequestResponsePanelUi {

    private RequestResponsePanelUi() {
    }

    static JPanel buildHistorySplitButton(JButton navButton, JButton dropButton) {
        styleAsFlatButton(navButton);
        styleAsFlatButton(dropButton);

        navButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        dropButton.setBorder(BorderFactory.createEmptyBorder(7, 0, 6, 0));
        dropButton.setFont(dropButton.getFont().deriveFont(dropButton.getFont().getSize2D() - 4f));

        installHoverBackground(navButton);
        installHoverBackground(dropButton);

        SplitButtonPanel panel = new SplitButtonPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(navButton);
        panel.add(dropButton);

        return panel;
    }

    static void styleAsFlatButton(JButton button) {
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        Color bg =
                UIManager.getColor("Panel.background") != null ? UIManager.getColor("Panel.background") :
                UIManager.getColor("control");
        if (bg != null) {
            button.setBackground(bg);
        }
    }

    static Color uiBorderColor() {
        Color c =
                UIManager.getColor("Separator.foreground") != null ? UIManager.getColor("Separator.foreground") :
                UIManager.getColor("Component.borderColor") != null ? UIManager.getColor("Component.borderColor") :
                UIManager.getColor("controlShadow");
        if (c != null) {
            return c;
        }
        return new Color(0, 0, 0, 80);
    }

    static Color uiHoverColor() {
        Color c =
                UIManager.getColor("Button.hoverBackground") != null ? UIManager.getColor("Button.hoverBackground") :
                UIManager.getColor("Button.highlight") != null ? UIManager.getColor("Button.highlight") :
                UIManager.getColor("Table.selectionBackground");
        if (c != null) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 70);
        }
        return new Color(0, 0, 0, 18);
    }

    static void installHoverBackground(JButton button) {
        button.getModel().addChangeListener(e -> applyFlatButtonHoverVisual(button));
    }

    static void applyFlatButtonHoverVisual(JButton button) {
        Color normalBg = flatPanelBackground();
        Color hoverBg = uiHoverColor();
        Color next;
        if (!button.isEnabled()) {
            next = normalBg;
        } else {
            next = button.getModel().isRollover() ? hoverBg : normalBg;
        }
        if (!Objects.equals(button.getBackground(), next)) {
            button.setBackground(next);
        }
        button.repaint();
        Component parent = button.getParent();
        if (parent != null) {
            parent.repaint();
        }
    }

    static Color flatPanelBackground() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg != null) {
            return bg;
        }
        Color c = UIManager.getColor("control");
        return c != null ? c : Color.LIGHT_GRAY;
    }

    static void restyleFlatToolbarButton(JButton button) {
        styleAsFlatButton(button);
        applyFlatButtonHoverVisual(button);
    }

    static JPanel makeHeaderPanel(String header, Component component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        Border labelPadding = BorderFactory.createEmptyBorder(12, 8, 12, 0);

        JLabel label = new JLabel(header);
        label.setBorder(labelPadding);

        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));

        panel.add(label);
        panel.add(component);

        panel.setBackground(UIManager.getColor("Colors.ui.background.1"));

        return panel;
    }

    /**
     * Re-reads {@link UIManager} colors for controls that are configured once in the constructor
     * but must track Burp theme changes.
     */
    static void applyTopBarTheme(
            JPanel topBarWrapper,
            CustomButton sendButton,
            JPanel historyBackSplitButton,
            JPanel historyForwardSplitButton,
            JButton historyBackButton,
            JButton historyBackDropButton,
            JButton historyForwardButton,
            JButton historyForwardDropButton) {
        if (topBarWrapper != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(topBarWrapper);
        }
        if (topBarWrapper != null) {
            topBarWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, uiBorderColor()));
        }
        if (sendButton != null) {
            Color bg = UIManager.getColor("Button.primary.background");
            Color fg = UIManager.getColor("Button.primary.foreground");
            Color hov = UIManager.getColor("Button.primary.hoverBackground");

            sendButton.setBackground(bg);
            sendButton.setForeground(fg);
            sendButton.setHoverBackground(hov);
            sendButton.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            sendButton.repaint();
        }
        if (historyBackButton != null) {
            restyleFlatToolbarButton(historyBackButton);
            restyleFlatToolbarButton(historyBackDropButton);
            restyleFlatToolbarButton(historyForwardButton);
            restyleFlatToolbarButton(historyForwardDropButton);
        }
        if (historyBackSplitButton != null) {
            historyBackSplitButton.repaint();
        }
        if (historyForwardSplitButton != null) {
            historyForwardSplitButton.repaint();
        }
    }
}
