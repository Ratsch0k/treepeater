package treepeater.settings;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import burp.api.montoya.ui.settings.SettingsPanelWithData;

/**
 * Burp settings UI for Treepeater: wraps the Montoya-built settings row and adds shortcut capture.
 */
public final class TreepeaterSettingsPanel implements SettingsPanelWithData {
    private static final int ROW_GAP = 10;

    private final TreepeaterSettings settings;
    private final JPanel root;

    public TreepeaterSettingsPanel() {
        this.settings = TreepeaterSettings.getInstance();
        this.root = new JPanel(new GridBagLayout());

        int row = 0;
        row = this.addHotkeySetting(row, "Send to Treepeater hotkey:", this.settings::getSendHotkey, this.settings::setSendHotkey);
        row = this.addHotkeySetting(row, "Send request hotkey:", this.settings::getSendRequestHotkey, this.settings::setSendRequestHotkey);
        row = this.addHotkeySetting(row, "History back hotkey:", this.settings::getHistoryBackHotkey, this.settings::setHistoryBackHotkey);
        row = this.addHotkeySetting(row, "History forward hotkey:", this.settings::getHistoryForwardHotkey, this.settings::setHistoryForwardHotkey);
        row = this.addHotkeySetting(
                row,
                "Copy same-parent request hotkey:",
                this.settings::getCopySameParentRequestHotkey,
                this.settings::setCopySameParentRequestHotkey);
        row = this.addHotkeySetting(row, "Rename hotkey:", this.settings::getRenameHotkey, this.settings::setRenameHotkey);
        row = this.addHotkeySetting(row, "Change status hotkey:", this.settings::getChangeStatusHotkey, this.settings::setChangeStatusHotkey);
        row = this.addHotkeySetting(row, "Edit target hotkey:", this.settings::getEditTargetHotkey, this.settings::setEditTargetHotkey);
        row = this.addHotkeySetting(row, "Previous request tab hotkey:", this.settings::getTabPreviousHotkey, this.settings::setTabPreviousHotkey);
        this.addHotkeySetting(row, "Next request tab hotkey:", this.settings::getTabNextHotkey, this.settings::setTabNextHotkey);
    }

    private int addHotkeySetting(int row, String labelText, Supplier<String> getter, Consumer<String> setter) {
        JLabel hotkeyLabel = new JLabel(labelText);
        JButton hotkeyButton = new JButton(getter.get());
        hotkeyButton.addActionListener(e -> {
            String hotkey = HotkeyCaptureDialog.showDialog(this.root);
            if (hotkey != null) {
                setter.accept(hotkey);
                hotkeyButton.setText(hotkey);
            }
        });

        Insets labelInsets = new Insets(row > 0 ? ROW_GAP : 0, 4, 0, 8);
        Insets buttonInsets = new Insets(row > 0 ? ROW_GAP : 0, 0, 0, 4);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.fill = GridBagConstraints.NONE;
        labelGbc.weightx = 0;
        labelGbc.insets = labelInsets;

        GridBagConstraints buttonGbc = new GridBagConstraints();
        buttonGbc.gridx = 1;
        buttonGbc.gridy = row;
        buttonGbc.anchor = GridBagConstraints.WEST;
        buttonGbc.fill = GridBagConstraints.NONE;
        buttonGbc.weightx = 1;
        buttonGbc.insets = buttonInsets;

        this.root.add(hotkeyLabel, labelGbc);
        this.root.add(hotkeyButton, buttonGbc);
        return row + 1;
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
