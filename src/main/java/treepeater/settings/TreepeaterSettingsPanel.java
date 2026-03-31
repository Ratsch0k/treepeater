package treepeater.settings;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Set;


import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import burp.api.montoya.ui.settings.SettingsPanelWithData;

/**
 * Burp settings UI for Treepeater: wraps the Montoya-built settings row and adds shortcut capture.
 */
public final class TreepeaterSettingsPanel implements SettingsPanelWithData {
    private final TreepeaterSettings settings;
    private final JPanel root;

    public TreepeaterSettingsPanel() {
        this.settings = TreepeaterSettings.getInstance();
        this.root = new JPanel();
        BoxLayout layout = new BoxLayout(this.root, BoxLayout.Y_AXIS);
        this.root.setLayout(layout);
        
        JLabel hotkeyLabel = new JLabel("Send to Treepeater hotkey:");
        JButton hotkeyButton = new JButton(this.settings.getSendHotkey());

        hotkeyButton.addActionListener(e -> {
            String hotkey = HotkeyCaptureDialog.showDialog(this.root);
            if (hotkey != null) {
                this.settings.setSendHotkey(hotkey);
                hotkeyButton.setText(hotkey);
            }
        });

        JPanel hotkeySetting = this.buildSettingsInput(hotkeyLabel, hotkeyButton);

        this.root.add(hotkeySetting);
    }

    private JPanel buildSettingsInput(JComponent description, JComponent input) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 4, 0, 4);
        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(description, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(input, gbc);
        return panel;
    }

    @Override
    public JComponent uiComponent() {
        return root;
    }

    @Override
    public Set<String> keywords() {
        return Set.of("Treepeater", "hotkey", "shortcut", "keyboard", "repeater");
    }

    @Override
    public String getString(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getString'");
    }

    @Override
    public boolean getBoolean(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBoolean'");
    }

    @Override
    public int getInteger(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getInteger'");
    }
}
