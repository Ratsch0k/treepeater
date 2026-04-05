package treepeater.settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import treepeater.components.CustomButton;
import treepeater.components.RoundedPanel;



/**
 * Modal dialog that captures a single key combination and returns it in Burp-style form
 * (e.g. {@code Ctrl+Alt+Shift+T}).
 */
public final class HotkeyCaptureDialog {

    private HotkeyCaptureDialog() {
    }

    /**
     * Shows a modal dialog; returns a hotkey string or {@code null} if cancelled.
     */
    public static String showDialog(Component parent) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Record shortcut", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JLabel hintLabel = new JLabel("Press the key combination you want to use.");
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel hint = new JPanel();
        hint.setLayout(new BorderLayout());
        hint.add(hintLabel, BorderLayout.WEST);
        hint.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 32));

        JLabel status = new JLabel(" ", JLabel.CENTER);
        status.setFont(status.getFont().deriveFont(Font.BOLD));

        RoundedPanel statusPanel = new RoundedPanel();
        statusPanel.setOpaque(true);
        statusPanel.setBackground(UIManager.getColor("Button.default.background"));
        statusPanel.setBorderColor(UIManager.getColor("Button.default.borderColor"));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        statusPanel.setLayout(new BorderLayout());
        statusPanel.add(status, BorderLayout.CENTER);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(statusPanel, BorderLayout.CENTER);
        statusRow.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelButton = new JButton("Cancel");
        CustomButton okButton = new CustomButton("OK");
        okButton.setBackground(UIManager.getColor("Button.primary.background"));
        okButton.setForeground(UIManager.getColor("Button.primary.foreground"));
        okButton.setHoverBackground(UIManager.getColor("Button.primary.hoverBackground"));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        JPanel content = new JPanel();
        BoxLayout layout = new BoxLayout(content, BoxLayout.PAGE_AXIS);
        content.setLayout(layout);
        content.add(hint);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(statusRow);
        content.add(Box.createRigidArea(new Dimension(0, 16)));
        content.add(buttonPanel);
        dialog.setContentPane(content);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        cancelButton.addActionListener(e -> {
            status.setText(null);
            dialog.dispose();
        });
        okButton.addActionListener(e -> dialog.dispose());

        KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (!dialog.isShowing()) {
                    return false;
                }
                if (e.getID() != KeyEvent.KEY_PRESSED) {
                    return false;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    status.setText(null);
                    SwingUtilities.invokeLater(dialog::dispose);
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_TAB || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    SwingUtilities.invokeLater(dialog::dispose);
                    return false;
                }
                String hotkey = formatBurpHotkey(e);
                if (hotkey == null) {
                    return false;
                }
                int mod = e.getModifiersEx();
                boolean hasCtrl = (mod & KeyEvent.CTRL_DOWN_MASK) != 0;
                boolean hasMeta = (mod & KeyEvent.META_DOWN_MASK) != 0;
                if (!hasCtrl && !hasMeta) {
                    SwingUtilities.invokeLater(() -> status.setText("Burp requires Ctrl or Cmd (⌘) in the shortcut."));
                    return true;
                }
                status.setText(hotkey);
                return true;
            }
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
            }
        });

        dialog.setVisible(true);
        return status.getText();
    }

    /**
     * @return Burp-style shortcut, or {@code null} if this event is not a usable key (e.g. modifier only).
     */
    static String formatBurpHotkey(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return null;
        }
        if (keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL
                || keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_META) {
            return null;
        }

        int mod = e.getModifiersEx();
        StringBuilder sb = new StringBuilder();
        if ((mod & KeyEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((mod & KeyEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        if ((mod & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        if ((mod & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("Cmd+");
        }

        String keyPart = KeyEvent.getKeyText(keyCode).replace(" ", "");
        sb.append(keyPart);
        return sb.toString();
    }
}
