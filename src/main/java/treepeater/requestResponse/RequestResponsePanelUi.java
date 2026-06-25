package treepeater.requestResponse;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.FlatClientProperties;

import treepeater.Treepeater;
import treepeater.Utilities;
import treepeater.components.CustomButton;
import treepeater.components.SplitButtonPanel;
import treepeater.icons.GearIcon;

/**
 * Static Swing helpers for {@link RequestResponsePanel}: themed top bar, flat toolbar buttons,
 * and labeled editor columns.
 */
public final class RequestResponsePanelUi {

    private static final int OPTIONS_BUTTON_ICON_SIZE = 22;

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

    public static void styleAsFlatButton(JButton button) {
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        button.setBackground(Utilities.flatPanelBackground());
    }



    public static void installHoverBackground(JButton button) {
        button.getModel().addChangeListener(e -> applyFlatButtonHoverVisual(button));
    }

    public static void applyFlatButtonHoverVisual(JButton button) {
        Color normalBg = Utilities.flatPanelBackground();
        Color hoverBg = Utilities.uiHoverColor();
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

    public static void restyleFlatToolbarButton(JButton button) {
        styleAsFlatButton(button);
        applyFlatButtonHoverVisual(button);
    }

    public static void styleOptionsButton(JButton button) {
        styleAsFlatButton(button);
        installHoverBackground(button);
        button.setMargin(new Insets(4,4,4,4));
        applyOptionsButtonIcon(button);
    }

    static void applyOptionsButtonIcon(JButton button) {
        if (button == null) {
            return;
        }
        button.setIcon(
                new GearIcon()
                        .withSize(OPTIONS_BUTTON_ICON_SIZE, OPTIONS_BUTTON_ICON_SIZE)
                        .withColor(UIManager.getColor("Label.foreground")));
    }

    /**
     * Modal OK/Cancel dialog positioned with its top-left just below {@code anchor}'s bottom-left.
     *
     * @return {@link JOptionPane#OK_OPTION}, {@link JOptionPane#CANCEL_OPTION}, or {@link JOptionPane#CLOSED_OPTION}
     */
    static int showConfirmDialogBelowButton(Component parent, JButton anchor, JComponent content, String title) {
        JOptionPane optionPane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(parent, title);
        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(dialog);
        }
        dialog.pack();
        Point location = new Point(0, anchor.getHeight() + 2);
        SwingUtilities.convertPointToScreen(location, anchor);
        dialog.setLocation(location);
        dialog.setVisible(true);
        Object value = optionPane.getValue();
        if (value instanceof Integer selected) {
            return selected;
        }
        return JOptionPane.CLOSED_OPTION;
    }

    /** Slightly tighter tab strip padding than FlatLaf defaults ({@code 4,12,4,12}). */
    public static void applyCompactTabPane(JTabbedPane tabbedPane) {
        tabbedPane.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_INSETS, new Insets(3, 10, 3, 10));
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
            JButton optionsButton,
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
            topBarWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Utilities.uiBorderColor()));
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
        if (optionsButton != null) {
            restyleFlatToolbarButton(optionsButton);
            applyOptionsButtonIcon(optionsButton);
            if (Treepeater.api != null) {
                Treepeater.api.userInterface().applyThemeToComponent(optionsButton);
            }
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
